package com.pizzadue.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Tick sounds made in code, tuned for a phone speaker in a noisy kitchen:
 * energy around 2–4 kHz (where small speakers are loudest and hearing is most sensitive),
 * soft-clipped so the sound stays dense and loud, peaking at full scale.
 */
object ToneSynth {
    const val RATE = 48_000

    /** Short tick: a sharp, bright 90 ms "tak". */
    fun shortTick(): ShortArray = render(0.090) { t ->
        val env = attack(t, 0.002) * exp(-38.0 * t)
        env * (sin(2 * PI * 3150 * t) + 0.6 * sin(2 * PI * 4200 * t) + 0.35 * sin(2 * PI * 2100 * t))
    }

    /** Long tick: a 450 ms ringing "toooc", lower so it is clearly different from the short one. */
    fun longTick(): ShortArray = render(0.450) { t ->
        val env = attack(t, 0.004) * exp(-5.5 * t)
        env * (sin(2 * PI * 2000 * t) + 0.7 * sin(2 * PI * 3000 * t) + 0.4 * sin(2 * PI * 4000 * t))
    }

    private fun attack(t: Double, len: Double) = min(1.0, t / len)

    private inline fun render(seconds: Double, wave: (Double) -> Double): ShortArray {
        val n = (RATE * seconds).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            // drive into tanh: soft clipping raises loudness without harsh digital clipping
            val v = tanh(2.2 * wave(t)) / tanh(2.2)
            // 3 ms fade-out so the end does not click
            val fade = min(1.0, (n - i).toDouble() / (RATE * 0.003))
            out[i] = (v * fade * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
