package com.vibepizzaiolo.voice

/**
 * Every phrase the app reacts to, per language. The same list is handed to the speech
 * recogniser as its grammar and used to turn recognised text back into a [Command],
 * so the two can never drift apart. A short list keeps recognition reliable in a noisy kitchen.
 */
object Grammar {
    val en: Map<String, Command> = linkedMapOf(
        // "vai" and "waj" are not English words; "vie" is pronounced exactly like them.
        "vie" to Command.START,
        "pizza in" to Command.START,
        "start" to Command.START,
        "pizza out" to Command.STOP,
        "stop" to Command.STOP,
    )

    val pl: Map<String, Command> = linkedMapOf(
        "start" to Command.START,
        "wyjmij" to Command.STOP,
        "stop" to Command.STOP,
        "koniec" to Command.STOP,
    )

    fun phrases(lang: Lang): Map<String, Command> = if (lang == Lang.EN) en else pl

    /** Grammar JSON for the recogniser: all phrases plus "[unk]" to soak up everything else. */
    fun recognizerGrammar(lang: Lang): String =
        (phrases(lang).keys + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

    /** Recognised text -> command: the longest known phrase found in the text on word boundaries. */
    fun parse(text: String, lang: Lang): Command? {
        val clean = text.lowercase().replace("[unk]", " ").split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
        if (clean.isEmpty()) return null
        val padded = " $clean "
        return phrases(lang).entries
            .filter { padded.contains(" ${it.key} ") }
            .maxByOrNull { it.key.length }
            ?.value
    }
}
