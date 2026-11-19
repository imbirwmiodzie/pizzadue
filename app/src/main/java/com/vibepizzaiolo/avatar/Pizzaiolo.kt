package com.vibepizzaiolo.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The pizzaiolo, drawn in pen and ink: brown ink lines put down twice like a quick sketch, hatching
 * for the shadows, flat colour laid on a touch off the line. Chef's toque, double-breasted jacket,
 * knotted red neckerchief, a peel with a pizza in one hand, waving with the other. With a face from
 * [FaceSketch] it is *you*; without one, a smiling face with a moustache.
 */
object Pizzaiolo {
    private const val INK = 0xFF2A1E16.toInt()
    private const val WHITE = 0xFFFBF7EE.toInt()
    private const val SHADE = 0xFFE9E1D2.toInt()
    private const val SKIN = 0xFFF0C7A4.toInt()
    private const val CHEEK = 0x47E86A5A
    private const val RED = 0xFFC8323A.toInt()
    private const val WOOD = 0xFFC08A55.toInt()
    private const val CRUST = 0xFFE3AE68.toInt()
    private const val SAUCE = 0xFFC4402F.toInt()
    private const val CHEESE = 0xFFFFF6E0.toInt()
    private const val BASIL = 0xFF3E8C3F.toInt()
    private const val TACHE = 0xFF4A2C1A.toInt()
    private const val PAPER = 0xFFFBF8F2.toInt()

    /** Head and toque are drawn a little smaller than the body, so the figure reads tall. */
    private const val HEAD_SCALE = 0.88f
    private const val HEAD_Y = 0.36f
    private const val ICON_SIDE = 0.37f

