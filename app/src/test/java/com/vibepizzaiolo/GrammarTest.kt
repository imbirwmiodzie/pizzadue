package com.vibepizzaiolo

import com.vibepizzaiolo.voice.Command
import com.vibepizzaiolo.voice.Grammar
import com.vibepizzaiolo.voice.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarTest {
    @Test fun samePhrasesInBothLanguages() {
        for (lang in Lang.entries) {
            assertEquals(Command.START, Grammar.parse("pizza napoli presto", lang))
            assertEquals(Command.START, Grammar.parse("  Pizza  Napoli Presto ", lang))
            assertEquals(Command.STOP, Grammar.parse("pizza basta", lang))
        }
    }

    @Test fun oldSingleWordsNoLongerWork() {
        for (lang in Lang.entries) {
            for (old in listOf("start", "stop", "vie", "pizza in", "pizza out", "wyjmij", "koniec")) {
                assertNull(old, Grammar.parse(old, lang))
            }
        }
    }

    @Test fun phraseInsideOtherSpeechIsIgnored() {
        assertNull(Grammar.parse("[unk] pizza napoli presto", Lang.EN))
        assertNull(Grammar.parse("pizza napoli presto [unk]", Lang.PL))
        assertNull(Grammar.parse("pizza basta pizza napoli presto", Lang.EN))
        assertNull(Grammar.parse("[unk] pizza basta", Lang.PL))
    }

    @Test fun partialPhrasesAreIgnored() {
        assertNull(Grammar.parse("pizza", Lang.EN))
        assertNull(Grammar.parse("pizza napoli", Lang.PL))
        assertNull(Grammar.parse("napoli presto", Lang.EN))
        assertNull(Grammar.parse("presto pizza napoli", Lang.EN))
    }

    @Test fun anythingElseIsIgnored() {
        assertNull(Grammar.parse("[unk]", Lang.EN))
        assertNull(Grammar.parse("", Lang.PL))
    }

    @Test fun recognizerGrammarListsEveryPhrasePlusUnknown() {
        val g = Grammar.recognizerGrammar(Lang.PL)
        assertTrue(g.startsWith("[") && g.endsWith("]"))
        assertTrue(g.contains("\"pizza napoli presto\"") && g.contains("\"pizza basta\"") && g.contains("\"[unk]\""))
    }
}
