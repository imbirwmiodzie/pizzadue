package com.vibepizzaiolo

import com.vibepizzaiolo.dough.DoughState
import com.vibepizzaiolo.dough.FermentPlan
import com.vibepizzaiolo.dough.FermentPresets
import com.vibepizzaiolo.dough.Fermentation
import com.vibepizzaiolo.dough.Stage
import com.vibepizzaiolo.dough.YeastType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fermentation model: shapes and rules of thumb it was tuned to, not exact numbers. */
class FermentationTest {
    private fun room(hours: Double, t: Double = 22.0, w: Int = 300) =
        FermentPlan(w = w, doughTempC = t, stages = listOf(Stage(hours, t)), autoYeast = true)

    private fun fridge(hours: Double, w: Int = 320) =
        FermentPlan(w = w, stages = listOf(Stage(1.0, 22.0), Stage(hours, 4.0), Stage(2.0, 22.0)), autoYeast = true)

    @Test fun yeastAlmostStopsInTheFridgeAndPeaksAroundThirtyThree() {
        val opt = Fermentation.ctmi(Fermentation.T_OPT)
        assertEquals(1.0, opt, 1e-9)
        assertTrue(Fermentation.ctmi(3.0) < 0.08)
        assertTrue(Fermentation.ctmi(30.0) > 0.95)
        assertTrue(Fermentation.ctmi(35.0) > 0.95)
        assertTrue("steep fall above 40 °C", Fermentation.ctmi(43.0) < 0.45)
        assertEquals(0.0, Fermentation.ctmi(46.0), 0.0)
        assertEquals(0.0, Fermentation.ctmi(-3.0), 0.0)
    }

    @Test fun everyPresetIsReadyAtTheEndOfItsSchedule() {
        for (p in FermentPresets.all) {
            val r = Fermentation.simulate(p.plan)
            assertEquals(p.name, DoughState.READY, r.end.state)
            assertNull(p.name, r.overFrom)
        }
    }

    @Test fun theYeastWorkedOutBackwardsLandsOnTheTarget() {
        for (plan in listOf(room(8.0), room(24.0, w = 380), fridge(48.0), room(2.0, 28.0))) {
            val r = Fermentation.simulate(plan)
            assertEquals(Fermentation.TARGET_X, r.end.x, 0.02)
        }
    }

    @Test fun yeastAmountsMatchRulesOfThumb() {
        fun instant(p: FermentPlan) = Fermentation.simulate(p).instantPct
        assertTrue("8 h at 22 °C", instant(room(8.0)) in 0.12..0.3)
        assertTrue("24 h at 22 °C", instant(room(24.0)) in 0.015..0.06)
        assertTrue("48 h in the fridge", instant(fridge(48.0)) in 0.15..0.4)
        assertTrue("72 h in the fridge", instant(fridge(72.0)) in 0.1..0.3)
        assertTrue("2 h somewhere warm", instant(room(2.0, 28.0)) in 0.8..2.0)
    }

    @Test fun freshIsThreeTimesInstant() {
        val inst = Fermentation.solveYeast(room(8.0)).pct
        val fresh = Fermentation.solveYeast(room(8.0).copy(yeastType = YeastType.FRESH)).pct
        val active = Fermentation.solveYeast(room(8.0).copy(yeastType = YeastType.ACTIVE)).pct
        assertEquals(3 * inst, fresh, 1e-9)
        assertTrue(active > 1.25 * inst - 1e-9 && active < 1.5 * inst + 1e-9)
    }

    @Test fun warmerMoreYeastLessSaltFermentFaster() {
        val base = FermentPlan(stages = listOf(Stage(6.0, 22.0)), doughTempC = 22.0, yeastPct = 0.2)
        val x = Fermentation.simulate(base).end.x
        assertTrue(Fermentation.simulate(base.copy(stages = listOf(Stage(6.0, 28.0)))).end.x > x)
        assertTrue(Fermentation.simulate(base.copy(stages = listOf(Stage(6.0, 4.0)))).end.x < x / 3)
        assertTrue(Fermentation.simulate(base.copy(yeastPct = 0.4)).end.x > x)
        assertTrue(Fermentation.simulate(base.copy(salt = 1.0)).end.x > x)
        assertTrue(Fermentation.simulate(base.copy(hydration = 75.0)).end.x > x)
        assertTrue("a little sugar helps", Fermentation.simulate(base.copy(sugar = 2.0)).end.x > x)
        assertTrue("a lot of sugar slows", Fermentation.simulate(base.copy(sugar = 10.0)).end.x < x)
    }

