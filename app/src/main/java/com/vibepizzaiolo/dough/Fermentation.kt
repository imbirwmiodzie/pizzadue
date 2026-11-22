package com.vibepizzaiolo.dough

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.serialization.Serializable

/*
 * An approximate, educational model of a pizza dough's fermentation. Not a laboratory simulation:
 * every curve below is a simple formula with parameters chosen by hand so that well known recipes
 * come out about right (see FermentationTest). What it is built from:
 *
 * - Yeast activity against temperature: the cardinal temperature model with inflection (CTMI,
 *   Rosso et al. 1993) with Tmin −2 °C, Topt 33 °C, Tmax 45 °C. That puts a fridge at 2–4 °C at
 *   3–6 % of the optimum, 22 °C at about 70 %, and drops steeply above 40 °C. Above 45 °C the
 *   yeast also dies: in hours at 48 °C, in minutes at 55–60 °C.
 * - The dough's own temperature follows the room or fridge with a delay (time constant 1.5 h):
 *   a ball put into the fridge keeps fermenting for a while, and one taken out needs time to warm.
 * - Fermentation progress P: hours at 22 °C, i.e. the integral of activity(T)/activity(22 °C),
 *   times the living yeast and the effects of salt, sugar, hydration, oil and vinegar.
 *   The dough has doubled when P reaches D(Y) = 2.8 h · Y^−0.6, Y being instant yeast in % of flour
 *   (fresh = 3 × instant, active dry ≈ 1.33 × instant). The exponent below 1 stands for the yeast
 *   multiplying during long fermentations: half the yeast does not take twice as long.
 *   Calibrated to rules of thumb (≈ 0.2 % instant: 8 h at 22 °C; ≈ 0.25 %: 48 h in the fridge;
 *   ≈ 1.2 %: 2 h somewhere warm), not copied from the TXCraig1 / PizzaBlab table.
 * - Rise: a Hill curve of x = P/D, so x = 1 is +100 %, with a ceiling set by the flour's W and the
 *   hydration. Once the gluten gives up, the dough cannot hold its gas and sinks.
 * - pH: from about 5.95, lowered by vinegar, by the yeast's CO₂ and acids (with x) and by the
 *   flour's bacteria and enzymes (with time and temperature).
 * - Gluten strength (0–100, a made-up scale): develops in the first hours after mixing, then is
 *   worn down by proteases, faster when warm (Q10 2), sour, very wet or unsalted. How much it can
 *   take grows with W: W 250 lasts ≈ 10 h at 22 °C, W 300–350 ≈ 48–72 h in the fridge.
 */

enum class YeastType(val label: String, val perInstant: Double) {
    FRESH("Świeże", 3.0),
    INSTANT("Suche instant", 1.0),
    ACTIVE("Suche aktywne", 4.0 / 3.0),
}

@Serializable
data class Stage(val hours: Double, val tempC: Double)

@Serializable
data class FermentPlan(
    val flourG: Int = 600,
    val balls: Int = 4,
    /** The flour's W (alveograph strength). */
    val w: Int = 280,
    val hydration: Double = 65.0,
    val salt: Double = 2.8,
    val sugar: Double = 0.0,
    val oil: Double = 0.0,
    /** Teaspoons (5 ml) of 5 % vinegar per 500 g of flour. */
    val vinegarTsp: Double = 0.0,
    val yeastType: YeastType = YeastType.INSTANT,
    /** % of flour, of [yeastType]. */
    val yeastPct: Double = 0.25,
    /** Let the simulator pick the yeast so the dough is ready at the end of the schedule. */
    val autoYeast: Boolean = false,
    /** The dough's temperature when mixing is done. */
    val doughTempC: Double = 23.0,
    val stages: List<Stage> = listOf(Stage(2.0, 22.0), Stage(24.0, 4.0), Stage(2.0, 22.0)),
) {
    val totalHours: Double get() = stages.sumOf { it.hours }
    val instantPct: Double get() = yeastPct / yeastType.perInstant
}

/** Where the dough is: not risen yet, ready to bake, past its best, over-fermented. */
enum class DoughState { UNDER, READY, LATE, OVER }

data class FermentPoint(
    val h: Double,
    val roomC: Double,
    val doughC: Double,
    /** Volume increase, %. */
    val rise: Double,
    val ph: Double,
    /** 0–100, conventional. */
    val gluten: Double,
    /** Fermentation progress: 1 = doubled. */
    val x: Double,
    val state: DoughState,
)

