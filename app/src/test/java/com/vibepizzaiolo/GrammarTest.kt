package com.vibepizzaiolo

import com.vibepizzaiolo.voice.Command
import com.vibepizzaiolo.voice.Grammar
import com.vibepizzaiolo.voice.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarTest {
    @Test fun englishStartAndStop() {
        assertEquals(Command.START, Grammar.parse("vie", Lang.EN))
        assertEquals(Command.START, Grammar.parse("pizza in", Lang.EN))
        assertEquals(Command.START, Grammar.parse("start", Lang.EN))
        assertEquals(Command.STOP, Grammar.parse("pizza out", Lang.EN))
        assertEquals(Command.STOP, Grammar.parse("[unk] stop", Lang.EN))
    }

    @Test fun polishStartAndStop() {
        assertEquals(Command.START, Grammar.parse("start", Lang.PL))
        assertEquals(Command.STOP, Grammar.parse("wyjmij", Lang.PL))
        assertEquals(Command.STOP, Grammar.parse("koniec", Lang.PL))
    }

    @Test fun anythingElseIsIgnored() {
        assertNull(Grammar.parse("[unk]", Lang.EN))
        assertNull(Grammar.parse("", Lang.PL))
        assertNull(Grammar.parse("pizza", Lang.EN))
    }

    @Test fun recognizerGrammarListsEveryPhrasePlusUnknown() {
        val g = Grammar.recognizerGrammar(Lang.PL)
        assertTrue(g.startsWith("[") && g.endsWith("]"))
        assertTrue(g.contains("\"wyjmij\"") && g.contains("\"[unk]\""))
    }
}
