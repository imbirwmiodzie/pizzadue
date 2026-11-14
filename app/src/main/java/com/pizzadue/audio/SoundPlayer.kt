package com.pizzadue.audio

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pizzadue.R
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

enum class Clip(val res: Int) {
    PRONTA(R.raw.voice_pronta),
    FORNO(R.raw.voice_forno),
    GIRA(R.raw.voice_gira),
}

/**
 * Plays ticks and voice clips one after another on the alarm stream (heard even when the
 * phone is on silent), at the volume chosen in Settings, with night mode, optional vibration
 * and flashlight blink on each tick.
 */
class SoundPlayer private constructor(private val context: Context) {
    /** Options taken from Settings. */
    @Volatile var vibrate = false
    @Volatile var flash = false
    /** Volume while a timer runs, % of the phone's alarm maximum (5-100). */
    @Volatile var volumePct = 40
        set(v) { field = v; if (timerHold) applyVolume() }
    /** Night mode: never louder than [NIGHT_PCT], and always vibrate. */
    @Volatile var night = false
        set(v) { field = v; if (timerHold) applyVolume() }

    private val effectivePct get() = if (night) minOf(volumePct, NIGHT_PCT) else volumePct
    private val shouldVibrate get() = vibrate || night

    /** Software gain below the alarm stream's lowest step, so 5% is really quiet. */
    @Volatile private var gain = 1f
    /** Loudness-enhancer gain in millibels; only used near the top of the slider. */
    @Volatile private var boostMb = 0

    private sealed interface Item {
        data class Ticks(val count: Int) : Item
        data object Long : Item
        data object Start : Item
        data class Voice(val clip: Clip) : Item
    }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val shortTrack = staticTrack(ToneSynth.shortTick())
    private val longTrack = staticTrack(ToneSynth.longTick())
    private val startTrack = staticTrack(ToneSynth.startBeep())
    private val tracks = listOf(shortTrack, longTrack, startTrack)
    private val enhancers = tracks.mapNotNull { enhance(it.audioSessionId, gainMb = 0) }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
    private val camera = context.getSystemService(CameraManager::class.java)
    private val torchId: String? = runCatching {
        camera?.cameraIdList?.firstOrNull { id ->
            camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private var savedAlarmVolume: Int? = null

    private val queue = Channel<Item>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            for (item in queue) {
                when (item) {
                    is Item.Ticks -> repeat(item.count) {
                        hit(shortTrack, vibrateMs = 70, flashMs = 70)
                        delay(TICK_GAP_MS)
                    }
                    Item.Long -> {
                        hit(longTrack, vibrateMs = 450, flashMs = 400)
                        delay(550)
                    }
                    Item.Start -> {
                        hit(startTrack, vibrateMs = 700, flashMs = 700)
                        delay(800)
                    }
                    is Item.Voice -> playVoice(item.clip)
                }
            }
        }
    }

    fun ticks(count: Int) { queue.trySend(Item.Ticks(count)) }
    fun longTick() { queue.trySend(Item.Long) }
    fun startBeep() { queue.trySend(Item.Start) }
    fun voice(clip: Clip) { queue.trySend(Item.Voice(clip)) }

    /** Drops everything queued and cuts off a voice clip that is playing. Call on the main thread. */
    fun silence() {
        while (queue.tryReceive().isSuccess) Unit
        currentVoice?.let { if (it.isActive) it.resume(Unit) }
    }

    private var currentVoice: CancellableContinuation<Unit>? = null

    private var timerHold = false

    /** A timer started: set the alarm stream to the chosen level (the user's own level is restored later). */
    fun timerStarted() {
        timerHold = true
        applyVolume()
    }

    /** No timer any more: put the alarm volume back to what it was. */
    fun timerStopped() {
        timerHold = false
        restoreVolume()
    }

    /** Plays one long tick at the chosen volume, for the volume slider. */
    fun previewVolume() = withTemporaryVolume(1500) { longTick() }

    /** Plays a voice clip at the chosen volume, for the play buttons in Settings. */
    fun previewClip(clip: Clip) = withTemporaryVolume(4000) { voice(clip) }

    private fun withTemporaryVolume(ms: Long, play: () -> Unit) {
        applyVolume()
        play()
        scope.launch {
            delay(ms)
            if (!timerHold) restoreVolume()
        }
    }

    /**
     * Alarm stream set to the step just above the chosen level, then the exact level made in
     * software, so the slider is smooth and even 5% is quiet. The loudness enhancer only
     * kicks in above 80%.
     */
    private fun applyVolume() {
        val p = effectivePct.coerceIn(5, 100) / 100f
        runCatching {
            if (savedAlarmVolume == null) savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val level = kotlin.math.ceil(max * p).toInt().coerceIn(1, max)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
            gain = (p * max / level).coerceIn(0.02f, 1f)
        }
        boostMb = if (p > 0.8f) ((p - 0.8f) / 0.2f * 800).toInt() else 0
        tracks.forEach { runCatching { it.setVolume(gain) } }
        enhancers.forEach { e ->
            runCatching {
                e.setTargetGain(boostMb)
                e.enabled = boostMb > 0
            }
        }
    }

    private fun restoreVolume() {
        val v = savedAlarmVolume ?: return
        savedAlarmVolume = null
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, v, 0) }
    }

    private suspend fun hit(track: AudioTrack, vibrateMs: Long, flashMs: Long) {
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
        if (shouldVibrate) runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(vibrateMs, 255)) }
        if (flash && torchId != null) {
            scope.launch {
                runCatching { camera?.setTorchMode(torchId, true) }
                delay(flashMs)
                runCatching { camera?.setTorchMode(torchId, false) }
            }
        }
    }

    private suspend fun playVoice(clip: Clip) {
        val mp = MediaPlayer()
        var enhancer: LoudnessEnhancer? = null
        MicGate.begin()
        try {
            context.resources.openRawResourceFd(clip.res).use { fd ->
                mp.setAudioAttributes(attrs)
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            mp.prepare()
            mp.setVolume(gain, gain)
            if (boostMb > 0) enhancer = enhance(mp.audioSessionId, gainMb = boostMb * 3 / 4)
            if (shouldVibrate) runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)) }
            suspendCancellableCoroutine { cont ->
                currentVoice = cont
                mp.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                mp.setOnErrorListener { _, _, _ -> if (cont.isActive) cont.resume(Unit); true }
                cont.invokeOnCancellation { runCatching { mp.stop() } }
                mp.start()
            }
        } catch (_: Exception) {
            // a broken clip must never stop the timer
        } finally {
            currentVoice = null
            enhancer?.release()
            mp.release()
            MicGate.end()
        }
    }

    private fun staticTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    /** Android's loudness enhancer: a limiter that adds gain without clipping. */
    private fun enhance(sessionId: Int, gainMb: Int): LoudnessEnhancer? = runCatching {
        LoudnessEnhancer(sessionId).apply {
            setTargetGain(gainMb)
            enabled = gainMb > 0
        }
    }.getOrNull()

    companion object {
        const val TICK_GAP_MS = 230L
        const val NIGHT_PCT = 15

        @Volatile private var instance: SoundPlayer? = null
        fun get(context: Context): SoundPlayer =
            instance ?: synchronized(this) {
                instance ?: SoundPlayer(context.applicationContext).also { instance = it }
            }
    }
}
