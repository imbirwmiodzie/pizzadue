package com.vibepizzaiolo.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.FaceDetector
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns a selfie into a pen-and-ink face that fits the drawn pizzaiolo, offline:
 * finds the eyes, puts them level at a fixed place in a square, smooths the skin (Kuwahara), draws
 * ink lines along the edges (difference of Gaussians), hatches the shadows and washes skin and
 * hair in flat colours. Everything outside the head and the hair is transparent, so the face drops
 * into the drawing's head.
 *
 * With [FaceModels] the eyes come from MediaPipe's face detector and the head's outline, skin and
 * hair from its selfie segmenter; without them, from Android's face finder and a colour test.
 */
object FaceSketch {
    const val SIZE = 360

    // where the eyes land in the square
    const val EYE_X = 180f
    const val EYE_Y = 120f
    const val EYE_D = 95f

    /** The head's egg in the square: [Pizzaiolo] fits this onto its head. */
    const val HEAD_CX = EYE_X
    const val HEAD_CY = EYE_Y + 0.47f * EYE_D
    const val HEAD_RX = 1.05f * EYE_D

    private const val INK = 0xFF2A1E16.toInt()
    private const val SKIN = 0xFFF3D2B3.toInt()
    private const val SKIN_SHADE = 0xFFE2AE8C.toInt()
    private const val HAIR = 0xFF8A6440.toInt()

    /** The eye-aligned square cut from the selfie, the face drawn from it, and whether a face was found at all. */
    class Drawn(val crop: Bitmap, val face: Bitmap, val faceFound: Boolean)

    /** Where the eyes are in a photo: the point between them, their distance, and the head's tilt (radians). */
    class Eyes(val x: Float, val y: Float, val distance: Float, val tilt: Float = 0f)

    fun sketch(selfie: Bitmap, models: FaceModels? = null): Drawn {
        val eyes = models?.let { runCatching { it.findEyes(selfie) }.getOrNull() } ?: androidEyes(selfie)
        val crop = cropFace(selfie, eyes ?: guessEyes(selfie))
        val parts = models?.let { runCatching { it.parts(crop) }.getOrNull() }
        return Drawn(crop, draw(crop, parts), eyes != null)
    }

