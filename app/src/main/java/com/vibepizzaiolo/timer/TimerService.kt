package com.vibepizzaiolo.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vibepizzaiolo.MainActivity
import com.vibepizzaiolo.R
import com.vibepizzaiolo.audio.Clip
import com.vibepizzaiolo.audio.SoundPlayer
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the preheat and bake timers as a foreground service, so ticks keep exact time
 * with the screen locked or the app in the background.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settings: StateFlow<Settings>
    private lateinit var player: SoundPlayer
    private var loopJob: Job? = null
    private var alertJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // running timer
    private var cues: List<TimedCue> = emptyList()
    private var cueIndex = 0
    private var startedAt = 0L      // elapsedRealtime when (re)started
    private var elapsedBefore = 0L  // time banked before the last pause

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository.get(this).flow.stateIn(scope, SharingStarted.Eagerly, Settings())
        player = SoundPlayer.get(this)
        scope.launch {
            settings.collect { s -> player.configure(s) }
        }
        createChannel()
    }

    private var lastStartId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        goForeground()
        when (intent?.action) {
            ACTION_START -> {
                val mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.BAKE.name)
                val ms = intent.getLongExtra(EXTRA_DURATION_MS, 90_000L)
                start(mode, ms)
            }
            ACTION_STOP -> stopAll()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            else -> if (!TimerRepository.state.value.isActive) stopAll()
        }
        return START_NOT_STICKY
    }

    private fun start(mode: Mode, durationMs: Long) {
        cancelAlert()
        player.silence()
        loopJob?.cancel()
        val s = settings.value
        cues = TickSchedule.forMode(mode, durationMs, giraAtHalf = s.gira)
        cueIndex = 0
        elapsedBefore = 0L
        startedAt = SystemClock.elapsedRealtime()
        TimerRepository.update {
            TimerState(mode = mode, phase = Phase.RUNNING, durationMs = durationMs, next = cues.firstOrNull())
        }
        acquireWake()
        player.timerStarted()
        player.startBeep()
        loopJob = scope.launch { runLoop() }
        notifyNow()
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val st = TimerRepository.state.value
            if (st.phase != Phase.RUNNING) return
            val elapsed = elapsedBefore + (SystemClock.elapsedRealtime() - startedAt)
            while (cueIndex < cues.size && cues[cueIndex].atMs <= elapsed) {
                val cue = cues[cueIndex++]
                if (fire(cue.cue)) return
            }
            TimerRepository.update { it.copy(elapsedMs = elapsed.coerceAtMost(it.durationMs), next = cues.getOrNull(cueIndex)) }
            val toNext = (cues.getOrNull(cueIndex)?.atMs ?: Long.MAX_VALUE) - elapsed
            delay(toNext.coerceIn(5L, 100L))
        }
    }

    /** Plays a cue. Returns true when the timer has finished. */
    private fun fire(cue: Cue): Boolean {
        val s = settings.value
        when (cue) {
            is Cue.Ticks -> if (s.ticks) player.ticks(cue.count)
            Cue.LongTick -> if (s.ticks) player.longTick()
            Cue.Gira -> if (s.gira) player.voice(Clip.GIRA)
            Cue.BakeDone -> {
                recordBake(TimerRepository.state.value.durationMs)
                TimerRepository.update { it.copy(phase = Phase.DONE, elapsedMs = it.durationMs, alert = Alert.PRONTA, next = null) }
                startAlert(Clip.PRONTA, enabled = s.pronta, everyMs = 10_000L, times = ALERT_TIMES)
                return true
            }
            Cue.PreheatDone -> {
                if (s.ticks) player.ticks(3)
                // oven is hot: switch to bake, ready for "ciao pizza start"
                TimerRepository.update {
                    TimerState(mode = Mode.BAKE, phase = Phase.IDLE, durationMs = s.bakeSec * 1000L, alert = Alert.FORNO)
                }
                startAlert(Clip.FORNO, enabled = s.forno, everyMs = 30_000L, times = ALERT_TIMES)
                return true
            }
        }
        return false
    }

    /**
     * Plays a voice clip [times] times, [everyMs] apart, unless stopped or "ciao pizza start" comes first.
     * The caller has already set the alert in the state; only an older alert's job is cancelled
     * here, never the state (clearing it hid the "silence" control while the call kept playing).
     */
    private fun startAlert(clip: Clip, enabled: Boolean, everyMs: Long, times: Int) {
        alertJob?.cancel()
        notifyNow()
        alertJob = scope.launch {
            repeat(times) { i ->
                if (enabled) player.voice(clip) else player.longTick()
                if (i < times - 1) delay(everyMs)
            }
            delay(3_000L) // let the last call finish
            TimerRepository.update { if (it.phase == Phase.DONE) TimerState(durationMs = settings.value.bakeSec * 1000L) else it.copy(alert = Alert.NONE) }
            finishService()
        }
    }

    private fun recordBake(ms: Long) {
        val repo = SettingsRepository.get(this)
        // its own scope: the service may stop right after, the write must still finish
        CoroutineScope(Dispatchers.IO).launch { repo.recordBake(ms) }
    }

    private fun cancelAlert() {
        alertJob?.cancel()
        alertJob = null
        TimerRepository.update { it.copy(alert = Alert.NONE) }
    }

    private fun pause() {
        val st = TimerRepository.state.value
        if (st.phase != Phase.RUNNING) return
        elapsedBefore += SystemClock.elapsedRealtime() - startedAt
        loopJob?.cancel()
        TimerRepository.update { it.copy(phase = Phase.PAUSED, elapsedMs = elapsedBefore) }
        notifyNow()
    }

    private fun resume() {
        if (TimerRepository.state.value.phase != Phase.PAUSED) return
        startedAt = SystemClock.elapsedRealtime()
        TimerRepository.update { it.copy(phase = Phase.RUNNING) }
        loopJob = scope.launch { runLoop() }
        notifyNow()
    }

    private fun stopAll() {
        loopJob?.cancel()
        cancelAlert()
        player.silence()
        val st = TimerRepository.state.value
        // "ciao pizza stop" before the time is up still counts as a pizza, if it was really in the oven
        if (st.mode == Mode.BAKE && st.isActive) {
            val baked = if (st.phase == Phase.RUNNING) elapsedBefore + (SystemClock.elapsedRealtime() - startedAt) else elapsedBefore
            if (baked >= 20_000L) recordBake(baked)
        }
        val s = settings.value
        val mode = if (st.phase == Phase.DONE) Mode.BAKE else st.mode
        val ms = if (mode == Mode.BAKE) s.bakeSec * 1000L else s.preheatSec * 1000L
        TimerRepository.update { TimerState(mode = mode, phase = Phase.IDLE, durationMs = ms) }
        finishService()
    }

    private fun finishService() {
        player.timerStopped()
        releaseWake()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // only stop if no newer start arrived meanwhile (a quick stop then start must not kill the new timer)
        stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        if (!TimerRepository.state.value.isActive) player.timerStopped()
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    // ---- wake lock: keeps the CPU awake so ticks stay on time with the screen off ----
    private fun acquireWake() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibePizzaiolo:timer")
            .apply { acquire(2 * 60 * 60_000L) }
    }

    private fun releaseWake() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ---- notification ----
    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun notifyNow() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val st = TimerRepository.state.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TimerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when {
            st.alert == Alert.PRONTA -> getString(R.string.notif_pronta)
            st.alert == Alert.FORNO -> getString(R.string.notif_forno)
            st.mode == Mode.PREHEAT -> getString(R.string.notif_preheat)
            else -> getString(R.string.notif_bake)
        }
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pizza)
            .setContentTitle(title)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(0, getString(R.string.action_stop), stop)
        if (st.phase == Phase.RUNNING) {
            b.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + st.remainingMs)
        } else if (st.phase == Phase.PAUSED) {
            b.setContentText(getString(R.string.notif_paused))
        }
        return b.build()
    }

    companion object {
        /** How many times the end call ("La pizza è pronta!", "Forno a legna caldo") is played. */
        const val ALERT_TIMES = 3
        private const val CHANNEL = "timer"
        private const val NOTIFICATION_ID = 15
        private const val ACTION_START = "com.vibepizzaiolo.START"
        private const val ACTION_STOP = "com.vibepizzaiolo.STOP"
        private const val ACTION_PAUSE = "com.vibepizzaiolo.PAUSE"
        private const val ACTION_RESUME = "com.vibepizzaiolo.RESUME"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_DURATION_MS = "duration_ms"

        fun start(context: Context, mode: Mode, durationMs: Long) = send(
            context, Intent(context, TimerService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_MODE, mode.name).putExtra(EXTRA_DURATION_MS, durationMs)
        )

        fun stop(context: Context) {
            val st = TimerRepository.state.value
            if (st.phase == Phase.IDLE && st.alert == Alert.NONE) return
            send(context, Intent(context, TimerService::class.java).setAction(ACTION_STOP))
        }

        fun pause(context: Context) = send(context, Intent(context, TimerService::class.java).setAction(ACTION_PAUSE))
        fun resume(context: Context) = send(context, Intent(context, TimerService::class.java).setAction(ACTION_RESUME))

        private fun send(context: Context, intent: Intent) = ContextCompat.startForegroundService(context, intent)
    }
}
