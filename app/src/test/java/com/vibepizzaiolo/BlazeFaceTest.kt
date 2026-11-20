package com.vibepizzaiolo

import com.vibepizzaiolo.avatar.BlazeFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The face detector's raw output for a portrait (MediaPipe's short-range BlazeFace model, run on
 * the NASA astronaut portrait shipped with scikit-image) read back into eye positions. The
 * expected eyes are what MediaPipe's own face detector reports for the same photo, within a pixel
 * or two of 512.
 */
class BlazeFaceTest {
    private fun floats(name: String): FloatArray {
        val bytes = javaClass.classLoader!!.getResourceAsStream("blazeface/$name")!!.readBytes()
        val f = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(f.remaining()).also { f.get(it) }
    }

    @Test fun anchorsCoverBothGrids() {
        assertEquals(BlazeFace.ANCHORS * 2, BlazeFace.anchors.size)
        // first anchor: the top-left cell of the 16 x 16 grid; last: the bottom-right cell of the 8 x 8 grid
        assertEquals(0.5f / 16, BlazeFace.anchors[0], 1e-6f)
        assertEquals(7.5f / 8, BlazeFace.anchors[BlazeFace.ANCHORS * 2 - 1], 1e-6f)
    }

    @Test fun eyesOfAPortrait() {
        val eyes = BlazeFace.decode(floats("regressors.bin"), floats("logits.bin"))
        assertNotNull(eyes)
        eyes!!
        assertEquals(0.921f, eyes.score, 0.01f)
        // the person's right eye is on the left of the photo
        assertEquals(0.3971f, eyes.rightX, 0.006f)
        assertEquals(0.1971f, eyes.rightY, 0.006f)
        assertEquals(0.4831f, eyes.leftX, 0.006f)
        assertEquals(0.2113f, eyes.leftY, 0.006f)
    }

    @Test fun noFaceNoEyes() {
        val logits = FloatArray(BlazeFace.ANCHORS) { -5f }
        assertNull(BlazeFace.decode(FloatArray(BlazeFace.ANCHORS * BlazeFace.VALUES), logits))
    }
}