    /**
     * Draws the figure with the bottom of the jacket at ([cx], [bottom]) and [size] tall (the toque
     * reaches a little above). [t] is time in seconds: the waving arm and the peel move with it.
     */
    fun draw(c: Canvas, cx: Float, bottom: Float, size: Float, t: Float, face: Bitmap?) {
        val s = size
        val k = Kit(c, s, 11)
        val top = bottom - s
        val headCy = top + s * HEAD_Y
        val hrx = s * 0.128f
        val hry = s * 0.152f
        val shoulderY = top + s * 0.56f

        // --- waving arm (behind the body): shoulder -> elbow out -> hand up, the forearm swings
        val sw = sin(t * 2 * PI.toFloat() * 1.6f)
        val shX = cx - s * 0.176f; val shY = shoulderY + s * 0.05f
        val elX = cx - s * 0.2746f; val elY = shoulderY
        val fa = rad(-96f + 14f * sw)
        val handX = elX + cos(fa) * s * 0.18f; val handY = elY + sin(fa) * s * 0.18f
        val arm = limb(shX, shY, elX + s * 0.02f, elY + s * 0.06f, elX, elY, s * 0.09f, s * 0.078f)
        val fore = limb(elX, elY, (elX + handX) / 2, (elY + handY) / 2, handX, handY, s * 0.078f, s * 0.068f)
        k.shape(fore.outline, WHITE)
        k.shadow(fore.outline) { x, _ -> (x - (elX + handX) / 2) / (s * 0.03f) + 0.5f }
        k.shape(arm.outline, WHITE)
        k.shadow(arm.outline) { _, y -> (y - elY - s * 0.01f) / (s * 0.03f) }
        k.stroke(cuff(fore, 0f, s * 0.012f), w = 0.8f)
        hand(k, s, handX, handY - s * 0.045f, fa + PI.toFloat() / 2)

        // --- jacket
        val body = Pts().moveTo(cx - s * 0.0704f, shoulderY - s * 0.045f)
            .cubicTo(cx - s * 0.1408f, shoulderY - s * 0.03f, cx - s * 0.1971f, shoulderY - s * 0.01f, cx - s * 0.2182f, shoulderY + s * 0.07f)
            .cubicTo(cx - s * 0.2358f, shoulderY + s * 0.17f, cx - s * 0.2323f, bottom - s * 0.1f, cx - s * 0.2394f, bottom)
            .lineTo(cx + s * 0.2394f, bottom)
            .cubicTo(cx + s * 0.2323f, bottom - s * 0.1f, cx + s * 0.2358f, shoulderY + s * 0.17f, cx + s * 0.2182f, shoulderY + s * 0.07f)
            .cubicTo(cx + s * 0.1971f, shoulderY - s * 0.01f, cx + s * 0.1408f, shoulderY - s * 0.03f, cx + s * 0.0704f, shoulderY - s * 0.045f)
        k.fill(body, WHITE)
        k.shadow(body) { x, y -> (x - (cx + s * 0.1197f)) / (s * 0.02f) + (y - shoulderY) / (s * 0.3f) }
        k.shadow(body, angle = 20f, spacing = 0.009f, alpha = 0.5f) { _, y -> (y - (bottom - s * 0.045f)) / (s * 0.01f) }
        k.stroke(body)
        // the double-breasted front: overlap edge and two rows of buttons
        k.stroke(
            Pts().moveTo(cx + s * 0.0493f, shoulderY + s * 0.03f)
                .cubicTo(cx + s * 0.0704f, shoulderY + s * 0.1f, cx + s * 0.0422f, shoulderY + s * 0.2f, cx + s * 0.0528f, bottom),
            w = 0.8f,
        )
        for (i in 0 until 3) {
            val y = shoulderY + s * (0.1f + i * 0.085f)
            for (bx in floatArrayOf(cx - s * 0.0352f, cx + s * 0.1091f)) k.shape(ellipse(bx, y, s * 0.014f, s * 0.013f), SHADE, w = 0.6f)
        }
        // a couple of folds
        k.stroke(Pts().moveTo(cx - s * 0.1408f, shoulderY + s * 0.12f).quadTo(cx - s * 0.1197f, shoulderY + s * 0.2f, cx - s * 0.1408f, shoulderY + s * 0.27f), w = 0.55f, alpha = 0.8f)
        k.stroke(Pts().moveTo(cx + s * 0.176f, bottom - s * 0.02f).quadTo(cx + s * 0.1549f, bottom - s * 0.08f, cx + s * 0.169f, bottom - s * 0.13f), w = 0.5f, alpha = 0.7f)

        // --- neck and collar
        val chin = headCy + hry * HEAD_SCALE * 0.6f
        val neck = Pts().moveTo(cx - s * 0.05f, chin).lineTo(cx - s * 0.06f, shoulderY - s * 0.01f)
            .lineTo(cx + s * 0.06f, shoulderY - s * 0.01f).lineTo(cx + s * 0.05f, chin)
        k.shape(neck, SKIN)
        k.shadow(neck, angle = 10f, spacing = 0.008f) { _, y -> (headCy + hry * HEAD_SCALE * 0.98f - y) / (s * 0.01f) + 0.6f }
        val collar = Pts().moveTo(cx - s * 0.11f, shoulderY - s * 0.045f).quadTo(cx, shoulderY, cx + s * 0.11f, shoulderY - s * 0.045f)
            .lineTo(cx + s * 0.1f, shoulderY - s * 0.075f).quadTo(cx, shoulderY - s * 0.035f, cx - s * 0.1f, shoulderY - s * 0.075f)
        k.shape(collar, WHITE, w = 0.85f)
        // neckerchief: two tails and the knot
        val tailL = Pts().moveTo(cx - s * 0.02f, shoulderY - s * 0.02f).quadTo(cx - s * 0.06f, shoulderY + s * 0.06f, cx - s * 0.075f, shoulderY + s * 0.115f)
            .lineTo(cx - s * 0.025f, shoulderY + s * 0.1f).quadTo(cx - s * 0.01f, shoulderY + s * 0.04f, cx + s * 0.005f, shoulderY - s * 0.01f)
        val tailR = Pts().moveTo(cx + s * 0.02f, shoulderY - s * 0.02f).quadTo(cx + s * 0.045f, shoulderY + s * 0.05f, cx + s * 0.06f, shoulderY + s * 0.1f)
            .lineTo(cx + s * 0.015f, shoulderY + s * 0.085f).quadTo(cx + s * 0.005f, shoulderY + s * 0.04f, cx - s * 0.005f, shoulderY - s * 0.01f)
        k.shape(tailL, RED, w = 0.85f)
        k.shape(tailR, RED, w = 0.85f)
        k.shadow(tailR, angle = 75f, spacing = 0.01f, alpha = 0.6f) { _, _ -> 1f }
        k.shape(ellipse(cx, shoulderY - s * 0.022f, s * 0.032f, s * 0.026f), RED, w = 0.85f)

        // --- the arm with the peel
        val bob = sin(t * 2 * PI.toFloat() * 0.8f) * s * 0.01f
        val sh2X = cx + s * 0.1901f; val sh2Y = shoulderY + s * 0.06f
        val el2X = cx + s * 0.2464f; val el2Y = shoulderY + s * 0.25f + bob
        val h2X = cx + s * 0.2886f; val h2Y = shoulderY + s * 0.13f + bob
        peel(k, s, h2X, h2Y)
        val arm2 = limb(sh2X, sh2Y, el2X - s * 0.01f, el2Y - s * 0.06f, el2X, el2Y, s * 0.09f, s * 0.078f)
        val fore2 = limb(el2X, el2Y, (el2X + h2X) / 2 + s * 0.01f, (el2Y + h2Y) / 2 + s * 0.01f, h2X - s * 0.012f, h2Y + s * 0.03f, s * 0.078f, s * 0.068f)
        k.shape(arm2.outline, WHITE)
        k.shadow(arm2.outline) { x, _ -> (x - (sh2X + s * 0.06f)) / (s * 0.02f) }
        k.shape(fore2.outline, WHITE)
        k.shadow(fore2.outline) { _, y -> (y - (el2Y - s * 0.04f)) / (s * 0.02f) }
        k.stroke(cuff(fore2, s * 0.008f, 0f), w = 0.8f)
        // fist round the handle
        k.shape(ellipse(h2X, h2Y, s * 0.042f, s * 0.036f, rot = rad(-30f)), SKIN)
        for (i in 0 until 3) {
            val dx = i * s * 0.014f; val dy = i * s * 0.008f
            k.stroke(
                Pts().moveTo(h2X - s * 0.022f + dx, h2Y - s * 0.03f + dy)
                    .quadTo(h2X - s * 0.005f + dx, h2Y - s * 0.012f + dy, h2X - s * 0.012f + dx, h2Y + s * 0.004f + dy),
                w = 0.45f,
            )
        }

        // --- head and toque, scaled about the head's centre, drawn with a kit sized for the
        // scale so their lines keep the body's weight
        c.save()
        c.scale(HEAD_SCALE, HEAD_SCALE, cx, headCy)
        val kh = Kit(c, s / HEAD_SCALE, 41)
        if (face != null) {
            // the face is already cut to a head with its hair, and inked round the edge
            val sc = hrx / FaceSketch.HEAD_RX
            val left = cx - FaceSketch.HEAD_CX * sc
            val topY = headCy - FaceSketch.HEAD_CY * sc
            c.drawBitmap(
                face, null, RectF(left, topY, left + FaceSketch.SIZE * sc, topY + FaceSketch.SIZE * sc),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } else {
            kh.shape(headShape(cx, headCy, hrx, hry), SKIN)
            cartoonFace(kh, c, s, cx, headCy)
        }
        toque(kh, s, cx, headCy - hry * 0.58f)
        c.restore()
    }

    /** Your head in the toque, cut from the drawing, on paper: the icon on the Timer and in the community. */
    fun icon(face: Bitmap?, px: Int): Bitmap {
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(PAPER)
        val s = px / ICON_SIDE
        val headCy = px * 0.62f
        draw(c, px / 2f, headCy - s * HEAD_Y + s, s, 0f, face)
        return out
    }

    // ---------------------------------------------------------------- parts

    private fun toque(k: Kit, s: Float, cx: Float, bandB: Float) {
        val bandT = bandB - s * 0.065f
        val puff = Pts().moveTo(cx - s * 0.135f, bandT + s * 0.005f)
            .cubicTo(cx - s * 0.1462f, bandT - s * 0.06f, cx - s * 0.2021f, bandT - s * 0.1f, cx - s * 0.172f, bandT - s * 0.17f)
            .cubicTo(cx - s * 0.172f, bandT - s * 0.235f, cx - s * 0.12f, bandT - s * 0.25f, cx - s * 0.08f, bandT - s * 0.225f)
            .cubicTo(cx - s * 0.05f, bandT - s * 0.285f, cx + s * 0.05f, bandT - s * 0.29f, cx + s * 0.075f, bandT - s * 0.23f)
            .cubicTo(cx + s * 0.13f, bandT - s * 0.255f, cx + s * 0.1849f, bandT - s * 0.22f, cx + s * 0.172f, bandT - s * 0.15f)
            .cubicTo(cx + s * 0.2021f, bandT - s * 0.095f, cx + s * 0.1462f, bandT - s * 0.05f, cx + s * 0.135f, bandT + s * 0.005f)
        k.fill(puff, WHITE)
        k.shadow(puff, angle = 60f) { x, y -> (x - (cx + s * 0.07f)) / (s * 0.025f) + (y - (bandT - s * 0.2f)) / (s * 0.08f) }
        k.stroke(puff)
        // pleats
        for (p in arrayOf(floatArrayOf(-0.075f, -0.1f, 0.16f), floatArrayOf(-0.02f, -0.025f, 0.2f), floatArrayOf(0.035f, 0.05f, 0.19f), floatArrayOf(0.09f, 0.12f, 0.14f))) {
            val (x0, x1, h) = Triple(p[0], p[1], p[2])
            k.stroke(
                Pts().moveTo(cx + s * x0, bandT - s * 0.005f).quadTo(cx + s * (x0 + x1) / 2 - s * 0.01f, bandT - s * h * 0.5f, cx + s * x1, bandT - s * h),
                w = 0.5f, alpha = 0.75f,
            )
        }
        val band = Pts().moveTo(cx - s * 0.142f, bandB).quadTo(cx, bandB + s * 0.03f, cx + s * 0.142f, bandB)
            .lineTo(cx + s * 0.137f, bandT).quadTo(cx, bandT + s * 0.025f, cx - s * 0.137f, bandT)
        k.shape(band, WHITE)
        k.shadow(band, angle = 70f) { x, _ -> (x - (cx + s * 0.07f)) / (s * 0.02f) }
    }

    /** An open hand, palm out, waving: four round fingers and a thumb. */
    private fun hand(k: Kit, s: Float, x: Float, y: Float, rot: Float) {
        val cr = cos(rot); val sr = sin(rot)
        fun tx(dx: Float, dy: Float) = x + dx * s * cr - dy * s * sr
        fun ty(dx: Float, dy: Float) = y + dx * s * sr + dy * s * cr
        val pts = Pts().moveTo(tx(-0.03f, 0.03f), ty(-0.03f, 0.03f))
        val curves = arrayOf(
            floatArrayOf(-0.042f, 0f, -0.04f, -0.03f, -0.034f, -0.045f),
            floatArrayOf(-0.036f, -0.07f, -0.02f, -0.075f, -0.017f, -0.052f),
            floatArrayOf(-0.016f, -0.08f, 0f, -0.082f, 0f, -0.055f),
            floatArrayOf(0.002f, -0.079f, 0.019f, -0.077f, 0.017f, -0.05f),
            floatArrayOf(0.022f, -0.068f, 0.036f, -0.062f, 0.032f, -0.04f),
            floatArrayOf(0.034f, -0.025f, 0.036f, -0.012f, 0.04f, -0.012f),
            floatArrayOf(0.056f, -0.024f, 0.068f, -0.012f, 0.054f, 0.004f),
            floatArrayOf(0.044f, 0.016f, 0.036f, 0.03f, 0.028f, 0.032f),
        )
        for (p in curves) pts.cubicTo(tx(p[0], p[1]), ty(p[0], p[1]), tx(p[2], p[3]), ty(p[2], p[3]), tx(p[4], p[5]), ty(p[4], p[5]))
        pts.lineTo(tx(-0.03f, 0.03f), ty(-0.03f, 0.03f))
        k.shape(pts, SKIN, w = 0.85f)
    }

    /** The peel, held at ([hx], [hy]), with a margherita on the blade. */
    private fun peel(k: Kit, s: Float, hx: Float, hy: Float) {
        val bx = hx + s * 0.07f
        val by = hy - s * 0.21f
        val ang = atan2(by - hy, bx - hx)
        val ex = hx - cos(ang) * s * 0.07f
        val ey = hy - sin(ang) * s * 0.07f
        val handle = limb(ex, ey, (ex + bx) / 2, (ey + by) / 2, bx - cos(ang) * s * 0.1f, by - sin(ang) * s * 0.1f, s * 0.026f, s * 0.03f)
        k.shape(handle.outline, WOOD, w = 0.8f)
        // blade: a rounded square seen from below
        val blade = Pts().moveTo(bx - s * 0.16f, by + s * 0.02f)
            .cubicTo(bx - s * 0.18f, by - s * 0.06f, bx - s * 0.1f, by - s * 0.085f, bx, by - s * 0.085f)
            .cubicTo(bx + s * 0.1f, by - s * 0.085f, bx + s * 0.18f, by - s * 0.06f, bx + s * 0.16f, by + s * 0.02f)
            .cubicTo(bx + s * 0.14f, by + s * 0.07f, bx + s * 0.06f, by + s * 0.06f, bx + s * 0.02f, by + s * 0.075f)
            .lineTo(bx - s * 0.02f, by + s * 0.075f)
            .cubicTo(bx - s * 0.06f, by + s * 0.06f, bx - s * 0.14f, by + s * 0.07f, bx - s * 0.16f, by + s * 0.02f)
        k.shape(blade, WOOD)
        k.shadow(blade, angle = 15f, spacing = 0.01f) { _, y -> (y - (by + s * 0.03f)) / (s * 0.015f) }
        // crust with a few blisters, sauce, mozzarella, basil
        k.shape(ellipse(bx, by - s * 0.012f, s * 0.135f, s * 0.06f, rot = rad(-3f)), CRUST, w = 0.8f)
        for (p in arrayOf(floatArrayOf(-0.11f, -0.03f), floatArrayOf(-0.04f, -0.062f), floatArrayOf(0.06f, -0.06f), floatArrayOf(0.12f, -0.02f), floatArrayOf(0.08f, 0.035f), floatArrayOf(-0.09f, 0.03f))) {
            k.stroke(ellipse(bx + s * p[0], by - s * 0.012f + s * p[1] * 0.8f, s * 0.008f, s * 0.004f), w = 0.4f, alpha = 0.7f)
        }
        k.fill(ellipse(bx, by - s * 0.014f, s * 0.11f, s * 0.045f, rot = rad(-3f)), SAUCE)
        for (p in arrayOf(floatArrayOf(-0.06f, -0.01f, 0.024f), floatArrayOf(0.03f, -0.02f, 0.022f), floatArrayOf(-0.01f, 0.018f, 0.02f), floatArrayOf(0.07f, 0.008f, 0.018f))) {
            val m = ellipse(bx + s * p[0], by - s * 0.014f + s * p[1], s * p[2], s * p[2] * 0.55f)
            k.fill(m, CHEESE, exact = true)
            k.stroke(m.slice(5, 40), w = 0.35f, alpha = 0.5f)
        }
        for (p in arrayOf(floatArrayOf(-0.025f, -0.022f, -20f), floatArrayOf(0.05f, 0.015f, 30f), floatArrayOf(-0.075f, 0.016f, 10f))) {
            val leaf = ellipse(bx + s * p[0], by - s * 0.014f + s * p[1], s * 0.016f, s * 0.008f, rot = rad(p[2]))
            k.fill(leaf, BASIL, exact = true)
            k.stroke(leaf, w = 0.35f, alpha = 0.6f)
        }
    }

    /** No selfie: closed happy eyes, brows, nose, cheeks and a smile under a big moustache. */
    private fun cartoonFace(k: Kit, c: Canvas, s: Float, cx: Float, cy: Float) {
        val cheek = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHEEK }
        for (sg in floatArrayOf(-1f, 1f)) {
            k.stroke(Pts().moveTo(cx + sg * s * 0.075f, cy - s * 0.035f).quadTo(cx + sg * s * 0.05f, cy - s * 0.058f, cx + sg * s * 0.025f, cy - s * 0.035f), w = 1.1f)
            k.stroke(Pts().moveTo(cx + sg * s * 0.085f, cy - s * 0.075f).quadTo(cx + sg * s * 0.055f, cy - s * 0.09f, cx + sg * s * 0.02f, cy - s * 0.08f), w = 1.2f)
            c.drawOval(cx + sg * s * 0.075f - s * 0.025f, cy + s * 0.012f - s * 0.016f, cx + sg * s * 0.075f + s * 0.025f, cy + s * 0.012f + s * 0.016f, cheek)
        }
        k.stroke(Pts().moveTo(cx - s * 0.005f, cy - s * 0.03f).quadTo(cx - s * 0.02f, cy + s * 0.012f, cx + s * 0.01f, cy + s * 0.018f), w = 0.8f)
        val tache = Pts().moveTo(cx, cy + s * 0.03f)
            .cubicTo(cx - s * 0.03f, cy + s * 0.012f, cx - s * 0.085f, cy + s * 0.02f, cx - s * 0.1f, cy)
            .cubicTo(cx - s * 0.095f, cy + s * 0.06f, cx - s * 0.03f, cy + s * 0.06f, cx, cy + s * 0.045f)
            .cubicTo(cx + s * 0.03f, cy + s * 0.06f, cx + s * 0.095f, cy + s * 0.06f, cx + s * 0.1f, cy)
            .cubicTo(cx + s * 0.085f, cy + s * 0.02f, cx + s * 0.03f, cy + s * 0.012f, cx, cy + s * 0.03f)
        k.shape(tache, TACHE, w = 0.7f)
        k.stroke(Pts().moveTo(cx - s * 0.035f, cy + s * 0.075f).quadTo(cx, cy + s * 0.095f, cx + s * 0.035f, cy + s * 0.075f), w = 0.9f)
    }