data class FermentResult(
    val points: List<FermentPoint>,
    val readyFrom: Double?,
    val readyTo: Double?,
    val overFrom: Double?,
    /** Instant yeast actually used, % of flour. */
    val instantPct: Double,
    /** 0–1: residual sugars and pH, how well the crust will colour. */
    val browning: Double,
    val yeastAlive: Double,
) {
    val end: FermentPoint get() = points.last()
    fun at(h: Double): FermentPoint = points.minBy { kotlin.math.abs(it.h - h) }
}

data class Ingredients(
    val flour: Double, val water: Double, val salt: Double, val yeast: Double,
    val sugar: Double, val oil: Double, val vinegarMl: Double,
) {
    val total: Double get() = flour + water + salt + yeast + sugar + oil + vinegarMl
}

data class YeastSolution(val pct: Double, val tooMuch: Boolean, val tooLittle: Boolean)

object Fermentation {
    const val T_MIN = -2.0
    const val T_OPT = 33.0
    const val T_MAX = 45.0
    const val T_REF = 22.0
    /** Hours for the dough's temperature to close 63 % of the gap to the room or fridge. */
    const val DOUGH_LAG_H = 1.5
    const val DT_H = 1.0 / 30
    /** Hours to double at 22 °C with 1 % instant yeast, and how that scales with the amount. */
    const val DOUBLE_H_AT_1PCT = 2.8
    const val YEAST_EXPONENT = 0.6

    const val READY_X0 = 0.8
    const val READY_X1 = 1.6
    const val OVER_X = 2.1
    const val READY_GLUTEN = 35.0
    const val COLLAPSE_GLUTEN = 25.0
    /** What the yeast is chosen for: a little past doubling, in the middle of the window. */
    const val TARGET_X = 1.1

    const val MIN_INSTANT = 0.005
    const val MAX_INSTANT = 3.0

    /** CTMI, 0 at and below Tmin and at and above Tmax, 1 at Topt. */
    fun ctmi(t: Double): Double {
        if (t <= T_MIN || t >= T_MAX) return 0.0
        val num = (t - T_MAX) * (t - T_MIN).pow(2)
        val den = (T_OPT - T_MIN) * ((T_OPT - T_MIN) * (t - T_OPT) - (T_OPT - T_MAX) * (T_OPT + T_MIN - 2 * t))
        return (num / den).coerceIn(0.0, 1.0)
    }

    private val ctmiRef = ctmi(T_REF)

    /** Yeast activity relative to 22 °C. */
    fun activity(t: Double): Double = ctmi(t) / ctmiRef

    /** Per hour; nothing below 45 °C, minutes at 55–60 °C. */
    fun deathRate(t: Double): Double = if (t <= 45) 0.0 else 0.05 * exp((t - 45) / 2.2)

    /** Enzymes, bacteria and proteases: Q10 of 2 around 22 °C. */
    fun q10(t: Double): Double = 2.0.pow((min(t, 50.0) - T_REF) / 10)

    fun doublingHours(instantPct: Double): Double = DOUBLE_H_AT_1PCT * instantPct.coerceAtLeast(1e-4).pow(-YEAST_EXPONENT)

    /** How much the recipe speeds fermentation up (>1) or slows it down, against 2.8 % salt and 63 % water. */
    fun recipeFactor(p: FermentPlan): Double {
        val salt = exp(-0.1 * (p.salt - 2.8))
        val sugar = 1 + 0.06 * min(p.sugar, 2.0) - 0.04 * max(0.0, p.sugar - 4)
        val water = 1 + 0.012 * (p.hydration - 63)
        val oil = 1 - 0.01 * p.oil
        val acid = 1 - 0.03 * p.vinegarTsp
        return salt * sugar * water * oil * acid
    }

    /** The flour's endurance in protease-hours at 22 °C. */
    fun endurance(w: Int): Double = 5.0 * exp((w - 180) / 95.0)

    fun peakRise(p: FermentPlan): Double = (130 + 0.55 * (p.w - 180) - 1.0 * (p.hydration - 63)).coerceAtLeast(110.0)

    fun riseOf(x: Double, peak: Double): Double {
        val hn = peak / 100 - 1
        val x2 = x * x
        return peak * x2 / (hn + x2)
    }

    fun vinegarDrop(tsp: Double): Double = 0.6 * (1 - exp(-tsp / 2))

    fun state(x: Double, gluten: Double): DoughState = when {
        x > OVER_X || gluten < COLLAPSE_GLUTEN -> DoughState.OVER
        x < READY_X0 -> DoughState.UNDER
        x <= READY_X1 && gluten >= READY_GLUTEN -> DoughState.READY
        else -> DoughState.LATE
    }

    /** The room temperature [h] hours after mixing. */
    fun roomAt(p: FermentPlan, h: Double): Double {
        var t = 0.0
        for (s in p.stages) {
            t += s.hours
            if (h < t) return s.tempC
        }
        return p.stages.lastOrNull()?.tempC ?: T_REF
    }

