package com.vibepizzaiolo

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.vibepizzaiolo.timer.Alert
import com.vibepizzaiolo.timer.Mode
import com.vibepizzaiolo.timer.Phase
import com.vibepizzaiolo.timer.TimerRepository
import com.vibepizzaiolo.timer.TimerService
import com.vibepizzaiolo.timer.TimerState
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Runs the real timer service with a fake clock: what happens when the bake ends,
 * and whether "silence" and "vai!" still work afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TimerServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private var startId = 0

    @Before fun reset() {
        TimerRepository.update { TimerState() }
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun sentIntent(): Intent = shadowOf(app).nextStartedService
    private fun deliver(c: ServiceController<TimerService>) = c.withIntent(sentIntent()).startCommand(0, ++startId)
    private val state get() = TimerRepository.state.value

    private fun bakeUntilDone(): ServiceController<TimerService> {
        TimerService.start(app, Mode.BAKE, 20_000)
        val c = Robolectric.buildService(TimerService::class.java).create()
        deliver(c)
        assertEquals(Phase.RUNNING, state.phase)
        idle(21_000)
        assertEquals(Phase.DONE, state.phase)
        assertEquals(Alert.PRONTA, state.alert)
        return c
    }

    @Test fun silenceAfterBakeStopsTheEndCall() {
        val c = bakeUntilDone()
        TimerService.stop(app)
        deliver(c)
        idle(100)
        assertEquals(Phase.IDLE, state.phase)
        assertEquals(Alert.NONE, state.alert)
        // and it stays quiet
        idle(60_000)
        assertEquals(Alert.NONE, state.alert)
    }

    @Test fun vaiAfterBakeStartsTheNextOne() {
        val c = bakeUntilDone()
        TimerService.start(app, Mode.BAKE, 20_000)
        deliver(c)
        idle(100)
        assertEquals(Phase.RUNNING, state.phase)
        assertEquals(Alert.NONE, state.alert)
        idle(5_000)
        assertEquals(5_100.0, state.elapsedMs.toDouble(), 300.0)
    }

    @Test fun endCallStopsByItselfAfterThreeTimes() {
        bakeUntilDone()
        idle(TimerService.ALERT_TIMES * 10_000L + 5_000)
        assertEquals(Alert.NONE, state.alert)
        assertEquals(Phase.IDLE, state.phase)
    }

    @Test fun stopThenQuickStartLeavesARunningTimer() {
        val c = bakeUntilDone()
        TimerService.stop(app)
        TimerService.start(app, Mode.BAKE, 20_000)
        deliver(c)
        deliver(c)
        idle(2_000)
        assertEquals(Phase.RUNNING, state.phase)
    }
}
