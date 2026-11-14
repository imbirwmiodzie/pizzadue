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

    /**
     * true: alarm sound (own volume, plays on silent and through the phone's speaker even with
     * headphones). false: media sound (media volume, goes to Bluetooth / headphones like music).
     */
    var asAlarm = true
        set(v) {
            if (field == v) return
            val held = timerHold
            restoreVolume()
            field = v
            buildTracks()
            if (held) applyVolume()
        }

    /** Takes over everything the player needs from Settings. */
    fun configure(s: com.pizzadue.settings.Settings) {
        vibrate = s.vibrate
        flash = s.flash
        asAlarm = s.asAlarm
        volumePct = s.volume
        night = s.night
    }

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

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val mediaAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val attrs get() = if (asAlarm) alarmAttrs else mediaAttrs
    private val stream get() = if (asAlarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val shortPcm = ToneSynth.shortTick()
    private val longPcm = ToneSynth.longTick()
    private val startPcm = ToneSynth.startBeep()
    private lateinit var shortTrack: AudioTrack
    private lateinit var longTrack: AudioTrack
    private lateinit var startTrack: AudioTrack
    private var tracks: List<AudioTrack> = emptyList()
    private var enhancers: List<LoudnessEnhancer> = emptyList()

    /** (Re)creates the tick sounds on the current sound type (alarm or media). */
    private fun buildTracks() {
        enhancers.forEach { runCatching { it.release() } }
        tracks.forEach { runCatching { it.release() } }
        shortTrack = staticTrack(shortPcm)
        longTrack = staticTrack(longPcm)
        startTrack = staticTrack(startPcm)
        tracks = listOf(shortTrack, longTrack, startTrack)
        enhancers = tracks.mapNotNull { enhance(it.audioSessionId, gainMb = 0) }
        tracks.forEach { runCatching { it.setVolume(gain) } }
    }

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

    /** The user's own volume before we changed it, and on which stream. */
    private var savedVolume: Pair<Int, Int>? = null

    private val queue = Channel<Item>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        buildTracks()
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
            val st = stream
            if (savedVolume == null) savedVolume = st to audioManager.getStreamVolume(st)
            val max = audioManager.getStreamMaxVolume(st)
            val level = kotlin.math.ceil(max * p).toInt().coerceIn(1, max)
            audioManager.setStreamVolume(st, level, 0)
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
        val (st, v) = savedVolume ?: return
        savedVolume = null
        runCatching { audioManager.setStreamVolume(st, v, 0) }
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