    /** Fermentation progress at the end of the schedule, in hours at 22 °C: heat, time, living yeast and recipe, whatever the amount of yeast. */
    fun progress(p: FermentPlan): Double {
        var td = p.doughTempC
        var alive = 1.0
        var prog = 0.0
        val k = recipeFactor(p)
        forEachStep(p) { _, room ->
            td += (room - td) * (1 - exp(-DT_H / DOUGH_LAG_H))
            alive *= exp(-deathRate(td) * DT_H)
            prog += activity(td) * alive * k * DT_H
        }
        return prog
    }

    private inline fun forEachStep(p: FermentPlan, step: (h: Double, room: Double) -> Unit) {
        val n = Math.round(p.totalHours / DT_H).toInt()
        for (i in 1..n) {
            val h = i * DT_H
            step(h, roomAt(p, h - DT_H / 2))
        }
    }

    /** Yeast of the plan's type, % of flour, for the dough to be ready at the end of the schedule. */
    fun solveYeast(p: FermentPlan): YeastSolution {
        val prog = progress(p)
        if (prog <= 1e-6) return YeastSolution(MAX_INSTANT * p.yeastType.perInstant, tooMuch = false, tooLittle = true)
        // TARGET_X = prog / D(Y) with D(Y) = D1 · Y^-a  →  Y = (TARGET_X · D1 / prog)^(1/a)
        val y = (TARGET_X * DOUBLE_H_AT_1PCT / prog).pow(1 / YEAST_EXPONENT)
        val clamped = y.coerceIn(MIN_INSTANT, MAX_INSTANT)
        return YeastSolution(clamped * p.yeastType.perInstant, tooMuch = y < MIN_INSTANT, tooLittle = y > MAX_INSTANT)
    }

    /** The plan with the yeast it will really use. */
    fun effective(p: FermentPlan): FermentPlan = if (p.autoYeast) p.copy(yeastPct = solveYeast(p).pct) else p

