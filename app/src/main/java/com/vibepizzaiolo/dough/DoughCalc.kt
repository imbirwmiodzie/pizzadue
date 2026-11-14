package com.vibepizzaiolo.dough

import java.util.Locale
import kotlin.math.roundToLong

data class DoughResult(val flour: Double, val water: Double, val salt: Double, val yeast: Double) {
    val total: Double get() = flour + water + salt + yeast
}

/** Baker's percentages: water, salt and yeast are given as % of flour. */
object DoughCalc {
    fun calc(balls: Int, ballWeightG: Double, hydrationPct: Double, saltPct: Double, yeastPct: Double): DoughResult {
        val total = balls.coerceAtLeast(0) * ballWeightG.coerceAtLeast(0.0)
        val flour = total / (1 + hydrationPct / 100 + saltPct / 100 + yeastPct / 100)
        return DoughResult(
            flour = flour,
            water = flour * hydrationPct / 100,
            salt = flour * saltPct / 100,
            yeast = flour * yeastPct / 100,
        )
    }

    /** Whole grams from 50 g up, one decimal below that (salt and yeast need it). */
    fun formatGrams(g: Double): String =
        if (g >= 50) g.roundToLong().toString() else String.format(Locale.US, "%.1f", g)
}
