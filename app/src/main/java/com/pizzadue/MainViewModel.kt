package com.pizzadue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pizzadue.audio.Clip
import com.pizzadue.audio.SoundPlayer
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

    fun preview(clip: Clip) = player.voice(clip)
    fun previewVolume() = player.previewVolume()

    // ---- voice: start and stop only ----
    private fun onCommand(cmd: Command) {
        val st = timer.value
        when (cmd) {
            // already running: ignore, so a second "vai!" never restarts the bake by accident
            // finished ("pronta!") counts as not running: "vai!" starts the next pizza
            Command.START -> if (!st.isActive) {
                _tab.value = AppTab.TIMER
                startCurrent()
            }
            Command.STOP -> if (st.isActive || st.phase == Phase.DONE || st.alert != Alert.NONE) stop()
        }
    }
}
