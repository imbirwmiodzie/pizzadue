package com.vibepizzaiolo.timer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Phase { IDLE, RUNNING, PAUSED, DONE }

/** A voice call that repeats until someone stops it. */
enum class Alert { NONE, PRONTA, FORNO }

data class TimerState(
    val mode: Mode = Mode.BAKE,
    val phase: Phase = Phase.IDLE,
    val durationMs: Long = 90_000L,
    val elapsedMs: Long = 0L,
    val alert: Alert = Alert.NONE,
    val next: TimedCue? = null,
) {
    val remainingMs: Long get() = (durationMs - elapsedMs).coerceAtLeast(0L)
    val isActive: Boolean get() = phase == Phase.RUNNING || phase == Phase.PAUSED
}

/** Single source of truth for the timer, written by [TimerService] and read by the UI. */
object TimerRepository {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    fun update(transform: (TimerState) -> TimerState) = _state.update(transform)

    /** Changes the idle display (mode or duration) when nothing is running. */
    fun setIdle(mode: Mode, durationMs: Long) = _state.update {
        if (it.isActive || it.phase == Phase.DONE) it
        else it.copy(mode = mode, phase = Phase.IDLE, durationMs = durationMs, elapsedMs = 0L, next = null)
    }
}
