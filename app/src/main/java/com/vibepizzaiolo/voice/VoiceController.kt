package com.vibepizzaiolo.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.vibepizzaiolo.audio.MicGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

sealed interface VoiceStatus {
    data object Off : VoiceStatus
    data object NoPermission : VoiceStatus
    data object Preparing : VoiceStatus
    data class Listening(val langs: Set<Lang>) : VoiceStatus
    data class Error(val message: String) : VoiceStatus
}

/**
 * Listens continuously while the app is open. One microphone stream feeds an English
 * and a Polish recogniser at once; each only knows the phrases in [Grammar].
 */
class VoiceController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCommand: (Command, Lang, String) -> Unit,
) {
    private val _status = MutableStateFlow<VoiceStatus>(VoiceStatus.Off)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private var job: Job? = null
    private var lastCommand: Command? = null
    private var lastCommandAt = 0L

    fun start(langs: Set<Lang>) {
        stop()
        if (langs.isEmpty()) { _status.value = VoiceStatus.Off; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = VoiceStatus.NoPermission
            return
        }
        job = scope.launch(Dispatchers.IO) {
            try {
                if (Models.loaded.keys.containsAll(langs).not()) _status.value = VoiceStatus.Preparing
                val recognizers = langs.mapNotNull { lang ->
                    val model = Models.get(context, lang) ?: return@mapNotNull null
                    lang to Recognizer(model, SAMPLE_RATE.toFloat(), Grammar.recognizerGrammar(lang)).apply { setWords(true) }
                }
                if (recognizers.isEmpty()) {
                    _status.value = VoiceStatus.Error("Speech models are missing from this build")
                    return@launch
                }
                listen(recognizers)
            } catch (e: Exception) {
                Log.e(TAG, "voice failed", e)
                _status.value = VoiceStatus.Error(e.message ?: "Voice recognition failed")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        if (_status.value !is VoiceStatus.NoPermission) _status.value = VoiceStatus.Off
    }

    @SuppressLint("MissingPermission") // checked in start()
    private suspend fun listen(recognizers: List<Pair<Lang, Recognizer>>) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _status.value = VoiceStatus.Error("Microphone is busy")
            return
        }
        val buf = ShortArray(SAMPLE_RATE / 10) // 100 ms
        var wasMuted = false
        try {
            record.startRecording()
            _status.value = VoiceStatus.Listening(recognizers.map { it.first }.toSet())
            val ctx = kotlin.coroutines.coroutineContext
            while (ctx.isActive) {
                val n = record.read(buf, 0, buf.size)
                // a dead microphone must show as an error, not as "listening" that never hears anything
                if (n < 0) throw IllegalStateException("Microphone stopped (error $n). Reopen the app.")
                if (n == 0) continue
                if (MicGate.isMuted) {
                    // the app itself is talking: do not listen to it
                    if (!wasMuted) recognizers.forEach { it.second.reset() }
                    wasMuted = true
                    continue
                }
                wasMuted = false
                for ((lang, rec) in recognizers) {
                    if (rec.acceptWaveForm(buf, n)) handle(lang, rec.result)
                }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            recognizers.forEach { it.second.close() }
        }
    }

    private suspend fun handle(lang: Lang, json: String) {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        val text = obj.optString("text").trim()
        if (text.isEmpty() || text == "[unk]") return
        // average word confidence: drop guesses forced out of random kitchen noise
        val words = obj.optJSONArray("result")
        if (words != null && words.length() > 0) {
            var sum = 0.0
            for (i in 0 until words.length()) sum += words.getJSONObject(i).optDouble("conf", 1.0)
            if (sum / words.length() < MIN_CONFIDENCE) return
        }
        val cmd = Grammar.parse(text, lang) ?: return
        val now = SystemClock.elapsedRealtime()
        // both recognisers may hear the same words; act once
        if (cmd == lastCommand && now - lastCommandAt < 2000) return
        lastCommand = cmd
        lastCommandAt = now
        withContext(Dispatchers.Main) { onCommand(cmd, lang, text) }
    }

    /** Loaded models stay in memory for the life of the process; loading takes a few seconds. */
    private object Models {
        val loaded = mutableMapOf<Lang, Model>()

        @Synchronized fun get(context: Context, lang: Lang): Model? {
            loaded[lang]?.let { return it }
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val dir = ModelInstaller.ensure(context, lang) ?: return null
            return Model(dir.absolutePath).also { loaded[lang] = it }
        }
    }

    private companion object {
        const val TAG = "VibePizzaioloVoice"
        const val SAMPLE_RATE = 16_000
        const val MIN_CONFIDENCE = 0.6
    }
}
