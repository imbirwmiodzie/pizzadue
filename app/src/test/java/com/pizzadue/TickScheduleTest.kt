package com.pizzadue

import com.pizzadue.timer.Cue
import com.pizzadue.timer.TickSchedule
import com.pizzadue.timer.TimedCue
import org.junit.Assert.assertEquals
import org.junit.Test

class TickScheduleTest {
    @Test fun bakeOneThirtyCountsQuartersAndEndsWithPronta() {
        val cues = TickSchedule.bake(90_000, giraAtHalf = false)
        assertEquals(
            listOf(
                TimedCue(15_000, Cue.Ticks(1)),
                TimedCue(30_000, Cue.Ticks(2)),
                TimedCue(45_000, Cue.Ticks(3)),
                TimedCue(60_000, Cue.LongTick),
                TimedCue(75_000, Cue.Ticks(1)),
                TimedCue(90_000, Cue.BakeDone),
            ),
            cues,
        )
    }

    @Test fun bakeTwoMinutesHasLongTickAtOneMinuteOnly() {
        val cues = TickSchedule.bake(120_000, giraAtHalf = false)
        assertEquals(listOf(Cue.LongTick), cues.filter { it.cue == Cue.LongTick }.map { it.cue })
        assertEquals(TimedCue(120_000, Cue.BakeDone), cues.last())
        assertEquals(Cue.Ticks(3), cues.first { it.atMs == 105_000L }.cue)
    }

    @Test fun giraAtHalfPlaysAfterTheTicksAtTheSameMoment() {
        val cues = TickSchedule.bake(90_000, giraAtHalf = true)
        val at45 = cues.filter { it.atMs == 45_000L }.map { it.cue }
        assertEquals(listOf(Cue.Ticks(3), Cue.Gira), at45)
    }

    @Test fun preheatThirtyMinutesTicksAtThirds() {
        assertEquals(
            listOf(
                TimedCue(600_000, Cue.Ticks(1)),
                TimedCue(1_200_000, Cue.Ticks(2)),
                TimedCue(1_800_000, Cue.PreheatDone),
            ),
            TickSchedule.preheat(1_800_000),
        )
    }

    @Test fun nextCue() {
        val cues = TickSchedule.bake(90_000, false)
        assertEquals(TimedCue(30_000, Cue.Ticks(2)), TickSchedule.next(cues, 15_000))
    }
}
