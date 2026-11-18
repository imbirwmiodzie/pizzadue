package com.vibepizzaiolo.voice

/**
 * Every phrase the app reacts to, per language. The same list is handed to the speech
 * recogniser as its grammar and used to turn recognised text back into a [Command],
 * so the two can never drift apart. A short list keeps recognition reliable in a noisy kitchen.
 *
 * The phrases are the same in both languages: every word is in both the English and the Polish
 * speech model and sounds almost the same in either, so they work whichever list is on.
 * They are several words in a fixed order that make no sense as a sentence, so a radio or TV
 * in the kitchen practically never says them on their own.
 */
object Grammar {
    private val shared: Map<String, Command> = linkedMapOf(
        "pizza napoli presto" to Command.START,
        "pizza basta" to Command.STOP,
    )

    val en: Map<String, Command> = shared
    val pl: Map<String, Command> = shared

    fun phrases(lang: Lang): Map<String, Command> = if (lang == Lang.EN) en else pl

    /** Grammar JSON for the recogniser: all phrases plus "[unk]" to soak up everything else. */
    fun recognizerGrammar(lang: Lang): String =
        (phrases(lang).keys + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

    /**
     * Recognised text -> command. The whole utterance must be exactly one known phrase:
     * anything else heard in the same breath (an "[unk]", another phrase) means it was
     * part of other speech, such as the radio, and is ignored.
     */
    fun parse(text: String, lang: Lang): Command? {
        val clean = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
        if (clean.isEmpty()) return null
        return phrases(lang)[clean]
    }
}
