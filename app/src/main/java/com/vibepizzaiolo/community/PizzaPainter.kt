package com.vibepizzaiolo.community

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the sample pizzas, so the gallery has something to show before any server or any
 * real photo exists. Each type gets its sauce and toppings; the seed moves them around.
 */
object PizzaPainter {
    private const val TAU = (Math.PI * 2).toFloat()

    fun paint(type: PizzaType, seed: Int, size: Int = 720, white: Boolean = type.base == Base.BIANCA): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed * 31 + type.ordinal)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.44f

        // the board it sits on
        c.drawColor(Color.rgb(46, 36, 30))
        p.color = Color.rgb(58, 45, 36)
        repeat(7) { i -> c.drawRect(0f, size * (i / 7f) + 2f, size.toFloat(), size * ((i + 1) / 7f) - 2f, p) }

        // cornicione, a little uneven, with leopard spots
        p.color = Color.rgb(222, 170, 104)
        repeat(40) { i ->
            val a = i / 40f * TAU
            val rr = r * (0.96f + rnd.nextFloat() * 0.05f)
            c.drawCircle(cx + cos(a) * (rr - r * 0.12f), cy + sin(a) * (rr - r * 0.12f), r * 0.14f, p)
        }
        c.drawCircle(cx, cy, r * 0.9f, p)
        p.color = Color.rgb(96, 58, 30)
        repeat(26) {
            val a = rnd.nextFloat() * TAU
            c.drawCircle(cx + cos(a) * r * 0.93f, cy + sin(a) * r * 0.93f, r * (0.012f + rnd.nextFloat() * 0.022f), p)
        }

        // sauce
        val inner = r * 0.82f
        p.color = if (white) Color.rgb(246, 236, 212) else Color.rgb(206, 52, 34)
        c.drawCircle(cx, cy, inner, p)
        if (!white) {
            p.color = Color.rgb(226, 74, 44)
            repeat(14) { c.drawCircle(cx + rnd.signed() * inner * 0.7f, cy + rnd.signed() * inner * 0.7f, inner * 0.09f, p) }
        }

        fun scatter(n: Int, reach: Float = 0.74f, draw: (x: Float, y: Float) -> Unit) = repeat(n) {
            val a = rnd.nextFloat() * TAU
            val d = kotlin.math.sqrt(rnd.nextFloat()) * inner * reach
            draw(cx + cos(a) * d, cy + sin(a) * d)
        }
        fun mozzarella(n: Int) = scatter(n) { x, y ->
            p.color = Color.rgb(250, 246, 236)
            c.drawOval(RectF(x - inner * 0.13f, y - inner * 0.10f, x + inner * 0.13f, y + inner * 0.10f), p)
        }
        fun basil(n: Int) = scatter(n) { x, y ->
            p.color = Color.rgb(52, 142, 70)
            c.save(); c.rotate(rnd.nextFloat() * 360f, x, y)
            c.drawOval(RectF(x - inner * 0.09f, y - inner * 0.045f, x + inner * 0.09f, y + inner * 0.045f), p)
            c.restore()
        }
        fun salami(n: Int) = scatter(n) { x, y ->
            p.color = Color.rgb(140, 34, 30); c.drawCircle(x, y, inner * 0.115f, p)
            p.color = Color.rgb(176, 58, 46); c.drawCircle(x - inner * 0.03f, y - inner * 0.02f, inner * 0.03f, p)
        }
        fun olives(n: Int) = scatter(n) { x, y ->
            p.color = Color.rgb(34, 30, 34); c.drawCircle(x, y, inner * 0.05f, p)
            p.color = Color.rgb(206, 52, 34).takeIf { !white } ?: Color.rgb(246, 236, 212); c.drawCircle(x, y, inner * 0.02f, p)
        }
        fun mushrooms(n: Int) = scatter(n) { x, y ->
            p.color = Color.rgb(112, 92, 76)
            c.drawOval(RectF(x - inner * 0.08f, y - inner * 0.05f, x + inner * 0.08f, y + inner * 0.05f), p)
            p.color = Color.rgb(84, 68, 58); c.drawRect(x - inner * 0.015f, y, x + inner * 0.015f, y + inner * 0.07f, p)
        }
        fun ham(n: Int) = scatter(n) { x, y ->
            p.color = Color.rgb(232, 142, 138)
            c.save(); c.rotate(rnd.nextFloat() * 360f, x, y)
            c.drawRoundRect(RectF(x - inner * 0.14f, y - inner * 0.06f, x + inner * 0.14f, y + inner * 0.06f), inner * 0.04f, inner * 0.04f, p)
            c.restore()
        }

        when (type) {
            PizzaType.MARGHERITA -> { mozzarella(9); basil(6) }
            PizzaType.MARINARA -> {
                p.color = Color.rgb(248, 240, 214); scatter(9) { x, y -> c.drawCircle(x, y, inner * 0.022f, p) } // garlic
                p.color = Color.rgb(64, 110, 60); scatter(40) { x, y -> c.drawCircle(x, y, inner * 0.009f, p) } // oregano
            }
            PizzaType.DIAVOLA -> { mozzarella(5); salami(11) }
            PizzaType.CAPRICCIOSA -> { mozzarella(5); ham(4); mushrooms(6); olives(7); basil(4) }
            PizzaType.PROSCIUTTO_FUNGHI -> { mozzarella(6); ham(6); mushrooms(8) }
            PizzaType.NAPOLI -> {
                mozzarella(5); olives(6)
                p.color = Color.rgb(120, 92, 74)
                scatter(6) { x, y -> c.drawRoundRect(RectF(x - inner * 0.12f, y - inner * 0.015f, x + inner * 0.12f, y + inner * 0.015f), 4f, 4f, p) } // anchovies
            }
            PizzaType.ORTOLANA -> {
                mozzarella(4); basil(10)
                p.color = Color.rgb(86, 160, 76); scatter(9) { x, y -> c.drawCircle(x, y, inner * 0.08f, p) } // courgette
                p.color = Color.rgb(240, 196, 60); scatter(6) { x, y -> c.drawRect(x - inner * 0.07f, y - inner * 0.02f, x + inner * 0.07f, y + inner * 0.02f, p) } // pepper
            }
            PizzaType.QUATTRO_FORMAGGI -> {
                p.color = Color.rgb(244, 208, 96); scatter(10) { x, y -> c.drawCircle(x, y, inner * 0.12f, p) }
                p.color = Color.rgb(252, 248, 238); scatter(8) { x, y -> c.drawCircle(x, y, inner * 0.10f, p) }
                p.color = Color.rgb(170, 190, 196); scatter(7) { x, y -> c.drawCircle(x, y, inner * 0.035f, p) } // gorgonzola
            }
            PizzaType.BIANCA -> {
                mozzarella(7)
                p.color = Color.rgb(60, 120, 64); scatter(14) { x, y -> c.drawRect(x - inner * 0.035f, y - inner * 0.006f, x + inner * 0.035f, y + inner * 0.006f, p) } // rosemary
            }
            PizzaType.OTHER -> { mozzarella(5); olives(4); basil(3); salami(3) }
        }
        return bmp
    }

    private fun Random.signed() = nextFloat() * 2f - 1f
}
