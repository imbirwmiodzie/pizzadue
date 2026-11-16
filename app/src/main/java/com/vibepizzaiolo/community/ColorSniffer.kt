package com.vibepizzaiolo.community

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A rough guess at the pizza from its colours, made on the phone when no server can look at the photo.
 * It measures how much of the middle of the picture is tomato red, cheese white, basil green,
 * salami dark red and dark toppings, and scores each type against that. It tells red from white
 * well; among red pizzas it is only a first proposal, which is why the user always confirms.
 */
object ColorSniffer {
    /** Shares of the pizza's area, each 0–1. */
    data class Palette(val red: Float, val white: Float, val green: Float, val salami: Float, val dark: Float, val yellow: Float)

    /** [pixels] are ARGB, row by row, [width] × [height]. */
    fun sniff(pixels: IntArray, width: Int, height: Int): Sniff {
        val p = palette(pixels, width, height)
        val scores = score(p)
        val best = scores.entries.sortedByDescending { it.value }.take(3)
        val total = best.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val base = when {
            p.red + p.salami > 0.22f -> Base.ROSSA
            p.white + p.yellow > 0.35f && p.red < 0.08f -> Base.BIANCA
            else -> null
        }
        return Sniff(
            suggestions = best.map { Suggestion(it.key, (it.value / total * 100).roundToInt().coerceIn(1, 99)) },
            base = base,
            source = "colours",
        )
    }

    fun palette(pixels: IntArray, width: Int, height: Int): Palette {
        // only the disc in the middle: the pizza, not the table around it
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) * 0.40f
        val step = max(1, min(width, height) / 160)
        var n = 0; var red = 0; var white = 0; var green = 0; var salami = 0; var dark = 0; var yellow = 0
        val hsv = FloatArray(3)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r * r) {
                    toHsv(pixels[y * width + x], hsv)
                    val (h, s, v) = hsv
                    n++
                    when {
                        v < 0.22f -> dark++
                        s < 0.28f && v > 0.72f -> white++
                        (h < 16f || h > 345f) && s > 0.45f && v < 0.62f -> salami++
                        (h < 22f || h > 345f) && s > 0.45f -> red++
                        h in 70f..165f && s > 0.25f -> green++
                        h in 38f..62f && s > 0.3f && v > 0.6f -> yellow++
                        s < 0.45f && v < 0.45f -> dark++
                    }
                }
                x += step
            }
            y += step
        }
        val t = n.coerceAtLeast(1).toFloat()
        return Palette(red / t, white / t, green / t, salami / t, dark / t, yellow / t)
    }

    fun score(p: Palette): Map<PizzaType, Float> {
        val tomato = p.red + p.salami
        val cheese = p.white + p.yellow
        fun near(value: Float, target: Float, width: Float) = (1f - kotlin.math.abs(value - target) / width).coerceAtLeast(0f)
        return mapOf(
            PizzaType.MARGHERITA to tomato.coerceAtMost(0.5f) * 1.2f * near(p.white, 0.25f, 0.3f) + min(p.green, 0.08f) * 3f - p.salami - p.dark * 0.8f,
            PizzaType.MARINARA to (p.red * 1.3f - cheese * 2.2f - p.salami - p.dark * 0.5f),
            PizzaType.DIAVOLA to (min(p.salami, 0.3f) * 3.2f + min(p.red, 0.3f) * 0.6f - p.green * 1.5f),
            PizzaType.CAPRICCIOSA to (min(p.dark, 0.2f) * 2.2f + min(p.green, 0.1f) * 1.5f + min(tomato, 0.3f) * 0.7f) * near(p.salami, 0.05f, 0.25f),
            PizzaType.PROSCIUTTO_FUNGHI to (min(p.dark, 0.15f) * 1.8f + min(tomato, 0.25f) * 0.8f + min(p.white, 0.2f) * 0.5f - p.green * 2f - p.salami),
            PizzaType.NAPOLI to (min(p.dark, 0.08f) * 2.5f + min(p.red, 0.45f) * 0.9f - p.green * 2f - p.salami - max(p.dark - 0.1f, 0f) * 4f),
            PizzaType.ORTOLANA to (min(p.green, 0.35f) * 2.6f + min(tomato, 0.2f) * 0.4f - p.salami),
            PizzaType.QUATTRO_FORMAGGI to (min(cheese, 0.7f) * 1.1f + p.yellow * 0.8f - tomato * 2.5f - p.green),
            PizzaType.BIANCA to (min(p.white, 0.7f) * 1.0f + min(p.green, 0.1f) - tomato * 2.5f - p.yellow * 0.5f),
            PizzaType.OTHER to 0.08f,
        ).mapValues { it.value.coerceAtLeast(0.01f) }
    }

    private fun toHsv(argb: Int, out: FloatArray) {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (mx == 0f) 0f else d / mx
        out[2] = mx
    }
}
