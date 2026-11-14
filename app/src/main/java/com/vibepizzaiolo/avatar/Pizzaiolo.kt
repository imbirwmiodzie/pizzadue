package com.vibepizzaiolo.avatar

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

/**
 * The cartoon pizzaiolo: chef's hat, white jacket, red neckerchief, a peel with a pizza in one
 * hand, waving with the other. With [face] (the user's cartoon selfie) it is *you*; without it,
 * a smiling face with a moustache.
 */
object Pizzaiolo {
    private const val OUTLINE = 0xFF2A1E16.toInt()
    private const val SKIN = 0xFFF2C29B.toInt()
    private const val CHEEK = 0x55E86A5A
    private const val WHITE = 0xFFFBF8F2.toInt()
    private const val SHADE = 0xFFE3DED3.toInt()
    private const val RED = 0xFFCE2B37.toInt()
    private const val WOOD = 0xFFB9824B.toInt()
    private const val CRUST = 0xFFE2B06E.toInt()
    private const val SAUCE = 0xFFC6372A.toInt()
    private const val CHEESE = 0xFFFFF4DC.toInt()
    private const val BASIL = 0xFF2F8F3A.toInt()
    private const val MOUSTACHE = 0xFF4A2C1A.toInt()

    /**
     * Draws the figure with its feet at ([cx], [bottom]) and [size] tall.
     * [t] is time in seconds: the waving arm and the peel move with it.
     */
    fun draw(c: Canvas, cx: Float, bottom: Float, size: Float, t: Float, face: Bitmap?) {
        val s = size
        val top = bottom - s
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = OUTLINE; strokeWidth = s * 0.012f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        fun shape(p: Path, color: Int) { c.drawPath(p, fill(color)); c.drawPath(p, line) }

        val shoulderY = top + s * 0.56f
        val headCy = top + s * 0.40f
        val headR = s * 0.165f

        // waving arm (behind the body), from the figure's left shoulder
        val wave = (-150f + 22f * sin(t * 2 * PI.toFloat() * 1.6f))
        arm(c, cx - s * 0.25f, shoulderY + s * 0.03f, s, wave, line, ::fill)

        // jacket
        val body = Path().apply {
            moveTo(cx - s * 0.27f, shoulderY)
            quadTo(cx, shoulderY - s * 0.05f, cx + s * 0.27f, shoulderY)
            lineTo(cx + s * 0.31f, bottom)
            lineTo(cx - s * 0.31f, bottom)
            close()
        }
        shape(body, WHITE)
        c.drawRect(cx - s * 0.29f, bottom - s * 0.06f, cx + s * 0.29f, bottom - s * 0.001f, fill(SHADE))
        // double row of buttons
        for (i in 0 until 3) {
            val y = shoulderY + s * (0.12f + i * 0.09f)
            c.drawCircle(cx - s * 0.06f, y, s * 0.014f, fill(OUTLINE))
            c.drawCircle(cx + s * 0.06f, y, s * 0.014f, fill(OUTLINE))
        }
        // neckerchief
        val scarf = Path().apply {
            moveTo(cx - s * 0.12f, shoulderY - s * 0.02f)
            lineTo(cx + s * 0.12f, shoulderY - s * 0.02f)
            lineTo(cx, shoulderY + s * 0.1f)
            close()
        }
        shape(scarf, RED)

        // arm with the peel and a pizza, the figure's right
        val bob = sin(t * 2 * PI.toFloat() * 0.8f) * s * 0.012f
        peel(c, cx + s * 0.36f, shoulderY + s * 0.2f + bob, s, line, ::fill)

        // head
        c.drawCircle(cx, headCy + s * 0.13f, s * 0.06f, fill(SKIN)) // neck
        if (face != null) {
            val m = Matrix().apply {
                setRectToRect(RectF(0f, 0f, face.width.toFloat(), face.height.toFloat()),
                    RectF(cx - headR, headCy - headR, cx + headR, headCy + headR), Matrix.ScaleToFit.FILL)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(face, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            }
            c.drawCircle(cx, headCy, headR, p)
            c.drawCircle(cx, headCy, headR, line)
        } else {
            c.drawCircle(cx, headCy, headR, fill(SKIN))
            c.drawCircle(cx, headCy, headR, line)
            // happy closed eyes, cheeks, smile, moustache
            val eye = Paint(line).apply { strokeWidth = s * 0.014f }
            c.drawArc(RectF(cx - s * 0.095f, headCy - s * 0.05f, cx - s * 0.035f, headCy + s * 0.0f), 200f, 140f, false, eye)
            c.drawArc(RectF(cx + s * 0.035f, headCy - s * 0.05f, cx + s * 0.095f, headCy + s * 0.0f), 200f, 140f, false, eye)
            c.drawCircle(cx - s * 0.1f, headCy + s * 0.04f, s * 0.03f, fill(CHEEK))
            c.drawCircle(cx + s * 0.1f, headCy + s * 0.04f, s * 0.03f, fill(CHEEK))
            c.drawArc(RectF(cx - s * 0.06f, headCy + s * 0.02f, cx + s * 0.06f, headCy + s * 0.11f), 20f, 140f, false, eye)
            val tache = Path().apply {
                moveTo(cx, headCy + s * 0.035f)
                cubicTo(cx - s * 0.04f, headCy + s * 0.0f, cx - s * 0.1f, headCy + s * 0.03f, cx - s * 0.11f, headCy + s * 0.0f)
                cubicTo(cx - s * 0.1f, headCy + s * 0.07f, cx - s * 0.03f, headCy + s * 0.07f, cx, headCy + s * 0.05f)
                cubicTo(cx + s * 0.03f, headCy + s * 0.07f, cx + s * 0.1f, headCy + s * 0.07f, cx + s * 0.11f, headCy + s * 0.0f)
                cubicTo(cx + s * 0.1f, headCy + s * 0.03f, cx + s * 0.04f, headCy + s * 0.0f, cx, headCy + s * 0.035f)
                close()
            }
            c.drawPath(tache, fill(MOUSTACHE))
        }

        // chef's hat: band and three puffs
        val bandTop = headCy - headR * 0.95f
        val puffs = Path().apply {
            addCircle(cx - s * 0.085f, bandTop - s * 0.07f, s * 0.085f, Path.Direction.CW)
            addCircle(cx + s * 0.085f, bandTop - s * 0.07f, s * 0.085f, Path.Direction.CW)
            addCircle(cx, bandTop - s * 0.12f, s * 0.1f, Path.Direction.CW)
        }
        c.drawPath(puffs, fill(WHITE))
        c.drawPath(puffs, line)
        val band = RectF(cx - s * 0.13f, bandTop - s * 0.035f, cx + s * 0.13f, bandTop + s * 0.035f)
        c.drawRoundRect(band, s * 0.02f, s * 0.02f, fill(WHITE))
        c.drawRoundRect(band, s * 0.02f, s * 0.02f, line)
    }

    private fun arm(c: Canvas, x: Float, y: Float, s: Float, angle: Float, line: Paint, fill: (Int) -> Paint) {
        c.save()
        c.rotate(angle, x, y)
        val r = RectF(x - s * 0.045f, y, x + s * 0.045f, y + s * 0.26f)
        c.drawRoundRect(r, s * 0.045f, s * 0.045f, fill(WHITE))
        c.drawRoundRect(r, s * 0.045f, s * 0.045f, line)
        c.drawCircle(x, y + s * 0.28f, s * 0.045f, fill(SKIN))
        c.drawCircle(x, y + s * 0.28f, s * 0.045f, line)
        c.restore()
    }

    private fun peel(c: Canvas, x: Float, y: Float, s: Float, line: Paint, fill: (Int) -> Paint) {
        // handle
        c.save()
        c.rotate(-35f, x, y)
        val handle = RectF(x - s * 0.018f, y - s * 0.05f, x + s * 0.018f, y + s * 0.42f)
        c.drawRoundRect(handle, s * 0.018f, s * 0.018f, fill(WOOD))
        c.drawRoundRect(handle, s * 0.018f, s * 0.018f, line)
        c.restore()
        // blade with the pizza, up and to the side
        val bx = x + s * 0.12f
        val by = y - s * 0.17f
        c.drawOval(RectF(bx - s * 0.17f, by - s * 0.07f, bx + s * 0.17f, by + s * 0.07f), fill(WOOD))
        c.drawOval(RectF(bx - s * 0.17f, by - s * 0.07f, bx + s * 0.17f, by + s * 0.07f), line)
        c.drawOval(RectF(bx - s * 0.14f, by - s * 0.055f, bx + s * 0.14f, by + s * 0.045f), fill(CRUST))
        c.drawOval(RectF(bx - s * 0.115f, by - s * 0.04f, bx + s * 0.115f, by + s * 0.03f), fill(SAUCE))
        for ((dx, dy) in listOf(-0.05f to -0.01f, 0.04f to 0.0f, -0.01f to 0.015f, 0.07f to -0.02f)) {
            c.drawOval(RectF(bx + s * (dx - 0.022f), by + s * (dy - 0.012f), bx + s * (dx + 0.022f), by + s * (dy + 0.012f)), fill(CHEESE))
        }
        c.drawOval(RectF(bx + s * 0.0f, by - s * 0.03f, bx + s * 0.035f, by - s * 0.015f), fill(BASIL))
        // hand on the handle
        c.drawCircle(x, y, s * 0.045f, fill(SKIN))
        c.drawCircle(x, y, s * 0.045f, line)
    }
}
