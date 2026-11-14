package com.pizzadue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pizzadue.audio.Clip
import com.pizzadue.audio.SoundPlayer
import com.pizzadue.audio.Speaker
import com.pizzadue.dough.DoughCalc
import com.pizzadue.settings.Settings
import com.pizzadue.settings.SettingsRepository
import com.pizzadue.timer.Alert
import com.pizzadue.timer.Mode
import com.pizzadue.timer.Phase
import com.pizzadue.timer.TimerRepository
import com.pizzadue.timer.TimerService
import com.pizzadue.timer.TimerState
import com.pizzadue.voice.AppTab
import com.pizzadue.voice.Command
import com.pizzadue.voice.Lang
import com.pizzadue.voice.Replies
import com.pizzadue.voice.VoiceController
import com.pizzadue.voice.VoiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What was heard and what the app answered, shown on the Timer tab. */
data class Heard(val heard: String?, val reply: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)
    private val speaker = Speaker(app)
    private val player = SoundPlayer.get(app)

    val settings: StateFlow<Settings> = repo.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val timer: StateFlow<TimerState> = TimerRepository.state

    private val _tab = MutableStateFlow(AppTab.TIMER)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    private val _heard = MutableStateFlow<Heard?>(null)
    val heard: StateFlow<Heard?> = _heard.asStateFlow()

    val voice = VoiceController(app, viewModelScope) { cmd, lang, text -> onCommand(cmd, lang, text) }
    val voiceStatus: StateFlow<VoiceStatus> = voice.status

    private var foreground = false

    init {
        // the idle timer always shows the current bake / preheat time from Settings
        viewModelScope.launch {
            settings.collect { s ->
                val st = TimerRepository.state.value
                TimerRepository.setIdle(st.mode, durationFor(st.mode, s))
            }
        }
        // restart listening when the language switches change
        viewModelScope.launch {
            settings.map { langs(it) }.distinctUntilChanged().collect { if (foreground) voice.start(it) }
        }
    }

    private fun langs(s: Settings) = buildSet {
        if (s.voiceEn) add(Lang.EN)
        if (s.voicePl) add(Lang.PL)
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

    override fun onCleared() {
        voice.stop()
        speaker.shutdown()
    }

    // ---- timer actions ----
    fun selectTab(t: AppTab) { _tab.value = t }

    fun selectMode(mode: Mode) = TimerRepository.setIdle(mode, durationFor(mode))

    fun startCurrent() {
        val st = timer.value
        start(st.mode)
    }

    private fun start(mode: Mode) {
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

    fun preview(clip: Clip) = player.voice(clip)

    // ---- voice ----
    private fun onCommand(cmd: Command, lang: Lang, text: String) {
        val st = timer.value
        val s = settings.value
        val reply: String? = when (cmd) {
            Command.StartBake -> {
                if (st.mode == Mode.BAKE && st.isActive) null // already baking: ignore, never restart by accident
                else { _tab.value = AppTab.TIMER; start(Mode.BAKE); Replies.bakeStarted(s.bakeSec.toLong(), lang) }
            }
            Command.StartPreheat -> {
                _tab.value = AppTab.TIMER
                if (st.mode == Mode.PREHEAT && st.isActive) null
                else { start(Mode.PREHEAT); Replies.preheatStarted(s.preheatSec.toLong(), lang) }
            }
            Command.Stop -> when {
                st.isActive || st.phase == Phase.DONE -> { stop(); Replies.stopped(st.elapsedMs / 1000, lang) }
                st.alert != Alert.NONE -> { stop(); Replies.silenced(lang) }
                else -> Replies.nothingRunning(lang)
            }
            Command.TimeLeft ->
                if (st.isActive) Replies.left((st.remainingMs + 999) / 1000, lang) else Replies.nothingRunning(lang)
            Command.Pause -> if (st.phase == Phase.RUNNING) { pause(); Replies.paused(lang) } else null
            Command.Resume -> if (st.phase == Phase.PAUSED) { resume(); Replies.resumed(lang) } else null
            is Command.SetBake -> {
                setDuration(Mode.BAKE, cmd.seconds)
                if (!st.isActive) selectMode(Mode.BAKE)
                Replies.bakeSet(cmd.seconds.toLong(), lang)
            }
            is Command.Bright -> { updateSettings { it.copy(bright = cmd.on) }; Replies.bright(cmd.on, lang) }
            is Command.Hydration -> {
                updateSettings { it.copy(hydration = cmd.percent.toFloat()) }
                Replies.hydration(cmd.percent, lang)
            }
            is Command.Dough -> {
                updateSettings { it.copy(balls = cmd.balls) }
                _tab.value = AppTab.DOUGH
                val r = DoughCalc.calc(cmd.balls, s.ballWeight.toDouble(), s.hydration.toDouble(), s.salt.toDouble(), s.yeast.toDouble())
                Replies.dough(r, lang)
            }
            is Command.Tab -> { _tab.value = cmd.tab; null }
            is Command.Ticks -> { updateSettings { it.copy(ticks = cmd.on) }; Replies.ticks(cmd.on, lang) }
        }
        _heard.value = Heard(text, reply ?: "")
        if (reply != null) speaker.speak(reply, lang)
    }
}
