package com.pizzadue

import com.pizzadue.voice.AppTab
import com.pizzadue.voice.Command
import com.pizzadue.voice.Grammar
import com.pizzadue.voice.Lang
import com.pizzadue.voice.NumberWords
import com.pizzadue.voice.Replies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class GrammarTest {
    @Test fun numbers() {
        assertEquals("sixty five", NumberWords.en(65))
        assertEquals("one hundred twenty", NumberWords.en(120))
        assertEquals("sześćdziesiąt pięć", NumberWords.pl(65))
        assertEquals("sto dwadzieścia", NumberWords.pl(120))
        assertEquals("jedną", NumberWords.pl(1, feminine = true))
        assertEquals("dwadzieścia dwie", NumberWords.pl(22, feminine = true))
        assertEquals("kulki", NumberWords.plForm(22, "kulkę", "kulki", "kulek"))
        assertEquals("kulek", NumberWords.plForm(12, "kulkę", "kulki", "kulek"))
    }

    @Test fun english() {
        assertEquals(Command.StartBake, Grammar.parse("vie", Lang.EN))
        assertEquals(Command.StartBake, Grammar.parse("pizza in", Lang.EN))
        assertEquals(Command.SetBake(180), Grammar.parse("set three minutes", Lang.EN))
        assertEquals(Command.SetBake(90), Grammar.parse("set ninety seconds", Lang.EN))
        assertEquals(Command.Hydration(65), Grammar.parse("hydration sixty five", Lang.EN))
        assertEquals(Command.Dough(6), Grammar.parse("dough for six balls", Lang.EN))
        assertEquals(Command.Tab(AppTab.DOUGH), Grammar.parse("dough", Lang.EN))
        assertEquals(Command.Stop, Grammar.parse("[unk] pizza out", Lang.EN))
        assertNull(Grammar.parse("[unk]", Lang.EN))
    }

    @Test fun polish() {
        assertEquals(Command.StartPreheat, Grammar.parse("rozgrzej piec", Lang.PL))
        assertEquals(Command.Stop, Grammar.parse("wyjmij", Lang.PL))
        assertEquals(Command.TimeLeft, Grammar.parse("ile zostało", Lang.PL))
        assertEquals(Command.SetBake(90), Grammar.parse("ustaw półtorej minuty", Lang.PL))
        assertEquals(Command.SetBake(120), Grammar.parse("ustaw dwie minuty", Lang.PL))
        assertEquals(Command.SetBake(300), Grammar.parse("ustaw pięć minut", Lang.PL))
        assertEquals(Command.Dough(6), Grammar.parse("ciasto na sześć kulek", Lang.PL))
        assertEquals(Command.Dough(1), Grammar.parse("ciasto na jedną kulkę", Lang.PL))
        assertEquals(Command.Hydration(65), Grammar.parse("nawodnienie sześćdziesiąt pięć", Lang.PL))
    }

    @Test fun repliesNeverContainACommandPhrase() {
        val samples = listOf(
            Replies.bakeStarted(90, Lang.EN), Replies.preheatStarted(1800, Lang.EN), Replies.stopped(75, Lang.EN),
            Replies.left(42, Lang.EN), Replies.bakeSet(120, Lang.EN),
        ).map { it to Lang.EN } + listOf(
            Replies.bakeStarted(90, Lang.PL), Replies.preheatStarted(1800, Lang.PL), Replies.stopped(75, Lang.PL),
            Replies.left(42, Lang.PL), Replies.bakeSet(120, Lang.PL),
        ).map { it to Lang.PL }
        for ((text, lang) in samples) {
            val words = text.lowercase().replace(Regex("[^\\p{L}\\d ]"), " ")
            assertNull("'$text' would trigger a command", Grammar.parse(words, lang))
        }
    }

    @Test fun durationsSpoken() {
        assertEquals("1 minute 30 seconds", Replies.duration(90, Lang.EN))
        assertEquals("1 minuta 30 sekund", Replies.duration(90, Lang.PL))
        assertEquals("2 minuty", Replies.duration(120, Lang.PL))
        assertEquals("22 sekundy", Replies.duration(22, Lang.PL))
    }

    /** Writes the grammar word lists so scripts/check_vocab.py can check them against the models. */
    @Test fun dumpGrammar() {
        val out = File("build/grammar").apply { mkdirs() }
        for (lang in Lang.entries) {
            File(out, "${lang.name.lowercase()}.txt").writeText(Grammar.phrases(lang).keys.joinToString("\n"))
        }
    }
}
