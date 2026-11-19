package com.vibepizzaiolo

import android.graphics.Bitmap
import android.graphics.Canvas
import com.vibepizzaiolo.avatar.FaceSketch
import com.vibepizzaiolo.avatar.Pizzaiolo
import com.vibepizzaiolo.video.AudioClip
import com.vibepizzaiolo.video.Soundtrack
import com.vibepizzaiolo.video.VideoMaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReelTest {
    @Test fun guidedRecordingIsCutIntoTopAroundAndRim() {
        val p = VideoMaker.plan(15_000)!!
        assertEquals(300L..2_300L, p.top)
        assertEquals(2, p.around.size)
        p.around.forEach { assertTrue(it.first >= 2_600 && it.last <= 15_000 - 4_600) }
        assertTrue(p.around[0].last <= p.around[1].first)
        assertEquals((15_000L - 4_300)..(15_000L - 300), p.rim)
    }

    @Test fun shortRecordingIsCutByProportion() {
        val p = VideoMaker.plan(8_000)!!
        assertTrue(p.top.last <= p.around[0].first)
        assertTrue(p.around[1].last <= p.rim.first)
        assertEquals(8_000L, p.rim.last)
    }

    @Test fun tooShortRecordingIsRefused() {
        assertNull(VideoMaker.plan(3_000))
    }

    @Test fun selfieBecomesAFaceCutToTheHead() {
        val selfie = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF8899AA.toInt()) }
        val face = FaceSketch.sketch(selfie)
        assertEquals(FaceSketch.SIZE, face.width)
        assertEquals(FaceSketch.SIZE, face.height)
        // inside the head it is drawn, in the corners it is see-through
        assertEquals(255, face.getPixel(FaceSketch.HEAD_CX.toInt(), FaceSketch.HEAD_CY.toInt()) ushr 24)
        assertEquals(0, face.getPixel(2, FaceSketch.SIZE - 3) ushr 24)
    }

    @Test fun iconIsTheHeadInTheToque() {
        val icon = Pizzaiolo.icon(Bitmap.createBitmap(FaceSketch.SIZE, FaceSketch.SIZE, Bitmap.Config.ARGB_8888), 128)
        assertEquals(128, icon.width)
        assertEquals(128, icon.height)
    }

    @Test fun pizzaioloDrawsWithAndWithoutAFace() {
        val frame = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val c = Canvas(frame)
        Pizzaiolo.draw(c, 540f, 1600f, 880f, 0.5f, null)
        Pizzaiolo.draw(c, 540f, 1600f, 880f, 1.5f, Bitmap.createBitmap(FaceSketch.SIZE, FaceSketch.SIZE, Bitmap.Config.ARGB_8888))
        assertNotNull(frame)
    }

    @Test fun soundtrackMixesLayersAtTheirTimeAndHasTheReelsLength() {
        val file = java.io.File.createTempFile("reel", ".wav")
        val crunch = FloatArray(Soundtrack.RATE / 10) { 0.5f } // 100 ms
        Soundtrack.write(file, lengthMs = 2_000, stampAtMs = 1_500, layers = listOf(Soundtrack.Layer(200, crunch)))
        val bytes = file.readBytes()
        assertEquals(44 + 2 * Soundtrack.RATE * 2, bytes.size) // 2 s of 16-bit mono after the header
        fun sample(ms: Int): Int {
            val i = 44 + 2 * (Soundtrack.RATE * ms / 1000)
            return (bytes[i].toInt() and 0xFF) or (bytes[i + 1].toInt() shl 8)
        }
        assertEquals(0, sample(100))
        assertEquals((0.5f * 32767).toInt(), sample(250))
        assertEquals(0, sample(400))
        file.delete()
    }

    @Test fun resamplingKeepsDurationAndShape() {
        val src = FloatArray(48_000) { it / 48_000f } // 1 s ramp at 48 kHz
        val out = AudioClip.resample(src, 48_000, Soundtrack.RATE)
        assertEquals(Soundtrack.RATE, out.size)
        assertEquals(0.5f, out[Soundtrack.RATE / 2], 0.001f)
        assertTrue(AudioClip.resample(src, 48_000, 48_000) === src)
    }

    @Test fun stillPicturesCarryTheLengthAndFrameRateMedia3Needs() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val item = VideoMaker(context).stillItem(android.net.Uri.parse("file:///stamp.png"), VideoMaker.STAMP_MS)
        // Media3 1.4's image loader refuses items without these (it failed the reel on a phone)
        assertEquals(VideoMaker.STAMP_MS * 1000, item.durationUs)
        assertEquals(30, item.frameRate)
    }
}
