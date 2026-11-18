package com.vibepizzaiolo.voice

/**
 * Every phrase the app reacts to. The same list is handed to the speech recogniser as its
 * grammar and used to turn recognised text back into a [Command], so the two can never drift apart.
 *
 * The phrases are Italian and recognised by the Italian speech model, which knows these words
 * best whatever accent says them. They are several words in a fixed order that make no sense
 * as a sentence, so a radio or TV in the kitchen practically never says them on their own.
 */
object Grammar {
    val phrases: Map<String, Command> = linkedMapOf(
        "pizza napoli presto" to Command.START,
        "pizza basta" to Command.STOP,
    )

    /**
     * Decoys: ordinary words the recogniser may pick instead of a command. A grammar of only
     * the commands forces every bit of other speech towards them ("pizza, pasta" on the radio
     * became "pizza basta"); with these to choose from, other speech lands on them and is ignored.
     * Words that sound like the commands matter most. Every word must be in the Italian model.
     */
    val decoys: List<String> = listOf(
        // sound like the command words
        "pasta", "pizze", "pizzeria", "napoletana", "pronta", "pronto", "presta", "posta", "festa", "vasta",
        "casta", "bastone", "piazza", "pazza", "polizia", "poli", "polo", "nap", "napo", "pista", "prato",
        "resto", "pesto", "questo", "basso",
        // everyday Italian
        "bella", "ciao", "grazie", "no", "che", "come", "sono", "non", "per", "una", "uno", "il", "la",
        "di", "da", "in", "con", "su", "ma", "poi", "oggi", "tempo", "mano", "bene", "buono", "casa", "vino",
        "acqua", "forno", "fuoco", "mozzarella", "margherita", "radio", "musica", "sport", "dopo", "prima",
        "tutto", "niente", "sempre", "mai", "ancora", "adesso", "subito", "piano", "forte", "a", "e", "o",
        "ho", "va", "fa", "sta", "tre", "due", "cinque", "dieci",
    )

    /** Grammar JSON for the recogniser: the commands, the decoys and "[unk]" to soak up everything else. */
    fun recognizerGrammar(): String =
        (phrases.keys + decoys + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

    /**
     * Recognised text -> command. The whole utterance must be exactly one command:
     * anything else heard in the same breath (a decoy, an "[unk]", another command) means
     * it was part of other speech, such as the radio, and is ignored.
     */
    fun parse(text: String): Command? {
        val clean = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
        if (clean.isEmpty()) return null
        return phrases[clean]
    }
}
