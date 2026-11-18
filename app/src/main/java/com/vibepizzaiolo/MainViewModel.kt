package com.vibepizzaiolo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibepizzaiolo.audio.Clip
import com.vibepizzaiolo.audio.SoundPlayer
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.settings.SettingsRepository
import com.vibepizzaiolo.timer.Alert
import com.vibepizzaiolo.timer.Mode
import com.vibepizzaiolo.timer.Phase
import com.vibepizzaiolo.timer.TimerRepository
import com.vibepizzaiolo.timer.TimerService
import com.vibepizzaiolo.timer.TimerState
import com.vibepizzaiolo.voice.AppTab
import com.vibepizzaiolo.voice.Command
import com.vibepizzaiolo.voice.Lang
import com.vibepizzaiolo.voice.VoiceController
import com.vibepizzaiolo.voice.VoiceStatus
import android.graphics.Bitmap
import android.net.Uri
import com.vibepizzaiolo.settings.BakeLog
import com.vibepizzaiolo.share.CardInfo
import com.vibepizzaiolo.share.CardRenderer
import com.vibepizzaiolo.share.Design
import com.vibepizzaiolo.share.Format
import com.vibepizzaiolo.share.ShareUtils
import com.vibepizzaiolo.avatar.AvatarStore
import com.vibepizzaiolo.avatar.Cartoonizer
import com.vibepizzaiolo.video.VideoMaker
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the share screen shows: the photo, the chosen design and format, and the finished card. */
data class ShareUi(
    val photo: Bitmap? = null,
    val design: Design = Design.SHEET,
    val format: Format = Format.FEED,
    val card: Bitmap? = null,
    val working: Boolean = true,
    val error: String? = null,
)