    /** Android's own face finder (no tilt), or null when it finds no face. */
    private fun androidEyes(src: Bitmap): Eyes? {
        val scale = 480f / max(src.width, src.height)
        val w = ((src.width * scale).toInt() / 2) * 2 // the face finder wants an even width
        val h = (src.height * scale).toInt()
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.RGB_565, false)
        val faces = arrayOfNulls<FaceDetector.Face>(1)
        val found = runCatching { FaceDetector(w, h, 1).findFaces(small, faces) }.getOrDefault(0)
        val f = faces[0]
        if (found <= 0 || f == null) return null
        val mid = PointF().also { f.getMidPoint(it) }
        return Eyes(mid.x / scale, mid.y / scale, f.eyesDistance() / scale)
    }

    /** No face found: assume one in the middle, as most selfies are. */
    private fun guessEyes(src: Bitmap): Eyes {
        val side = min(src.width, src.height)
        return Eyes(src.width / 2f, src.height * 0.42f, side * 0.26f)
    }

    /**
     * A square with the eyes level at ([EYE_X], [EYE_Y]), [EYE_D] apart; what lies outside the
     * photo takes its corner colour.
     */
    fun cropFace(src: Bitmap, eyes: Eyes): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(src.getPixel(2, 2) or (0xFF shl 24))
        val k = EYE_D / eyes.distance
        val m = Matrix().apply {
            postTranslate(-eyes.x, -eyes.y)
            postRotate(-Math.toDegrees(eyes.tilt.toDouble()).toFloat())
            postScale(k, k)
            postTranslate(EYE_X, EYE_Y)
        }
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * The pen-and-ink face, transparent outside the head and hair. [parts] is the segmenter's
     * answer for this square ([FaceModels.parts]); without it the head is an egg from the eyes
     * plus the hair found by colour.
     */
    fun draw(square: Bitmap, parts: FloatArray? = null): Bitmap {
        val n = SIZE
        val px = IntArray(n * n).also { square.getPixels(it, 0, n, 0, 0, n, n) }
        val lum = FloatArray(n * n) { i -> luma(px[i]) }
        val smoothL = kuwahara(lum, 3)
        val lines = dogLines(smoothL, 1.5f, 0.013f)
        val tone = blur(smoothL, 2.5f)
        val egg = headMask(0.1f)
        val p = parts?.let { upsample(it) }
        // how much of each pixel is head, and of that, skin, hair or something else (glasses, a hat)
        val mask: FloatArray
        val skinShare: FloatArray
        val hairShare: FloatArray
        val otherShare: FloatArray
        if (p != null) {
            mask = headFromParts(p, egg)
            skinShare = FloatArray(n * n) { i -> p.skin[i] / (p.skin[i] + p.hair[i] + p.other[i] + 1e-3f) }
            hairShare = FloatArray(n * n) { i -> p.hair[i] / (p.skin[i] + p.hair[i] + p.other[i] + 1e-3f) }
            otherShare = FloatArray(n * n) { i -> 1 - skinShare[i] - hairShare[i] }
        } else {
            mask = headAndHair(px)
            skinShare = egg
            hairShare = FloatArray(n * n) { i -> 1 - egg[i] }
            otherShare = FloatArray(n * n)
        }
        val sil = silhouette(mask, 5f)
        val skinL = skinLevel(tone, if (p != null) skinShare else null)
        val hair = tame(hairColour(px, if (p != null) p.hair else null, mask, egg), rgb(HAIR), 0.25f, 0.8f)
        val skin = rgb(SKIN)
        val shade = rgb(SKIN_SHADE)
        val ink = rgb(INK)
        val out = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x
            // skin wash with a shade tone where the face is darker
            val sh = smooth(skinL * 0.86f, skinL * 0.7f, tone[i])
            var r = mix(skin[0], shade[0], sh)
            var g = mix(skin[1], shade[1], sh)
            var b = mix(skin[2], shade[2], sh)
            // hair: its own colour, a little darker where the hair is darker
            val hairT = if (p != null) hairShare[i] else
                min(1f, hairShare[i] * smooth(skinL * 1.05f, skinL * 0.85f, tone[i]) + hairShare[i] * 0.6f)
            r = mix(r, hair[0], hairT); g = mix(g, hair[1], hairT); b = mix(b, hair[2], hairT)
            // glasses, a hat: the photo's own colour, tamed
            if (otherShare[i] > 0.01f) {
                val c = tame(rgb(px[i]), skin, 0.2f, 0.7f)
                r = mix(r, c[0], otherShare[i]); g = mix(g, c[1], otherShare[i]); b = mix(b, c[2], otherShare[i])
            }
            // teeth and the whites of the eyes stay paper; shiny skin does not
            if (skinShare[i] > 0.5f && tone[i] > skinL * 1.12f && featureZone(x, y)) {
                r = mix(r, 252f, 0.7f); g = mix(g, 252f, 0.7f); b = mix(b, 252f, 0.7f)
            }
            // hatching in the darker half of the face and the hair, at 45 degrees with a wobble
            val wob = kotlin.math.sin(y * 0.11f) * 0.8f + kotlin.math.sin(x * 0.07f) * 0.5f
            val h1 = if (abs(((x + y + wob) % 7f) - 3.5f) < 0.7f) 1f else 0f
            val hatch = h1 * smooth(skinL * 0.78f, skinL * 0.6f, tone[i]) * 0.5f
            val a = max(lines[i], max(hatch, sil[i]))
            r = mix(r, ink[0], a); g = mix(g, ink[1], a); b = mix(b, ink[2], a)
            val alpha = (255 * max(mask[i], sil[i])).roundToInt().coerceIn(0, 255)
            out[i] = (alpha shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(out, n, n, Bitmap.Config.ARGB_8888)
    }

    // ---------------------------------------------------------------- the segmenter's parts

    private class Parts(val hair: FloatArray, val skin: FloatArray, val other: FloatArray)

    /** The segmenter's hair, face-skin and other probabilities, scaled up to the square. */
    private fun upsample(parts: FloatArray): Parts {
        val n = SIZE
        val m = FaceModels.SEG
        val k = FaceModels.CLASSES
        val hair = FloatArray(n * n); val skin = FloatArray(n * n); val other = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val gx = ((x + 0.5f) * m / n - 0.5f).coerceIn(0f, m - 1f)
            val gy = ((y + 0.5f) * m / n - 0.5f).coerceIn(0f, m - 1f)
            val x0 = gx.toInt(); val y0 = gy.toInt()
            val x1 = min(x0 + 1, m - 1); val y1 = min(y0 + 1, m - 1)
            val fx = gx - x0; val fy = gy - y0
            fun at(c: Int): Float {
                val a = parts[(y0 * m + x0) * k + c]; val b = parts[(y0 * m + x1) * k + c]
                val d = parts[(y1 * m + x0) * k + c]; val e = parts[(y1 * m + x1) * k + c]
                return mix(mix(a, b, fx), mix(d, e, fx), fy)
            }
            val i = y * n + x
            hair[i] = at(FaceModels.HAIR); skin[i] = at(FaceModels.FACE_SKIN); other[i] = at(FaceModels.OTHER)
        }
        return Parts(hair, skin, other)
    }

    /**
     * The head from the segmenter: face skin and hair near the face, and glasses or a hat on it.
     * Long hair is cut a little below the jaw, so it never covers the jacket.
     */
    private fun headFromParts(p: Parts, egg: FloatArray): FloatArray {
        val n = SIZE
        val rx = HEAD_RX * 1.6f
        val ry = 1.2f * EYE_D * 1.5f
        val cut = EYE_Y + 1.75f * EYE_D
        val raw = FloatArray(n * n) { i ->
            val x = i % n; val y = i / n
            val ex = (x - HEAD_CX) / rx; val ey = (y - HEAD_CY) / ry
            val near = (1 - smooth(0.85f, 1f, ex * ex + ey * ey)) * (1 - smooth(cut - 12f, cut + 12f, y.toFloat()))
            max(p.skin[i], max(p.hair[i], p.other[i] * egg[i])) * near
        }
        val b = blur(raw, 1.2f)
        return FloatArray(n * n) { i -> smooth(0.35f, 0.65f, b[i]) }
    }

    // ---------------------------------------------------------------- masks

    /** Head-shaped mask from the eye positions: an egg, narrower towards the chin, feathered at the edge. */
    private fun headMask(feather: Float): FloatArray {
        val n = SIZE
        val ry = 1.2f * EYE_D
        return FloatArray(n * n) { i ->
            val x = i % n; val y = i / n
            val dy = (y - HEAD_CY) / ry
            val rxy = HEAD_RX * (if (dy > 0) 1 - 0.18f * dy * dy else 1f)
            val dx = (x - HEAD_CX) / rxy
            1 - smooth(1 - feather, 1f, sqrt(dx * dx + dy * dy))
        }
    }

    /**
     * The head and the hair around it: pixels near the head that are clearly not the colour of the
     * background (taken from the top corners). Hair stops at the jaw, so collars never come along.
     */
    private fun headAndHair(px: IntArray): FloatArray {
        val n = SIZE
        val egg = headMask(0.1f)
        val corner = ArrayList<Int>()
        for (y in 0 until 40 step 2) for (x in 0 until 40 step 2) { corner += px[y * n + x]; corner += px[y * n + n - 1 - x] }
        val bg = IntArray(3) { ch -> corner.map { (it shr (16 - 8 * ch)) and 0xFF }.sorted()[corner.size / 2] }
        val rx = HEAD_RX * 1.4f
        val ry = 1.2f * EYE_D * 1.3f
        val chinY = EYE_Y + 0.6f * EYE_D
        val raw = FloatArray(n * n) { i ->
            val x = i % n; val y = i / n
            val ex = (x - HEAD_CX) / rx; val ey = (y - HEAD_CY) / ry
            val inside = ex * ex + ey * ey < 1 && y < chinY &&
                abs(x - HEAD_CX) < HEAD_RX * (1.4f - 0.45f * smooth(EYE_Y - 0.3f * EYE_D, EYE_Y + 0.9f * EYE_D, y.toFloat()))
            if (!inside) 0f else {
                val c = px[i]
                val dr = ((c shr 16) and 0xFF) - bg[0]; val dg = ((c shr 8) and 0xFF) - bg[1]; val db = (c and 0xFF) - bg[2]
                smooth(35f, 70f, sqrt((dr * dr + dg * dg + db * db).toFloat()))
            }
        }
        val b = blur(raw, 4f) // close small holes
        return FloatArray(n * n) { i -> max(egg[i], smooth(0.35f, 0.65f, b[i])) }
    }

    /** Ink along the edge of a mask, about [w] pixels wide: the outline of head and hair. */
    private fun silhouette(mask: FloatArray, w: Float): FloatArray {
        val b = blur(mask, w * 0.5f)
        return FloatArray(b.size) { i -> 1 - min(1f, abs(b[i] - 0.5f) / 0.16f) }
    }

    /** Eyes and mouth: the only places where white may stay white. */
    private fun featureZone(x: Int, y: Int): Boolean {
        val dx = abs(x - EYE_X) / EYE_D
        val dy = (y - EYE_Y) / EYE_D
        return (dy > -0.12f && dy < 0.12f && dx > 0.25f && dx < 0.75f) || (dy > 0.6f && dy < 1.0f && dx < 0.42f)
    }

    /** The face's own brightness: the median over the skin, or over the middle of the face. */
    private fun skinLevel(tone: FloatArray, skin: FloatArray?): Float {
        val v = ArrayList<Float>()
        if (skin != null) {
            for (i in tone.indices step 3) if (skin[i] > 0.8f) v += tone[i]
        }
        if (v.size < 200) {
            v.clear()
            var y = EYE_Y
            while (y < EYE_Y + EYE_D * 0.8f) {
                var x = EYE_X - EYE_D * 0.4f
                while (x < EYE_X + EYE_D * 0.4f) { v += tone[y.roundToInt() * SIZE + x.roundToInt()]; x += 3 }
                y += 3
            }
        }
        v.sort()
        return max(0.05f, v[v.size / 2])
    }

    /** The average colour of the hair: where the segmenter sees hair, or inside the mask, outside the face, above the eyes. */
    private fun hairColour(px: IntArray, hair: FloatArray?, mask: FloatArray, egg: FloatArray): FloatArray {
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        for (y in 0 until (if (hair != null) SIZE else EYE_Y.toInt())) for (x in 0 until SIZE) {
            val i = y * SIZE + x
            val take = if (hair != null) hair[i] > 0.8f && mask[i] > 0.5f else mask[i] > 0.9f && egg[i] < 0.3f
            if (take) {
                val c = px[i]; r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
            }
        }
        return if (n > 200) floatArrayOf(r / n, g / n, b / n) else rgb(HAIR)
    }

    /** Pulls a colour towards a palette colour and softens its saturation. */
    private fun tame(c: FloatArray, towards: FloatArray, k: Float, sat: Float): FloatArray {
        val l = (c[0] + c[1] + c[2]) / 3
        return FloatArray(3) { i -> mix(l + (c[i] - l) * sat, towards[i], k) }
    }

    // ---------------------------------------------------------------- filters

    /** Edge-preserving smoothing: each pixel takes the mean of its calmest neighbouring quadrant. */
    fun kuwahara(src: FloatArray, r: Int): FloatArray {
        val n = SIZE
        val out = FloatArray(n * n)
        val qx = intArrayOf(-r, 0, -r, 0)
        val qy = intArrayOf(-r, -r, 0, 0)
        for (y in 0 until n) for (x in 0 until n) {
            var bestVar = Float.MAX_VALUE
            var best = src[y * n + x]
            for (q in 0 until 4) {
                var s = 0f; var s2 = 0f; var cnt = 0
                for (dy in 0..r) {
                    val yy = (y + qy[q] + dy).coerceIn(0, n - 1)
                    for (dx in 0..r) {
                        val v = src[yy * n + (x + qx[q] + dx).coerceIn(0, n - 1)]
                        s += v; s2 += v * v; cnt++
                    }
                }
                val mean = s / cnt
                val variance = s2 / cnt - mean * mean
                if (variance < bestVar) { bestVar = variance; best = mean }
            }
            out[y * n + x] = best
        }
        return out
    }

    /** Ink on the dark side of edges: the difference of two Gaussian blurs. 1 = ink. */
    fun dogLines(l: FloatArray, sigma: Float, tau: Float): FloatArray {
        val g1 = blur(l, sigma)
        val g2 = blur(l, sigma * 1.6f)
        return FloatArray(l.size) { i -> smooth(tau, tau * 2.2f, g2[i] - g1[i]) }
    }

    fun blur(src: FloatArray, sigma: Float): FloatArray {
        val n = SIZE
        val r = ceil(sigma * 3).toInt()
        val k = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        val sum = k.sum()
        for (i in k.indices) k[i] /= sum
        val tmp = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            var a = 0f
            for (i in -r..r) a += k[i + r] * src[y * n + (x + i).coerceIn(0, n - 1)]
            tmp[y * n + x] = a
        }
        val out = FloatArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            var a = 0f
            for (i in -r..r) a += k[i + r] * tmp[(y + i).coerceIn(0, n - 1) * n + x]
            out[y * n + x] = a
        }
        return out
    }

    private fun luma(c: Int) = (0.3f * ((c shr 16) and 0xFF) + 0.59f * ((c shr 8) and 0xFF) + 0.11f * (c and 0xFF)) / 255f
    private fun rgb(c: Int) = floatArrayOf(((c shr 16) and 0xFF).toFloat(), ((c shr 8) and 0xFF).toFloat(), (c and 0xFF).toFloat())
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }
}