    @Test fun aDoughPutInTheFridgeCoolsDownSlowly() {
        val r = Fermentation.simulate(FermentPlan(doughTempC = 23.0, stages = listOf(Stage(12.0, 4.0))))
        assertTrue(r.at(1.0).doughC > 12)
        assertTrue(r.at(8.0).doughC < 5)
    }

    @Test fun riseIsSigmoidAndPhFalls() {
        val r = Fermentation.simulate(FermentPlan(stages = listOf(Stage(12.0, 22.0)), doughTempC = 22.0, yeastPct = 0.2))
        val rises = r.points.map { it.rise }
        // slow start, fast middle
        assertTrue(rises[r.points.size / 6] < 25)
        assertTrue(r.points.zipWithNext().all { (a, b) -> b.ph <= a.ph + 1e-9 })
        assertTrue(r.points.first().ph in 5.8..6.0)
        assertTrue(r.end.ph in 5.3..5.75)
    }

    @Test fun vinegarLowersThePhAtOnce() {
        val plain = Fermentation.simulate(FermentPlan(stages = listOf(Stage(1.0, 22.0))))
        val sour = Fermentation.simulate(FermentPlan(vinegarTsp = 2.0, stages = listOf(Stage(1.0, 22.0))))
        assertTrue(plain.points.first().ph - sour.points.first().ph > 0.25)
    }

    @Test fun strongerFlourLastsLonger() {
        fun lasts(w: Int, t: Double): Double {
            val p = FermentPlan(w = w, yeastPct = 0.01, doughTempC = t, stages = listOf(Stage(150.0, t)))
            return Fermentation.simulate(p).points.first { it.gluten < Fermentation.READY_GLUTEN }.h
        }
        assertTrue("W 250 at 22 °C: about 8–12 h", lasts(250, 22.0) in 8.0..12.5)
        assertTrue("W 300 in the fridge: 48 h or more", lasts(300, 4.0) > 45)
        assertTrue(lasts(350, 4.0) > lasts(300, 4.0))
    }

    @Test fun tooLongAndWarmIsOverFermented() {
        val r = Fermentation.simulate(FermentPlan(w = 220, yeastPct = 1.0, stages = listOf(Stage(24.0, 24.0))))
        assertEquals(DoughState.OVER, r.end.state)
        assertNotNull(r.overFrom)
        assertNotNull("it was ready at some point before", r.readyFrom)
        assertTrue(r.readyFrom!! < r.overFrom!!)
    }

    @Test fun theWeakestFlourThatHolds() {
        assertTrue(Fermentation.minFlourW(fridge(48.0))!! in 290..340)
        assertTrue(Fermentation.minFlourW(room(8.0))!! in 220..280)
        assertNull("nothing survives a week at 25 °C", Fermentation.minFlourW(room(160.0, 25.0)))
    }

    @Test fun hotDoughKillsTheYeast() {
        // the ball warms with a delay: an hour at 60 °C only gets it to about 45 °C, three hours kill the yeast
        val r = Fermentation.simulate(FermentPlan(doughTempC = 30.0, stages = listOf(Stage(3.0, 60.0), Stage(4.0, 22.0))))
        assertTrue(r.yeastAlive < 0.01)
    }

    @Test fun bakingLaterStretchesTheLongestStage() {
        val p = FermentPlan(stages = listOf(Stage(2.0, 22.0), Stage(24.0, 4.0), Stage(2.0, 22.0)))
        val later = Fermentation.fitTo(p, 50.0)!!
        assertEquals(listOf(2.0, 46.0, 2.0), later.stages.map { it.hours })
        assertNull("the other stages alone take 4 h", Fermentation.fitTo(p, 3.0))
    }

    @Test fun gramsForTheBatch() {
        val i = Fermentation.ingredients(FermentPlan(flourG = 500, hydration = 65.0, salt = 2.8, yeastPct = 0.2, vinegarTsp = 1.0))
        assertEquals(325.0, i.water, 1e-9)
        assertEquals(14.0, i.salt, 1e-9)
        assertEquals(1.0, i.yeast, 1e-9)
        assertEquals(5.0, i.vinegarMl, 1e-9)
    }
}
