package com.vibepizzaiolo

import com.vibepizzaiolo.voice.Command
import com.vibepizzaiolo.voice.Grammar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarTest {
    @Test fun startAndStop() {
        assertEquals(Command.START, Grammar.parse("pizza napoli presto"))
        assertEquals(Command.START, Grammar.parse("  Pizza  Napoli Presto "))
        assertEquals(Command.STOP, Grammar.parse("pizza basta"))
    }

    @Test fun oldWordsNoLongerWork() {
        for (old in listOf("start", "stop", "vie", "pizza in", "pizza out", "wyjmij", "koniec")) {
            assertNull(old, Grammar.parse(old))
        }
    }

    @Test fun phraseInsideOtherSpeechIsIgnored() {
        assertNull(Grammar.parse("[unk] pizza napoli presto"))
        assertNull(Grammar.parse("pizza napoli presto [unk]"))
        assertNull(Grammar.parse("pizza basta pizza napoli presto"))
        assertNull(Grammar.parse("pesto pizza napoli presto su sta"))
        assertNull(Grammar.parse("la pizza pronta basta"))
    }

    @Test fun partialPhrasesAndDecoysAreIgnored() {
        assertNull(Grammar.parse("pizza"))
        assertNull(Grammar.parse("pizza napoli"))
        assertNull(Grammar.parse("napoli presto"))
        assertNull(Grammar.parse("presto pizza napoli"))
        assertNull(Grammar.parse("pizza pasta"))
        for (d in Grammar.decoys) assertNull(d, Grammar.parse(d))
    }

    @Test fun anythingElseIsIgnored() {
        assertNull(Grammar.parse("[unk]"))
        assertNull(Grammar.parse(""))
    }

    @Test fun decoysNeverClashWithCommandWords() {
        val commandWords = Grammar.phrases.keys.flatMap { it.split(" ") }.toSet()
        assertTrue(Grammar.decoys.none { it in commandWords })
        assertEquals(Grammar.decoys.size, Grammar.decoys.toSet().size)
    }

    @Test fun recognizerGrammarListsCommandsDecoysAndUnknown() {
        val g = Grammar.recognizerGrammar()
        assertTrue(g.startsWith("[") && g.endsWith("]"))
        assertTrue(g.contains("\"pizza napoli presto\"") && g.contains("\"pizza basta\""))
        assertTrue(g.contains("\"pasta\"") && g.contains("\"[unk]\""))
    }
}
