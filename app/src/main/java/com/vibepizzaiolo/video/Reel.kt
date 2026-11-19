package com.vibepizzaiolo.video

import com.vibepizzaiolo.settings.SealText
import com.vibepizzaiolo.share.CardInfo
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** How the reel looks. The footage and the cutting are the same; the words and sounds differ. */
enum class ReelStyle(val label: String, val hint: String) {
    STATS("Stats", "your numbers on the shots"),
    ASMR("ASMR", "the sound, almost no text"),
    FUNNY("Funny", "the cartoon pizzaiolo talks"),
    CLEAN("No text", "write your own in Instagram or TikTok"),
    ;

    /** The quality seal and the "La pizza è pronta!" voice belong to the talkative styles. */
    val seal: Boolean get() = this == STATS || this == FUNNY
    val voice: Boolean get() = this == STATS || this == FUNNY

    companion object {
        fun of(i: Int) = entries.getOrElse(i) { STATS }
    }
}

/** A pizza night, for the Serata reel: its numbers replace the single pizza's. */
data class NightInfo(
    val title: String,
    val pizzas: Int,
    val guests: List<String>,
    val favourite: String?,
    val favouriteCount: Int,
)

/**
 * The bake cam's recording: the phone filmed the oven from before "vai!" to after "pizza out".
 * [launchMs] and [outMs] are where the bake started and ended, in milliseconds of [file].
 */
data class BakeCamClip(val file: File, val durationMs: Long, val launchMs: Long, val outMs: Long) {
    val bakeMs: Long get() = outMs - launchMs
}

/** Everything the reel prints, besides the footage. */
data class ReelInfo(
    val card: CardInfo,
    val style: ReelStyle = ReelStyle.STATS,
    /** "@handle" or a name; empty prints none. */
    val handle: String = "",
    val ovenC: Int = 0,
    val fermentH: Int = 0,
    val seriesGoal: Int = 0,
    val sealText: SealText = SealText.QUALITA,
    val appTag: Boolean = true,
    val night: NightInfo? = null,
) {
    val seal: Boolean get() = style.seal && sealText != SealText.NONE

    /** "Pizza nº 47 / 100" while the series runs, else "Pizza nº 47". */
    val pizzaTitle: String
        get() = if (seriesGoal > 0 && card.pizzaNo <= seriesGoal) "Pizza nº ${card.pizzaNo} / $seriesGoal" else "Pizza nº ${card.pizzaNo}"

    /** The handle with an @ when it looks like one ("pizza_marco" → "@pizza_marco"; "Marco R." stays). */
    val handleText: String
        get() {
            val h = handle.trim()
            if (h.isEmpty()) return ""
            return if (h.startsWith("@") || h.contains(' ')) h else "@$h"
        }
}

/** Which footage a shot comes from. */
enum class Source { PIZZA, BAKECAM }

/** The kinds of shot; each has its own words on it. */
enum class Kind { HOOK, LAUNCH, BAKE, PULL, TOP, AROUND, RIM, UNDER, STAMP, SLICE, ENDING }

/**
 * One piece of the reel: [range] in milliseconds of its [source]. [index] tells apart shots of the
 * same kind (the two cuts around, the pieces of the bake). [endingFromMs] is where, inside this
 * shot, the ending (pizzaiolo, handle) starts; null when it has none.
 */
data class Shot(
    val kind: Kind,
    val range: LongRange,
    val source: Source = Source.PIZZA,
    val index: Int = 0,
    val endingFromMs: Long? = null,
) {
    val lengthMs: Long get() = range.last - range.first
}

/** The pieces of the guided recording, before choosing what goes into the reel. */
data class Parts(
    val top: LongRange,
    val orbit: LongRange,
    val rim: LongRange,
    val under: LongRange?,
    val slice: LongRange?,
)

object ReelPlanner {
    const val MIN_RECORDING_MS = 5_000L
    /** From this long the recording is taken to follow the five-step guide (with underside and slice). */
    const val FIVE_STEPS_MS = 18_000L
    const val HOOK_MS = 1_200L
    const val ENDING_MS = 2_100L
    const val STAMP_MS = 2_200L
    /** Pieces of the bake shown as a time-lapse, and how long each one is on screen. */
    const val BAKE_PIECES = 5
    const val BAKE_PIECE_MS = 400L

    private fun Long.of(a: Double) = (this * a).toLong()
    private fun span(d: Long, a: Double, b: Double) = d.of(a)..d.of(b)

