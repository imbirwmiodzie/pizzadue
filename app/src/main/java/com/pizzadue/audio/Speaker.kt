package com.pizzadue.audio

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pizzadue.voice.Lang
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Spoken replies ("1 minuta 12 sekund") in English or Polish through the phone's own TTS engine. */
class Speaker(context: Context) {
    private var ready = false
    private val ids = AtomicInteger()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = MicGate.begin()
            override fun onDone(utteranceId: String?) = MicGate.end()
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = MicGate.end()
            override fun onStop(utteranceId: String?, interrupted: Boolean) = MicGate.end()
        })
    }

    fun speak(text: String, lang: Lang) {
        if (!ready || text.isBlank()) return
        tts.language = if (lang == Lang.PL) Locale("pl", "PL") else Locale.US
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${ids.incrementAndGet()}")
    }

    fun shutdown() = tts.shutdown()
}