    fun simulate(plan: FermentPlan): FermentResult {
        val p = effective(plan)
        val y = p.instantPct.coerceAtLeast(1e-4)
        val dbl = doublingHours(y)
        val k = recipeFactor(p)
        val peak = peakRise(p)
        val endur = endurance(p.w)
        val ph0 = 5.95 - vinegarDrop(p.vinegarTsp)
        val flourScale = (0.75 + 0.25 * (p.w - 180) / 200.0).coerceIn(0.6, 1.05)
        val extras = (1 - 0.04 * p.oil).coerceAtLeast(0.6) * (1 - 0.04 * p.vinegarTsp).coerceAtLeast(0.7)
        val wetWear = (1 + 0.025 * (p.hydration - 63)).coerceAtLeast(0.5)
        val saltWear = exp(-0.08 * (p.salt - 2.8))

        var td = p.doughTempC
        var alive = 1.0
        var prog = 0.0
        var enzymes = 0.0 // protease / amylase / bacteria hours at 22 °C
        var develop = 0.0
        var wear = 0.0
        var ph = ph0
        val pts = ArrayList<FermentPoint>()

        fun point(h: Double, room: Double): FermentPoint {
            val x = prog / dbl
            val dev = 1 - 0.4 * exp(-develop)
            val life = 0.5.pow((wear / endur).pow(2.5))
            val g = (100 * flourScale * dev * life * extras).coerceIn(0.0, 100.0)
            // weak gluten cannot hold the gas: the dough sinks
            val hold = 0.5 + 0.5 * ((g - 15) / 25).coerceIn(0.0, 1.0)
            return FermentPoint(h, room, td, riseOf(x, peak) * hold, ph, g, x, state(x, g))
        }

        pts += point(0.0, p.stages.firstOrNull()?.tempC ?: T_REF)
        forEachStep(p) { h, room ->
            td += (room - td) * (1 - exp(-DT_H / DOUGH_LAG_H))
            alive *= exp(-deathRate(td) * DT_H)
            val act = activity(td)
            prog += act * alive * k * DT_H
            enzymes += q10(td) * DT_H
            develop += (0.3 + 0.7 * min(act, 1.0)) * DT_H
            val x = prog / dbl
            ph = (ph0 - 0.35 * (1 - exp(-x / 1.2)) - 0.45 * (1 - exp(-enzymes / 30))).coerceAtLeast(4.6)
            val sour = 1 + 1.0 * max(0.0, 5.9 - ph)
            wear += q10(td) * sour * wetWear * saltWear * DT_H
            pts += point(h, room)
        }

        val firstReady = pts.indexOfFirst { it.state == DoughState.READY }
        var readyTo: Double? = null
        if (firstReady >= 0) {
            var i = firstReady
            while (i + 1 < pts.size && pts[i + 1].state == DoughState.READY) i++
            readyTo = pts[i].h
        }
        val end = pts.last()
        val sugars = max(0.0, 1 + p.sugar + 0.05 * enzymes - 0.4 * end.x)
        val browning = (0.55 * min(1.0, sugars / 1.8) + 0.45 * ((end.ph - 5.0) / 0.9).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        return FermentResult(
            points = pts,
            readyFrom = pts.getOrNull(firstReady)?.h,
            readyTo = readyTo,
            overFrom = pts.firstOrNull { it.state == DoughState.OVER }?.h,
            instantPct = y,
            browning = browning,
            yeastAlive = alive,
        )
    }

    /** The weakest flour (W, in steps of 10) that still has [READY_GLUTEN] + 5 left at the end; null if none up to 400 does. */
    fun minFlourW(plan: FermentPlan): Int? {
        val p = effective(plan)
        fun ok(w: Int) = simulate(p.copy(w = w, autoYeast = false)).end.gluten >= READY_GLUTEN + 5
        if (!ok(400)) return null
        var lo = 120
        var hi = 400
        while (hi - lo > 10) {
            val mid = (lo + hi) / 2
            if (ok(mid)) hi = mid else lo = mid
        }
        return ((hi + 9) / 10) * 10
    }

    /** Grams for the whole batch; yeast of the plan's type. */
    fun ingredients(plan: FermentPlan): Ingredients {
        val p = effective(plan)
        val f = p.flourG.toDouble()
        return Ingredients(
            flour = f,
            water = f * p.hydration / 100,
            salt = f * p.salt / 100,
            yeast = f * p.yeastPct / 100,
            sugar = f * p.sugar / 100,
            oil = f * p.oil / 100,
            vinegarMl = p.vinegarTsp * 5 * f / 500,
        )
    }

    /**
     * The schedule made [hours] long by stretching or shrinking its longest stage (usually the
     * fridge or the main rise); the others stay as they are. Null if the others alone are longer.
     */
    fun fitTo(p: FermentPlan, hours: Double): FermentPlan? {
        if (p.stages.isEmpty()) return null
        val i = p.stages.indices.maxBy { p.stages[it].hours }
        val rest = p.totalHours - p.stages[i].hours
        val len = hours - rest
        if (len < 0.25) return null
        return p.copy(stages = p.stages.mapIndexed { j, s -> if (j == i) s.copy(hours = len) else s })
    }

}

/** Ready-made schedules and recipes. */
object FermentPresets {
    data class Preset(val name: String, val note: String, val plan: FermentPlan)

    val all = listOf(
        Preset(
            "Neapolitańska tego samego dnia",
            "8 h w 22 °C: 2 h w masie, 6 h w kulkach",
            FermentPlan(
                flourG = 600, balls = 4, w = 260, hydration = 63.0, salt = 2.8,
                yeastType = YeastType.FRESH, yeastPct = 0.6, doughTempC = 23.0,
                stages = listOf(Stage(2.0, 22.0), Stage(6.0, 22.0)),
            ),
        ),
        Preset(
            "48 h w lodówce",
            "1 h w 22 °C → 45 h w 4 °C → 2 h w 22 °C",
            FermentPlan(
                flourG = 600, balls = 4, w = 320, hydration = 65.0, salt = 2.8,
                yeastType = YeastType.INSTANT, yeastPct = 0.25, doughTempC = 22.0,
                stages = listOf(Stage(1.0, 22.0), Stage(45.0, 4.0), Stage(2.0, 22.0)),
            ),
        ),
        Preset(
            "Szybka 2 h",
            "ciepła woda, ciepłe miejsce 28 °C, dużo drożdży",
            FermentPlan(
                flourG = 500, balls = 3, w = 230, hydration = 60.0, salt = 2.2, sugar = 1.0, oil = 2.0,
                yeastType = YeastType.INSTANT, yeastPct = 1.2, doughTempC = 27.0,
                stages = listOf(Stage(2.0, 28.0)),
            ),
        ),
        Preset(
            "Nowojorska",
            "cukier i olej, 1 h → 24 h w lodówce → 2 h",
            FermentPlan(
                flourG = 650, balls = 3, w = 310, hydration = 62.0, salt = 2.0, sugar = 2.0, oil = 3.0,
                yeastType = YeastType.INSTANT, yeastPct = 0.4, doughTempC = 22.0,
                stages = listOf(Stage(1.0, 22.0), Stage(24.0, 4.0), Stage(2.0, 22.0)),
            ),
        ),
    )
}
