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
 * Plays ticks and voice clips one after another, as loud as the phone allows:
 * alarm stream (loud even when media volume is down), a loudness enhancer on every sound,
 * alarm volume raised to maximum while the timer runs, plus optional vibration and
 * flashlight blink on each tick.
 */
class SoundPlayer private constructor(private val context: Context) {
    /** Options taken from Settings. */
    @Volatile var vibrate = true
    @Volatile var flash = false
    @Volatile var maxVolume = true

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
    private val enhancers = listOf(shortTrack, longTrack, startTrack).mapNotNull { enhance(it.audioSessionId) }

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

    /** Raises the alarm stream to maximum while the timer runs (if enabled). */
    fun boostVolume() {
        if (!maxVolume || savedAlarmVolume != null) return
        runCatching {
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        }
    }

    /** Puts the alarm volume back to what it was before the timer started. */
    fun restoreVolume() {
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
        if (vibrate) runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(vibrateMs, 255)) }
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
            enhancer = enhance(mp.audioSessionId, gainMb = 600)
            if (vibrate) runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)) }
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
    private fun enhance(sessionId: Int, gainMb: Int = 800): LoudnessEnhancer? = runCatching {
        LoudnessEnhancer(sessionId).apply {
            setTargetGain(gainMb)
            enabled = true
        }
    }.getOrNull()

    companion object {
        const val TICK_GAP_MS = 230L

        @Volatile private var instance: SoundPlayer? = null
        fun get(context: Context): SoundPlayer =
            instance ?: synchronized(this) {
                instance ?: SoundPlayer(context.applicationContext).also { instance = it }
            }
    }
}
