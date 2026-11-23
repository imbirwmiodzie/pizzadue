package com.vibepizzaiolo.voice

/**
 * The English and the Polish recogniser hear the same words and sometimes disagree: one
 * hears "ciao pizza start", the other "ciao pizza stop". Acting on both started the timer
 * and stopped it again at once. So what they hear within [windowSamples] of each other is
 * one decision, and the surer recogniser wins.
 *
 * Time is counted in audio samples, not on the clock, so the decision depends only on the sound.
 */
class Referee(private val voters: Int, private val windowSamples: Long) {
    private var best: Grammar.Heard? = null
    private var since = 0L
    private val voted = mutableSetOf<Lang>()

    /** [lang]'s recogniser heard [heard] when [at] samples had been listened to. */
    fun offer(heard: Grammar.Heard, lang: Lang, at: Long) {
        val b = best
        if (b == null || at - since > windowSamples) {
            best = heard
            since = at
            voted.clear()
        } else if (heard.confidence > b.confidence) {
            best = heard
        }
        voted += lang
    }

    /**
     * The decision, once every recogniser has had its say or the window is over; null meanwhile.
     * Each decision is handed out once.
     */
    fun decide(at: Long): Grammar.Heard? {
        val b = best ?: return null
        if (voted.size < voters && at - since < windowSamples) return null
        best = null
        voted.clear()
        return b
    }

    fun reset() {
        best = null
        voted.clear()
    }
}
