package com.vibepizzaiolo.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.media.FaceDetector
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Turns a selfie into a cartoon portrait, offline: finds the face, crops a square around it,
 * then smooths colours into flat patches (Kuwahara filter), posterises them and draws dark
 * outlines along the edges, like a comic drawing.
 */
object Cartoonizer {
    const val SIZE = 360

    fun cartoon(selfie: Bitmap): Bitmap {
        val face = cropFace(selfie)
        val smooth = kuwahara(face, radius = 3)
        return outline(posterize(smooth, levels = 6), smooth)
    }

    /** Square around the face (Android's built-in face finder), or the centre if none is found. */
    fun cropFace(src: Bitmap): Bitmap {
        val scale = 480f / max(src.width, src.height)
        val w = ((src.width * scale).toInt() / 2) * 2 // the face finder wants an even width
        val h = (src.height * scale).toInt()
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.RGB_565, false)
        val faces = arrayOfNulls<FaceDetector.Face>(1)
        val found = runCatching { FaceDetector(w, h, 1).findFaces(small, faces) }.getOrDefault(0)
        val rect = if (found > 0 && faces[0] != null) {
            val f = faces[0]!!
            val mid = PointF().also { f.getMidPoint(it) }
            val eyes = f.eyesDistance()
            val side = eyes * 4.4f
            val cx = mid.x / scale
            val cy = (mid.y + eyes * 0.45f) / scale
            val half = side / scale / 2
            Rect((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        } else {
            val side = min(src.width, src.height)
            Rect((src.width - side) / 2, (src.height - side) / 3, (src.width + side) / 2, (src.height - side) / 3 + side)
        }
        // keep the square inside the photo
        val side = min(rect.width(), min(src.width, src.height))
        val left = rect.centerX() - side / 2
        val top = rect.centerY() - side / 2
        val safe = Rect(
            left.coerceIn(0, src.width - side), top.coerceIn(0, src.height - side), 0, 0,
        ).apply { right = this.left + side; bottom = this.top + side }
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, safe, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Edge-preserving smoothing: each pixel takes the mean of its calmest neighbouring quadrant. */
    fun kuwahara(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val out = IntArray(w * h)
        val qx = intArrayOf(-radius, 0, -radius, 0)
        val qy = intArrayOf(-radius, -radius, 0, 0)
        for (y in 0 until h) for (x in 0 until w) {
            var bestVar = Float.MAX_VALUE
            var best = px[y * w + x]
            for (q in 0 until 4) {
                var sr = 0f; var sg = 0f; var sb = 0f; var sl = 0f; var sl2 = 0f; var n = 0
                for (dy in 0..radius) for (dx in 0..radius) {
                    val xx = (x + qx[q] + dx).coerceIn(0, w - 1)
                    val yy = (y + qy[q] + dy).coerceIn(0, h - 1)
                    val c = px[yy * w + xx]
                    val r = (c shr 16 and 0xFF).toFloat()
                    val g = (c shr 8 and 0xFF).toFloat()
                    val b = (c and 0xFF).toFloat()
                    val l = 0.3f * r + 0.59f * g + 0.11f * b
                    sr += r; sg += g; sb += b; sl += l; sl2 += l * l; n++
                }
                val mean = sl / n
                val variance = sl2 / n - mean * mean
                if (variance < bestVar) {
                    bestVar = variance
                    best = (0xFF shl 24) or ((sr / n).roundToInt() shl 16) or ((sg / n).roundToInt() shl 8) or (sb / n).roundToInt()
                }
            }
            out[y * w + x] = best
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Flat comic colours: a few levels per channel, slightly more saturated. */
    fun posterize(src: Bitmap, levels: Int): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val step = 255f / (levels - 1)
        for (i in px.indices) {
            val c = px[i]
            var r = (c shr 16 and 0xFF).toFloat()
            var g = (c shr 8 and 0xFF).toFloat()
            var b = (c and 0xFF).toFloat()
            val l = (r + g + b) / 3
            r = (l + (r - l) * 1.25f).coerceIn(0f, 255f)
            g = (l + (g - l) * 1.25f).coerceIn(0f, 255f)
            b = (l + (b - l) * 1.25f).coerceIn(0f, 255f)
            fun q(v: Float) = ((v / step).roundToInt() * step).roundToInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (q(r) shl 16) or (q(g) shl 8) or q(b)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Dark lines where the brightness changes sharply (Sobel), drawn over [colours]. */
    fun outline(colours: Bitmap, smooth: Bitmap): Bitmap {
        val w = smooth.width
        val h = smooth.height
        val px = IntArray(w * h).also { smooth.getPixels(it, 0, w, 0, 0, w, h) }
        val lum = FloatArray(w * h) { i ->
            val c = px[i]
            0.3f * (c shr 16 and 0xFF) + 0.59f * (c shr 8 and 0xFF) + 0.11f * (c and 0xFF)
        }
        val col = IntArray(w * h).also { colours.getPixels(it, 0, w, 0, 0, w, h) }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            fun l(dx: Int, dy: Int) = lum[(y + dy) * w + x + dx]
            val gx = -l(-1, -1) - 2 * l(-1, 0) - l(-1, 1) + l(1, -1) + 2 * l(1, 0) + l(1, 1)
            val gy = -l(-1, -1) - 2 * l(0, -1) - l(1, -1) + l(-1, 1) + 2 * l(0, 1) + l(1, 1)
            val mag = sqrt(gx * gx + gy * gy)
            if (mag > 70f) {
                val k = ((mag - 70f) / 90f).coerceIn(0f, 1f) * 0.85f
                val c = col[y * w + x]
                val r = ((c shr 16 and 0xFF) * (1 - k) + 30 * k).roundToInt()
                val g = ((c shr 8 and 0xFF) * (1 - k) + 22 * k).roundToInt()
                val b = ((c and 0xFF) * (1 - k) + 18 * k).roundToInt()
                col[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(col, w, h, Bitmap.Config.ARGB_8888)
    }
}
