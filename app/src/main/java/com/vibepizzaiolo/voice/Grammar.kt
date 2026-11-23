package com.vibepizzaiolo.voice

/**
 * The phrases the app reacts to: "ciao pizza" and then the command, like "OK Google".
 *
 * Single words such as "stop" and "start" came out of the radio dozens of times a minute,
 * so on its own a word does nothing: the three words have to come in a row. Both speech
 * models (English and Polish) know all three words, so the same phrases work in both.
 *
 * The same list is handed to the speech recogniser as its grammar and used to turn
 * recognised words back into a [Command], so the two can never drift apart.
 */
object Grammar {
    const val WAKE = "ciao pizza"

    val phrases: Map<String, Command> = linkedMapOf(
        "$WAKE start" to Command.START,
        "$WAKE stop" to Command.STOP,
    )

    /** One recognised word and how sure the recogniser is of it (0..1). */
    data class Word(val word: String, val conf: Double = 1.0)

    /** A command heard, with the confidence of its least certain word. */
    data class Heard(val command: Command, val confidence: Double)

    /** Grammar JSON for the recogniser: the phrases plus "[unk]" to soak up everything else. */
    fun recognizerGrammar(): String =
        (phrases.keys + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

    /**
     * Recognised words -> command: a phrase as three words in a row, anywhere in what was heard.
     * Its confidence is that of its least certain word, so a mumble around it does not count.
     * If both phrases are in there, the surer one wins.
     */
    fun parse(words: List<Word>): Heard? {
        val w = words.map { it.copy(word = it.word.lowercase()) }
        val size = phrases.keys.first().split(' ').size
        return (0..w.size - size).mapNotNull { i ->
            val run = w.subList(i, i + size)
            phrases[run.joinToString(" ") { it.word }]?.let { Heard(it, run.minOf { word -> word.conf }) }
        }.maxByOrNull { it.confidence }
    }

    /** Recognised text -> command, every word taken as certain. */
    fun parse(text: String): Command? =
        parse(text.split(Regex("\\s+")).filter { it.isNotBlank() }.map { Word(it) })?.command
}
