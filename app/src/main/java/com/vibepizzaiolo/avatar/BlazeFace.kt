package com.vibepizzaiolo.avatar

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the output of MediaPipe's short-range face detector (BlazeFace, 128 x 128 input): 896
 * anchor boxes, each with a score and a box plus six key points. Plain maths, so it is tested on
 * the JVM; [FaceModels] runs the model itself.
 */
object BlazeFace {
    const val INPUT = 128
    const val ANCHORS = 896
    const val VALUES = 16

    /** Anchor centres (x, y), 0..1: a 16 x 16 grid with 2 anchors per cell, then 8 x 8 with 6. */
    val anchors: FloatArray = FloatArray(ANCHORS * 2).also { a ->
        var i = 0
        for ((stride, perCell) in listOf(8 to 2, 16 to 6)) {
            val cells = INPUT / stride
            for (y in 0 until cells) for (x in 0 until cells) repeat(perCell) {
                a[i++] = (x + 0.5f) / cells
                a[i++] = (y + 0.5f) / cells
            }
        }
    }

    /** Eye centres in the model's input square, 0..1: the person's right eye (left in the photo) and left eye. */
    class Eyes(val rightX: Float, val rightY: Float, val leftX: Float, val leftY: Float, val score: Float)

    /**
     * The most confident face, its eyes averaged over the detections that overlap it (weighted by
     * score, as MediaPipe does), or null when no face scores [minScore].
     */
    fun decode(regressors: FloatArray, logits: FloatArray, minScore: Float = 0.5f): Eyes? {
        val score = FloatArray(ANCHORS) { 1f / (1f + exp(-logits[it].coerceIn(-100f, 100f))) }
        var best = 0
        for (i in 1 until ANCHORS) if (score[i] > score[best]) best = i
        if (score[best] < minScore) return null
        val b = box(regressors, best)
        var rx = 0f; var ry = 0f; var lx = 0f; var ly = 0f; var total = 0f
        for (i in 0 until ANCHORS) {
            if (score[i] < minScore || iou(b, box(regressors, i)) < 0.3f) continue
            val o = i * VALUES
            val w = score[i]
            rx += (regressors[o + 4] / INPUT + anchors[2 * i]) * w
            ry += (regressors[o + 5] / INPUT + anchors[2 * i + 1]) * w
            lx += (regressors[o + 6] / INPUT + anchors[2 * i]) * w
            ly += (regressors[o + 7] / INPUT + anchors[2 * i + 1]) * w
            total += w
        }
        return Eyes(rx / total, ry / total, lx / total, ly / total, score[best])
    }

    private fun box(r: FloatArray, i: Int): FloatArray {
        val o = i * VALUES
        val cx = r[o] / INPUT + anchors[2 * i]
        val cy = r[o + 1] / INPUT + anchors[2 * i + 1]
        val w = r[o + 2] / INPUT
        val h = r[o + 3] / INPUT
        return floatArrayOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val ix = max(0f, min(a[2], b[2]) - max(a[0], b[0]))
        val iy = max(0f, min(a[3], b[3]) - max(a[1], b[1]))
        val union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - ix * iy
        return if (union > 0f) ix * iy / union else 0f
    }
}
