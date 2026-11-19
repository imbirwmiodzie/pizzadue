package com.vibepizzaiolo.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Google's MediaPipe face models, run on the phone with LiteRT: the short-range face detector
 * (where the eyes are) and the selfie multiclass segmenter (which pixels are hair, face skin,
 * body skin, clothes, other or background). The model files are put into the app's assets by
 * scripts/fetch_models.sh; without them [open] returns null and the app falls back to Android's
 * own face finder and a colour test for the hair.
 */
class FaceModels private constructor(private val detector: Interpreter, private val segmenter: Interpreter) : Closeable {

    /** The eyes in [photo]'s pixels, or null when no face is found. */
    fun findEyes(photo: Bitmap): FaceSketch.Eyes? {
        val n = BlazeFace.INPUT
        // fit the photo into the square, keeping its shape (MediaPipe pads the same way)
        val s = n.toFloat() / max(photo.width, photo.height)
        val w = photo.width * s
        val h = photo.height * s
        val padX = (n - w) / 2
        val padY = (n - h) / 2
        val square = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        Canvas(square).apply {
            drawColor(Color.BLACK)
            drawBitmap(photo, null, RectF(padX, padY, padX + w, padY + h), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val input = pixels(square, n) { it / 127.5f - 1f }
        val regressors = ByteBuffer.allocateDirect(BlazeFace.ANCHORS * BlazeFace.VALUES * 4).order(ByteOrder.nativeOrder())
        val logits = ByteBuffer.allocateDirect(BlazeFace.ANCHORS * 4).order(ByteOrder.nativeOrder())
        val outputs = HashMap<Int, Any>()
        for (i in 0 until detector.outputTensorCount) {
            outputs[i] = if (detector.getOutputTensor(i).numElements() == BlazeFace.ANCHORS) logits else regressors
        }
        detector.runForMultipleInputsOutputs(arrayOf(input), outputs)
        val eyes = BlazeFace.decode(floats(regressors), floats(logits)) ?: return null
        // back from the padded square to the photo
        fun px(v: Float) = (v * n - padX) / s
        fun py(v: Float) = (v * n - padY) / s
        // the person's right eye is on the left of the photo
        val ax = px(eyes.rightX); val ay = py(eyes.rightY)
        val bx = px(eyes.leftX); val by = py(eyes.leftY)
        return FaceSketch.Eyes((ax + bx) / 2, (ay + by) / 2, hypot(bx - ax, by - ay), atan2(by - ay, bx - ax))
    }

    /**
     * What each pixel of a [FaceSketch.SIZE] square is: [SEG] x [SEG] x [CLASSES] probabilities,
     * classes 0 background, 1 hair, 2 body skin, 3 face skin, 4 clothes, 5 other (glasses, hats).
     */
    fun parts(square: Bitmap): FloatArray {
        val n = SEG
        val scaled = Bitmap.createScaledBitmap(square, n, n, true)
        val input = pixels(scaled, n) { it / 255f }
        val out = ByteBuffer.allocateDirect(n * n * CLASSES * 4).order(ByteOrder.nativeOrder())
        segmenter.run(input, out)
        return floats(out)
    }

    override fun close() {
        detector.close()
        segmenter.close()
    }

    private fun pixels(b: Bitmap, n: Int, scale: (Float) -> Float): ByteBuffer {
        val px = IntArray(n * n).also { b.getPixels(it, 0, n, 0, 0, n, n) }
        val buf = ByteBuffer.allocateDirect(n * n * 3 * 4).order(ByteOrder.nativeOrder())
        for (c in px) {
            buf.putFloat(scale(((c shr 16) and 0xFF).toFloat()))
            buf.putFloat(scale(((c shr 8) and 0xFF).toFloat()))
            buf.putFloat(scale((c and 0xFF).toFloat()))
        }
        buf.rewind()
        return buf
    }

    private fun floats(b: ByteBuffer): FloatArray {
        b.rewind()
        val f = b.asFloatBuffer()
        return FloatArray(f.remaining()).also { f.get(it) }
    }

    companion object {
        const val SEG = 256
        const val CLASSES = 6
        const val HAIR = 1
        const val FACE_SKIN = 3
        const val OTHER = 5
        private const val DETECTOR = "faces/blaze_face_short_range.tflite"
        private const val SEGMENTER = "faces/selfie_multiclass_256x256.tflite"

        /** The models, or null when they are missing or cannot run on this phone. */
        fun open(context: Context): FaceModels? = runCatching {
            val options = Interpreter.Options().setNumThreads(4)
            FaceModels(Interpreter(map(context, DETECTOR), options), Interpreter(map(context, SEGMENTER), options))
        }.getOrNull()

        private fun map(context: Context, name: String): MappedByteBuffer =
            context.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
            }
    }
}
