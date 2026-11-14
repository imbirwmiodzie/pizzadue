package com.vibepizzaiolo.timer

enum class Mode { PREHEAT, BAKE }

/** Something the timer plays at a given moment. */
sealed interface Cue {
    /** [count] short ticks in a row. */
    data class Ticks(val count: Int) : Cue
    /** One long tick, on each full minute of the bake. */
    data object LongTick : Cue
    /** "Gira, gira!" reminder to turn the pizza. */
    data object Gira : Cue
    /** End of preheat: 3 ticks, then "Forno a legna caldo". */
    data object PreheatDone : Cue
    /** End of bake: "La pizza è pronta". */
    data object BakeDone : Cue
}

data class TimedCue(val atMs: Long, val cue: Cue)

object TickSchedule {
    const val QUARTER_MS = 15_000L

    /**
     * Bake: every 15 s, counting within each minute: 1 tick at :15, 2 at :30, 3 at :45,
     * one long tick on the full minute. The end replaces the last tick.
     */
    fun bake(durationMs: Long, giraAtHalf: Boolean): List<TimedCue> {
        require(durationMs > 0)
        val cues = mutableListOf<TimedCue>()
        var k = 1L
        while (k * QUARTER_MS < durationMs) {
            val q = (k % 4).toInt()
            cues += TimedCue(k * QUARTER_MS, if (q == 0) Cue.LongTick else Cue.Ticks(q))
            k++
        }
        if (giraAtHalf && durationMs >= 20_000L) cues += TimedCue(durationMs / 2, Cue.Gira)
        cues += TimedCue(durationMs, Cue.BakeDone)
        // sortedBy is stable: a tick that falls on the same moment as "gira" plays first
        return cues.sortedBy { it.atMs }
    }

    /** Preheat: silent except 1 tick at 1/3, 2 ticks at 2/3, then 3 ticks and the voice at the end. */
    fun preheat(durationMs: Long): List<TimedCue> {
        require(durationMs > 0)
        return listOf(
            TimedCue(durationMs / 3, Cue.Ticks(1)),
            TimedCue(durationMs * 2 / 3, Cue.Ticks(2)),
            TimedCue(durationMs, Cue.PreheatDone),
        )
    }

    fun forMode(mode: Mode, durationMs: Long, giraAtHalf: Boolean) =
        if (mode == Mode.BAKE) bake(durationMs, giraAtHalf) else preheat(durationMs)

    /** The first cue still to come after [elapsedMs]. */
    fun next(cues: List<TimedCue>, elapsedMs: Long): TimedCue? = cues.firstOrNull { it.atMs > elapsedMs }
}
