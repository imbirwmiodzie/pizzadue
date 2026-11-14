package com.vibepizzaiolo

import android.graphics.Bitmap
import android.graphics.Canvas
import com.vibepizzaiolo.avatar.Cartoonizer
import com.vibepizzaiolo.avatar.Pizzaiolo
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

    @Test fun selfieBecomesASquareCartoon() {
        val selfie = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val cartoon = Cartoonizer.cartoon(selfie)
        assertEquals(Cartoonizer.SIZE, cartoon.width)
        assertEquals(Cartoonizer.SIZE, cartoon.height)
    }

    @Test fun pizzaioloDrawsWithAndWithoutAFace() {
        val frame = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val c = Canvas(frame)
        Pizzaiolo.draw(c, 540f, 1600f, 880f, 0.5f, null)
        Pizzaiolo.draw(c, 540f, 1600f, 880f, 1.5f, Bitmap.createBitmap(Cartoonizer.SIZE, Cartoonizer.SIZE, Bitmap.Config.ARGB_8888))
        assertNotNull(frame)
    }
}