    // ---------------------------------------------------------------- geometry

    /** A polyline sampled from lines and curves. */
    private class Pts {
        var xs = FloatArray(64); var ys = FloatArray(64); var n = 0
        private var px = 0f; private var py = 0f

        fun add(x: Float, y: Float) {
            if (n == xs.size) { xs = xs.copyOf(n * 2); ys = ys.copyOf(n * 2) }
            xs[n] = x; ys[n] = y; n++
        }
        fun moveTo(x: Float, y: Float) = apply { px = x; py = y; add(x, y) }
        fun lineTo(x: Float, y: Float) = apply {
            for (i in 1..12) add(px + (x - px) * i / 12, py + (y - py) * i / 12)
            px = x; py = y
        }
        fun quadTo(qx: Float, qy: Float, x: Float, y: Float) = apply {
            for (i in 1..20) {
                val t = i / 20f; val u = 1 - t
                add(u * u * px + 2 * u * t * qx + t * t * x, u * u * py + 2 * u * t * qy + t * t * y)
            }
            px = x; py = y
        }
        fun cubicTo(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float) = apply {
            for (i in 1..24) {
                val t = i / 24f; val u = 1 - t
                add(u * u * u * px + 3 * u * u * t * ax + 3 * u * t * t * bx + t * t * t * x, u * u * u * py + 3 * u * u * t * ay + 3 * u * t * t * by + t * t * t * y)
            }
            px = x; py = y
        }
        fun slice(from: Int, to: Int) = Pts().also { p -> for (i in from until min(to, n)) p.add(xs[i], ys[i]) }
        fun polygon(path: Path) = path.apply {
            reset()
            moveTo(xs[0], ys[0])
            for (i in 1 until n) lineTo(xs[i], ys[i])
            close()
        }
    }

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f): Pts {
        val p = Pts()
        val cr = cos(rot); val sr = sin(rot)
        for (i in 0..80) {
            val a = 2 * PI.toFloat() * i / 80
            val x = cos(a) * rx; val y = sin(a) * ry
            p.add(cx + x * cr - y * sr, cy + x * sr + y * cr)
        }
        return p
    }

    /** An egg, narrower towards the chin: the same shape [FaceSketch] cuts the face to. */
    private fun headShape(cx: Float, cy: Float, rx: Float, ry: Float): Pts {
        val p = Pts()
        for (i in 0..90) {
            val a = PI.toFloat() / 2 + i / 90f * 2 * PI.toFloat()
            val dy = sin(a)
            val r = rx * (if (dy > 0) 1 - 0.18f * dy * dy else 1f)
            p.add(cx + cos(a) * r, cy + dy * ry)
        }
        return p
    }

    private class Limb(val outline: Pts, val endLx: Float, val endLy: Float, val endRx: Float, val endRy: Float)

    /** A sleeve: a closed outline around a curved centre line, tapering from [w0] to [w1]. */
    private fun limb(ax: Float, ay: Float, mx: Float, my: Float, bx: Float, by: Float, w0: Float, w1: Float): Limb {
        val n = 24
        val l = Pts(); val r = Pts()
        for (i in 0..n) {
            val t = i.toFloat() / n; val u = 1 - t
            val x = u * u * ax + 2 * u * t * mx + t * t * bx
            val y = u * u * ay + 2 * u * t * my + t * t * by
            val dx = 2 * u * (mx - ax) + 2 * t * (bx - mx)
            val dy = 2 * u * (my - ay) + 2 * t * (by - my)
            val d = hypot(dx, dy).takeIf { it > 0f } ?: 1f
            val w = (w0 + (w1 - w0) * t) / 2
            l.add(x - dy / d * w, y + dx / d * w)
            r.add(x + dy / d * w, y - dx / d * w)
        }
        val outline = Pts()
        for (i in 0 until l.n) outline.add(l.xs[i], l.ys[i])
        for (i in r.n - 1 downTo 0) outline.add(r.xs[i], r.ys[i])
        return Limb(outline, l.xs[n], l.ys[n], r.xs[0], r.ys[0])
    }

    /** The line across the end of a sleeve, bowed by ([bx], [by]). */
    private fun cuff(limb: Limb, bx: Float, by: Float) = Pts().moveTo(limb.endLx, limb.endLy)
        .quadTo((limb.endLx + limb.endRx) / 2 + bx, (limb.endLy + limb.endRy) / 2 + by, limb.endRx, limb.endRy)

    private fun rad(d: Float) = d * PI.toFloat() / 180f

    // ---------------------------------------------------------------- the pen

    private val NOISE = FloatArray(256).also { v ->
        var x = 7L
        for (i in v.indices) { x = (x * 1103515245L + 12345L) and 0x7fffffffL; v[i] = x / 2147483647f * 2 - 1 }
    }

    /** Smooth 1-D noise in -1..1; [seed] picks a different stretch of it. */
    private fun noise(seed: Int, x: Float): Float {
        val i = floor(x).toInt()
        val f = x - i
        val o = seed * 53
        val a = NOISE[(i + o) and 255]
        val b = NOISE[(i + 1 + o) and 255]
        val u = (1 - cos(f * PI.toFloat())) / 2
        return a + (b - a) * u
    }

    private class Rng(seed: Int) {
        private var x = (seed.toLong() and 0x7fffffffL).coerceAtLeast(1L)
        fun next(): Float { x = (x * 1103515245L + 12345L) and 0x7fffffffL; return x / 2147483647f }
    }

    /**
     * How the pen draws at figure size [s]: every line twice, the second pass a little off and
     * lighter; fills a touch off the line; shadows as hatching.
     */
    private class Kit(val c: Canvas, val s: Float, private var seed: Int) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = INK
        }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        fun stroke(p: Pts, w: Float = 1f, passes: Int = 2, alpha: Float = 1f) {
            if (p.n < 2) return
            val width = w * LINE * s
            for (pass in 0 until passes) {
                val nz = seed++ * 7 + pass * 131
                val shiftX = if (pass > 0) noise(nz, 3.3f) * 0.6f * JITTER * s else 0f
                val shiftY = if (pass > 0) noise(nz, 7.7f) * 0.6f * JITTER * s else 0f
                var len = 0f
                path.reset()
                for (i in 0 until p.n) {
                    if (i > 0) len += hypot(p.xs[i] - p.xs[i - 1], p.ys[i] - p.ys[i - 1])
                    val a = max(0, i - 1); val b = min(p.n - 1, i + 1)
                    val dx = p.xs[b] - p.xs[a]; val dy = p.ys[b] - p.ys[a]
                    val d = hypot(dx, dy).takeIf { it > 0f } ?: 1f
                    val off = noise(nz, len / (s * 0.07f)) * JITTER * s
                    val x = p.xs[i] - dy / d * off + shiftX
                    val y = p.ys[i] + dx / d * off + shiftY
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                line.strokeWidth = width * (if (pass > 0) 0.65f else 1f)
                line.alpha = (255 * alpha * (if (pass > 0) 0.7f else 1f)).toInt().coerceIn(0, 255)
                c.drawPath(path, line)
            }
        }

        fun fill(p: Pts, color: Int, exact: Boolean = false) {
            c.save()
            if (!exact) c.translate(OFF_X * s, OFF_Y * s)
            paint.color = color
            c.drawPath(p.polygon(path), paint)
            c.restore()
        }

        fun shape(p: Pts, color: Int, w: Float = 1f) {
            fill(p, color)
            stroke(p, w)
        }

        /** Hatching inside [p] where [side] (x, y) > 0, the lines at [angle] degrees. */
        fun shadow(p: Pts, angle: Float = 62f, spacing: Float = 0.014f, alpha: Float = 0.7f, side: (Float, Float) -> Float) {
            if (p.n < 3) return
            var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (i in 0 until p.n) { x0 = min(x0, p.xs[i]); x1 = max(x1, p.xs[i]); y0 = min(y0, p.ys[i]); y1 = max(y1, p.ys[i]) }
            c.save()
            c.clipPath(p.polygon(Path()))
            val r = Rng(seed++ * 3 + 1)
            val a = angle * PI.toFloat() / 180f
            val sp = s * spacing
            val step = s * 0.004f
            val diag = hypot(x1 - x0, y1 - y0)
            val mx = (x0 + x1) / 2; val my = (y0 + y1) / 2
            val ux = cos(a); val uy = sin(a); val nx = -uy; val ny = ux
            var k = -diag
            while (k <= diag) {
                var on = false
                var ax = 0f; var ay = 0f; var bx = 0f; var by = 0f; var long = false
                var d = -diag
                while (d <= diag + step) {
                    val x = mx + nx * k + ux * d; val y = my + ny * k + uy * d
                    val inside = d <= diag && side(x, y) > (r.next() - 0.5f) * 0.06f
                    if (inside && !on) { on = true; ax = x; ay = y; long = false }
                    else if (inside) { bx = x; by = y; long = true }
                    else if (on) {
                        on = false
                        if (long) {
                            val mid = Pts()
                            mid.add(ax, ay)
                            mid.add((ax + bx) / 2 + (r.next() - 0.5f) * s * 0.003f, (ay + by) / 2)
                            mid.add(bx, by)
                            stroke(mid, w = 0.5f, passes = 1, alpha = alpha)
                        }
                    }
                    d += step
                }
                k += sp
            }
            c.restore()
        }

        companion object {
            const val LINE = 0.0105f
            const val JITTER = 0.0032f
            const val OFF_X = 0.006f
            const val OFF_Y = 0.004f
        }
    }
}