    /** Splits a recording of [d] ms the way the guide asked for it to be filmed. */
    fun parts(d: Long): Parts? {
        if (d < MIN_RECORDING_MS) return null
        if (d >= FIVE_STEPS_MS) {
            // from above ~2 s · around ~8 s · rim ~4 s · underneath ~3 s · a slice ~4 s
            return Parts(
                top = span(d, 0.015, 0.11),
                orbit = span(d, 0.12, 0.48),
                rim = span(d, 0.49, 0.67),
                under = span(d, 0.68, 0.81),
                slice = span(d, 0.82, 1.0),
            )
        }
        return if (d >= 12_000) {
            Parts(300L..2_300L, 2_600L..(d - 4_600), (d - 4_300)..(d - 300), null, null)
        } else {
            Parts(span(d, 0.0, 0.18), span(d, 0.20, 0.66), span(d, 0.70, 1.0), null, null)
        }
    }

    /** [len] ms of [r] starting at [at] (clamped so it fits); the whole of [r] if it is shorter. */
    private fun piece(r: LongRange, len: Long, at: Long): LongRange {
        val l = min(len, r.last - r.first)
        val s = at.coerceIn(r.first, r.last - l)
        return s..(s + l)
    }

    private fun centred(r: LongRange, len: Long, where: Double = 0.5): LongRange {
        val l = min(len, r.last - r.first)
        return piece(r, l, r.first + ((r.last - r.first - l) * where).toLong())
    }

    /**
     * The reel, shot by shot. It opens on the crunch ([crunchAtMs], the loudest moment of the
     * rim, if the sound could be read), then the bake from the bake cam if there is one, then the
     * pizza; the seal lands on moving footage, and it ends on the pizza with the pizzaiolo, so
     * it runs straight back into the crunch when it loops.
     */
    fun plan(durationMs: Long, crunchAtMs: Long? = null, seal: Boolean = true, bakeCam: BakeCamClip? = null): List<Shot>? {
        val p = parts(durationMs) ?: return null
        val shots = mutableListOf<Shot>()
        val rimLen = p.rim.last - p.rim.first
        val peak = crunchAtMs?.takeIf { it in p.rim } ?: (p.rim.first + rimLen / 3)

        // 1. the hook: the crunch itself, from the first frame
        shots += Shot(Kind.HOOK, piece(p.rim, HOOK_MS, peak - 300))

        // 2. the bake, when the bake cam filmed it: launch, a time-lapse with the clock, the pull-out
        val cam = bakeCam?.takeIf { it.bakeMs >= 10_000 && it.outMs <= it.durationMs }
        if (cam != null) {
            val all = 0L..cam.durationMs
            shots += Shot(Kind.LAUNCH, piece(all, 1_600, cam.launchMs - 1_100), Source.BAKECAM)
            val from = cam.launchMs + 1_500
            val to = cam.outMs - 1_500
            for (i in 0 until BAKE_PIECES) {
                val at = from + (to - from - BAKE_PIECE_MS) * i / (BAKE_PIECES - 1)
                shots += Shot(Kind.BAKE, at..(at + BAKE_PIECE_MS), Source.BAKECAM, index = i)
            }
            shots += Shot(Kind.PULL, piece(all, 1_800, cam.outMs - 700), Source.BAKECAM)
        }

        // 3. the pizza: from above, around, the rim (with the crunch again), underneath
        shots += Shot(Kind.TOP, centred(p.top, 2_000))
        val orbitLen = p.orbit.last - p.orbit.first
        val cut = min(1_400L, max(600L, orbitLen * 30 / 100))
        shots += Shot(Kind.AROUND, centred(p.orbit, cut, 0.10), index = 0)
        if (cam == null) shots += Shot(Kind.AROUND, centred(p.orbit, cut, 0.85), index = 1)
        val rimShot = if (cam == null) 2_600L else 2_000L
        shots += Shot(Kind.RIM, piece(p.rim, rimShot, peak - 800))
        p.under?.let { shots += Shot(Kind.UNDER, centred(it, 1_500)) }

        // 4. the seal lands on the pizza, on moving footage from the middle of the walk around
        if (seal) shots += Shot(Kind.STAMP, centred(p.orbit, STAMP_MS, 0.5))

        // 5. the ending: on the slice if there is one, else on the pizza from above
        val slice = p.slice
        if (slice != null) {
            val r = piece(slice, 4_000, slice.last - 4_000)
            shots += Shot(Kind.SLICE, r, endingFromMs = max(0L, r.last - r.first - ENDING_MS))
        } else {
            shots += Shot(Kind.ENDING, centred(p.top, ENDING_MS), endingFromMs = 0L)
        }
        return shots
    }

    /** Where the loudest crunch is in [samples] (mono, [rate] per second) that start at [fromMs]. */
    fun loudest(samples: FloatArray, rate: Int, fromMs: Long): Long? {
        if (samples.isEmpty()) return null
        val win = rate / 20 // 50 ms
        var best = -1.0
        var bestAt = -1
        var i = 0
        while (i + win <= samples.size) {
            var e = 0.0
            for (k in i until i + win) e += samples[k] * samples[k]
            if (e > best) { best = e; bestAt = i }
            i += win / 2
        }
        if (bestAt < 0 || best <= 1e-6) return null
        return fromMs + (bestAt + win / 2) * 1000L / rate
    }
}
