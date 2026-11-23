package com.vibepizzaiolo

import com.vibepizzaiolo.voice.Command
import com.vibepizzaiolo.voice.Grammar
import com.vibepizzaiolo.voice.Grammar.Word
import com.vibepizzaiolo.voice.Lang
import com.vibepizzaiolo.voice.Referee
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarTest {
    @Test fun ciaoPizzaStartsAndStops() {
        assertEquals(Command.START, Grammar.parse("ciao pizza start"))
        assertEquals(Command.STOP, Grammar.parse("ciao pizza stop"))
        assertEquals(Command.STOP, Grammar.parse("[unk] ciao pizza stop [unk]"))
        assertEquals(Command.START, Grammar.parse("Ciao Pizza Start"))
    }

    /** What the old commands reacted to, all of it heard from the radio: none of it may act now. */
    @Test fun loneWordsFromTheRadioDoNothing() {
        for (text in listOf("stop", "start", "[unk] stop [unk]", "start stop", "pizza", "ciao", "ciao pizza",
            "pizza start", "pizza stop", "ciao start", "stop ciao pizza", "vie", "pizza in", "pizza out", "wyjmij", "koniec", "", "[unk]")) {
            assertNull(text, Grammar.parse(text))
        }
    }

    @Test fun theWordsMustComeInARow() {
        assertNull(Grammar.parse("ciao [unk] pizza start"))
        assertNull(Grammar.parse("ciao pizza [unk] stop"))
    }

    @Test fun confidenceIsThatOfTheLeastSureWord() {
        val heard = Grammar.parse(listOf(Word("[unk]", 0.2), Word("ciao", 0.9), Word("pizza", 0.55), Word("stop", 1.0)))
        assertEquals(Command.STOP, heard?.command)
        assertEquals(0.55, heard!!.confidence, 1e-9)
    }

    @Test fun twoPhrasesInOneGoTheSurerWins() {
        val words = listOf(Word("ciao", 0.9), Word("pizza", 0.9), Word("stop", 0.5), Word("ciao", 1.0), Word("pizza", 1.0), Word("start", 0.95))
        assertEquals(Command.START, Grammar.parse(words)?.command)
    }

    @Test fun recognizerGrammarListsBothPhrasesPlusUnknown() {
        assertEquals("[\"ciao pizza start\",\"ciao pizza stop\",\"[unk]\"]", Grammar.recognizerGrammar())
        assertTrue(Grammar.phrases.keys.all { it.startsWith(Grammar.WAKE + " ") })
    }
}

class RefereeTest {
    private val second = 16_000L
    private fun heard(cmd: Command, conf: Double) = Grammar.Heard(cmd, conf)

    @Test fun whenTheRecognisersDisagreeTheSurerOneWins() {
        val r = Referee(voters = 2, windowSamples = second)
        r.offer(heard(Command.STOP, 0.7), Lang.PL, 10 * second)
        assertNull("waits for the other recogniser", r.decide(10 * second))
        r.offer(heard(Command.START, 0.95), Lang.EN, 10 * second + 3_200)
        assertEquals(Command.START, r.decide(10 * second + 3_200)?.command)
        assertNull("one decision, handed out once", r.decide(10 * second + 4_800))
    }

    @Test fun theFirstSurerVerdictIsKept() {
        val r = Referee(2, second)
        r.offer(heard(Command.START, 0.9), Lang.EN, 0)
        r.offer(heard(Command.STOP, 0.6), Lang.PL, 1_600)
        assertEquals(Command.START, r.decide(1_600)?.command)
    }

    @Test fun ifOnlyOneHearsItItActsWhenTheWindowIsOver() {
        val r = Referee(2, second)
        r.offer(heard(Command.STOP, 0.8), Lang.EN, 5 * second)
        assertNull(r.decide(5 * second + 8_000))
        assertEquals(Command.STOP, r.decide(6 * second)?.command)
    }

    @Test fun withOneLanguageOnItActsAtOnce() {
        val r = Referee(1, second)
        r.offer(heard(Command.START, 0.8), Lang.PL, 0)
        assertEquals(Command.START, r.decide(0)?.command)
    }

    @Test fun commandsFarApartAreSeparateDecisions() {
        val r = Referee(2, second)
        r.offer(heard(Command.START, 0.9), Lang.EN, 0)
        assertEquals(Command.START, r.decide(second)?.command)
        r.offer(heard(Command.STOP, 0.7), Lang.EN, 100 * second)
        r.offer(heard(Command.STOP, 0.8), Lang.PL, 100 * second + 1_600)
        assertEquals(Command.STOP, r.decide(100 * second + 1_600)?.command)
    }

    @Test fun resetForgetsWhatWasHeard() {
        val r = Referee(2, second)
        r.offer(heard(Command.START, 0.9), Lang.EN, 0)
        r.reset()
        assertNull(r.decide(10 * second))
    }
}