/** The pizza reel: the recording guide, making it, then the finished file. */
sealed interface VideoUi {
    data object Guide : VideoUi
    data class Working(val progress: Int) : VideoUi
    data class Ready(val file: File) : VideoUi
    data class Failed(val message: String) : VideoUi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)
    private val player = SoundPlayer.get(app)

    val settings: StateFlow<Settings> = repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val timer: StateFlow<TimerState> = TimerRepository.state

    private val _tab = MutableStateFlow(AppTab.TIMER)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    val voice = VoiceController(app, viewModelScope) { cmd, _, _ -> onCommand(cmd) }
    val voiceStatus: StateFlow<VoiceStatus> = voice.status

    private var foreground = false

    val bakeLog: StateFlow<BakeLog> = repo.bakeLog.stateIn(viewModelScope, SharingStarted.Eagerly, BakeLog())

    private val _share = MutableStateFlow<ShareUi?>(null)
    val share: StateFlow<ShareUi?> = _share.asStateFlow()

    private val _video = MutableStateFlow<VideoUi?>(null)
    val video: StateFlow<VideoUi?> = _video.asStateFlow()

    /** Your cartoon portrait: your icon, and your face on the pizzaiolo at the end of the reel. */
    private val _avatar = MutableStateFlow<Bitmap?>(null)
    val avatar: StateFlow<Bitmap?> = _avatar.asStateFlow()
    private val _avatarBusy = MutableStateFlow(false)
    val avatarBusy: StateFlow<Boolean> = _avatarBusy.asStateFlow()

    init {
        // the idle timer always shows the current bake / preheat time from Settings
        viewModelScope.launch {
            settings.collect { s ->
                val st = TimerRepository.state.value
                TimerRepository.setIdle(st.mode, durationFor(st.mode, s))
            }
        }
        viewModelScope.launch { _avatar.value = withContext(Dispatchers.IO) { AvatarStore.load(getApplication()) } }
        // the sound player follows Settings even when no timer runs (previews in Settings)
        viewModelScope.launch { settings.collect { player.configure(it) } }
        // restart listening when the voice switch changes
        viewModelScope.launch {
            settings.map { langs(it) }.distinctUntilChanged().collect { if (foreground) voice.start(it) }
        }
    }

    private fun langs(s: Settings) = buildSet {
        if (s.voice) add(Lang.IT)
    }

    private fun durationFor(mode: Mode, s: Settings = settings.value) =
        (if (mode == Mode.BAKE) s.bakeSec else s.preheatSec) * 1000L

    // ---- lifecycle ----
    fun onForeground() {
        foreground = true
        voice.start(langs(settings.value))
    }

    fun onBackground() {
        foreground = false
        voice.stop()
    }

    override fun onCleared() = voice.stop()

    // ---- actions ----
    fun selectTab(t: AppTab) { _tab.value = t }

    fun selectMode(mode: Mode) = TimerRepository.setIdle(mode, durationFor(mode))

    fun startCurrent() {
        val mode = timer.value.mode
        TimerService.start(getApplication(), mode, durationFor(mode))
    }

    fun stop() = TimerService.stop(getApplication())
    fun pause() = TimerService.pause(getApplication())
    fun resume() = TimerService.resume(getApplication())

    fun setDuration(mode: Mode, sec: Int) = updateSettings {
        if (mode == Mode.BAKE) it.copy(bakeSec = sec) else it.copy(preheatSec = sec)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun preview(clip: Clip) = player.previewClip(clip)
    fun previewVolume() = player.previewVolume()

    // ---- share your pizza ----
    fun openShare(uri: Uri) {
        _share.value = ShareUi()
        viewModelScope.launch {
            val photo = withContext(Dispatchers.IO) { ShareUtils.loadPhoto(getApplication(), uri) }
            if (photo == null) {
                _share.value = ShareUi(working = false, error = "Could not open that photo. Try another one.")
                return@launch
            }
            _share.update { it?.copy(photo = photo) }
            renderCard()
        }
    }

    fun setDesign(d: Design) { _share.update { it?.copy(design = d) }; renderCard() }
    fun setFormat(f: Format) { _share.update { it?.copy(format = f) }; renderCard() }
    fun closeShare() { _share.value = null }

    fun cardInfo(): CardInfo {
        val s = settings.value
        val log = bakeLog.value
        return CardInfo(
            pizzaNo = log.count.coerceAtLeast(1),
            bakeMs = if (log.lastBakeMs > 0) log.lastBakeMs else s.bakeSec * 1000L,
            whenMs = if (log.lastAt > 0) log.lastAt else System.currentTimeMillis(),
            hydration = s.hydration, salt = s.salt, yeast = s.yeast,
            preheatMin = s.preheatSec / 60, balls = s.balls, ballWeight = s.ballWeight,
        )
    }

    fun caption(): String = ShareUtils.caption(cardInfo())

    private var renderJob: Job? = null
    private fun renderCard() {
        val ui = _share.value ?: return
        val photo = ui.photo ?: return
        _share.update { it?.copy(working = true) }
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val card = withContext(Dispatchers.Default) { CardRenderer.render(photo, ui.design, ui.format, cardInfo()) }
            _share.update { it?.copy(card = card, working = false) }
        }
    }

    // ---- the pizza reel ----
    private var videoJob: Job? = null

    fun openVideoGuide() { _video.value = VideoUi.Guide }

    fun closeVideo() {
        videoJob?.cancel()
        _video.value = null
    }

    fun makeVideo(recording: Uri) {
        videoJob?.cancel()
        _video.value = VideoUi.Working(0)
        videoJob = viewModelScope.launch {
            try {
                val file = VideoMaker(getApplication()).make(recording, cardInfo(), _avatar.value) { p ->
                    if (_video.value is VideoUi.Working) _video.value = VideoUi.Working(p)
                }
                _video.value = VideoUi.Ready(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _video.value = VideoUi.Failed(VideoMaker.explain(e))
            }
        }
    }

    // ---- your cartoon portrait ----
    fun makeAvatar(photo: Uri) {
        _avatarBusy.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            val cartoon = withContext(Dispatchers.Default) {
                ShareUtils.loadPhoto(app, photo, maxSide = 1400)?.let { Cartoonizer.cartoon(it) }
            }
            if (cartoon != null) {
                withContext(Dispatchers.IO) { AvatarStore.save(app, cartoon) }
                _avatar.value = cartoon
            }
            _avatarBusy.value = false
        }
    }

    fun removeAvatar() {
        AvatarStore.delete(getApplication())
        _avatar.value = null
    }

    // ---- voice: start and stop only ----
    private fun onCommand(cmd: Command) {
        val st = timer.value
        when (cmd) {
            // already running: ignore, so a second "pizza napoli presto" never restarts the bake by accident
            // finished ("pronta!") counts as not running: the start phrase starts the next pizza
            Command.START -> if (!st.isActive) {
                _tab.value = AppTab.TIMER
                startCurrent()
            }
            Command.STOP -> if (st.isActive || st.phase == Phase.DONE || st.alert != Alert.NONE) stop()
        }
    }
}
