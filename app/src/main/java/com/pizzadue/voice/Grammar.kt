package com.pizzadue.voice

/**
 * Every phrase the app reacts to, per language. The same list is handed to the speech
 * recogniser as its grammar and used to turn recognised text back into a [Command],
 * so the two can never drift apart.
 */
object Grammar {
    private val bakeMinutes = 1..15
    private val bakeSeconds = (10..300 step 5)
    private val hydrations = 50..90
    private val ballCounts = 1..20

    val en: Map<String, Command> by lazy { buildEn() }
    val pl: Map<String, Command> by lazy { buildPl() }

    fun phrases(lang: Lang): Map<String, Command> = if (lang == Lang.EN) en else pl

    private fun buildEn(): Map<String, Command> {
        val m = LinkedHashMap<String, Command>()
        // "vai" and "waj" are not English words; "vie" is pronounced exactly like them.
        listOf("vie", "pizza in", "start").forEach { m[it] = Command.StartBake }
        listOf("preheat", "preheat oven").forEach { m[it] = Command.StartPreheat }
        listOf("pizza out", "stop").forEach { m[it] = Command.Stop }
        listOf("time left", "how long").forEach { m[it] = Command.TimeLeft }
        m["pause"] = Command.Pause
        m["resume"] = Command.Resume
        for (n in bakeMinutes) m["set ${NumberWords.en(n)} ${if (n == 1) "minute" else "minutes"}"] = Command.SetBake(n * 60)
        for (n in bakeSeconds) m["set ${NumberWords.en(n)} seconds"] = Command.SetBake(n)
        m["set one and a half minutes"] = Command.SetBake(90)
        m["set a minute and a half"] = Command.SetBake(90)
        m["bright"] = Command.Bright(true)
        m["normal screen"] = Command.Bright(false)
        for (n in hydrations) m["hydration ${NumberWords.en(n)}"] = Command.Hydration(n)
        for (n in ballCounts) m["dough for ${NumberWords.en(n)} ${if (n == 1) "ball" else "balls"}"] = Command.Dough(n)
        m["timer"] = Command.Tab(AppTab.TIMER)
        m["dough"] = Command.Tab(AppTab.DOUGH)
        m["settings"] = Command.Tab(AppTab.SETTINGS)
        m["mute"] = Command.Ticks(false)
        m["sound on"] = Command.Ticks(true)
        return m
    }

    private fun buildPl(): Map<String, Command> {
        val m = LinkedHashMap<String, Command>()
        m["start"] = Command.StartBake
        listOf("rozgrzej piec", "grzej piec").forEach { m[it] = Command.StartPreheat }
        listOf("wyjmij", "stop", "koniec").forEach { m[it] = Command.Stop }
        m["ile zostało"] = Command.TimeLeft
        m["pauza"] = Command.Pause
        m["dalej"] = Command.Resume
        for (n in bakeMinutes) {
            val unit = NumberWords.plForm(n, "minutę", "minuty", "minut")
            m["ustaw ${NumberWords.pl(n, feminine = true)} $unit"] = Command.SetBake(n * 60)
        }
        for (n in bakeSeconds) {
            val unit = NumberWords.plForm(n, "sekundę", "sekundy", "sekund")
            m["ustaw ${NumberWords.pl(n, feminine = true)} $unit"] = Command.SetBake(n)
        }
        m["ustaw półtorej minuty"] = Command.SetBake(90)
        m["jasno"] = Command.Bright(true)
        m["ekran normalny"] = Command.Bright(false)
        for (n in hydrations) m["nawodnienie ${NumberWords.pl(n)}"] = Command.Hydration(n)
        for (n in ballCounts) {
            val unit = NumberWords.plForm(n, "kulkę", "kulki", "kulek")
            m["ciasto na ${NumberWords.pl(n, feminine = true)} $unit"] = Command.Dough(n)
        }
        m["minutnik"] = Command.Tab(AppTab.TIMER)
        m["ciasto"] = Command.Tab(AppTab.DOUGH)
        m["ustawienia"] = Command.Tab(AppTab.SETTINGS)
        m["cisza"] = Command.Ticks(false)
        m["dźwięk"] = Command.Ticks(true)
        return m
    }

    /** Grammar JSON for the recogniser: all phrases plus "[unk]" to soak up everything else. */
    fun recognizerGrammar(lang: Lang): String =
        (phrases(lang).keys + "[unk]").joinToString(",", "[", "]") { "\"$it\"" }

    /**
     * Recognised text -> command. Exact phrase first; otherwise the longest phrase found
     * inside the text on word boundaries (the recogniser may glue "[unk]" or a second phrase on).
     */
    fun parse(text: String, lang: Lang): Command? {
        val clean = text.lowercase().replace("[unk]", " ").split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
        if (clean.isEmpty()) return null
        val map = phrases(lang)
        map[clean]?.let { return it }
        val padded = " $clean "
        return map.entries
            .filter { padded.contains(" ${it.key} ") }
            .maxByOrNull { it.key.length }
            ?.value
    }
}
