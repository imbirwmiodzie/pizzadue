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
 * and a Polish recogniser at once; each only knows the phrases in [Grammar]
 * ("ciao pizza start", "ciao pizza stop"). When they disagree, the surer one wins ([Referee]).
 */
class VoiceController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onCommand: (Command) -> Unit,
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
                    lang to Recognizer(model, SAMPLE_RATE.toFloat(), Grammar.recognizerGrammar()).apply { setWords(true) }
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
        val referee = Referee(recognizers.size, SETTLE_SAMPLES)
        var heardSamples = 0L
        try {
            record.startRecording()
            _status.value = VoiceStatus.Listening(recognizers.map { it.first }.toSet())
            val ctx = kotlin.coroutines.coroutineContext
            while (ctx.isActive) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) continue
                if (MicGate.isMuted) {
                    // the app itself is talking: do not listen to it
                    if (!wasMuted) {
                        recognizers.forEach { it.second.reset() }
                        referee.reset()
                    }
                    wasMuted = true
                    continue
                }
                wasMuted = false
                heardSamples += n
                for ((lang, rec) in recognizers) {
                    if (rec.acceptWaveForm(buf, n)) heard(rec.result)?.let { referee.offer(it, lang, heardSamples) }
                }
                referee.decide(heardSamples)?.let { act(it) }
            }
        } finally {
            runCatching { record.stop() }
            record.release()
            recognizers.forEach { it.second.close() }
        }
    }

    /** Recogniser result JSON -> the command in it, if it is sure enough of every word of the phrase. */
    private fun heard(json: String): Grammar.Heard? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val list = obj.optJSONArray("result") ?: return null
        val words = (0 until list.length()).map {
            val w = list.getJSONObject(it)
            Grammar.Word(w.optString("word"), w.optDouble("conf", 1.0))
        }
        // drop guesses forced out of kitchen noise
        return Grammar.parse(words)?.takeIf { it.confidence >= MIN_CONFIDENCE }
    }

    private suspend fun act(heard: Grammar.Heard) {
        val now = SystemClock.elapsedRealtime()
        // the same command said twice in a row: act once
        if (heard.command == lastCommand && now - lastCommandAt < 2000) return
        lastCommand = heard.command
        lastCommandAt = now
        withContext(Dispatchers.Main) { onCommand(heard.command) }
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
        /** Lowest confidence of a phrase's words; tested on synthetic speech with radio and fan noise behind. */
        const val MIN_CONFIDENCE = 0.6
        /** How long the second recogniser may take to give its verdict on the same words: 1 s. */
        const val SETTLE_SAMPLES = SAMPLE_RATE.toLong()
    }
}
