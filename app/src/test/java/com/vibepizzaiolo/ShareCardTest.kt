package com.vibepizzaiolo

import android.graphics.Bitmap
import com.vibepizzaiolo.share.CardInfo
import com.vibepizzaiolo.share.CardRenderer
import com.vibepizzaiolo.share.Design
import com.vibepizzaiolo.share.Format
import com.vibepizzaiolo.share.ShareUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareCardTest {
    private val info = CardInfo(
        pizzaNo = 23, bakeMs = 88_000, whenMs = 1_763_000_000_000, hydration = 70f, salt = 4f, yeast = 0.5f,
        preheatMin = 30, balls = 4, ballWeight = 250,
    )

    @Test fun everyDesignInEveryFormatRendersAtSocialMediaSize() {
        val landscapePhoto = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        val portraitPhoto = Bitmap.createBitmap(900, 1600, Bitmap.Config.ARGB_8888)
        for (photo in listOf(landscapePhoto, portraitPhoto)) {
            for (d in Design.entries) for (f in Format.entries) {
                val card = CardRenderer.render(photo, d, f, info)
                assertEquals("$d $f width", f.w, card.width)
                assertEquals("$d $f height", f.h, card.height)
            }
        }
        assertEquals(1080 to 1350, Format.FEED.w to Format.FEED.h)
        assertEquals(1080 to 1920, Format.STORY.w to Format.STORY.h)
    }

    @Test fun captionCarriesTheBakeTheLinkAndTheHashtag() {
        val c = ShareUtils.caption(com.vibepizzaiolo.share.CaptionInfo(info), reel = false)
        assertTrue(!c.contains("Sound on"))
        assertTrue(c.contains("Pizza nº 23"))
        assertTrue(c.contains("1:28"))
        assertTrue(c.contains("70% hydration"))
        assertTrue(c.contains("https://"))
        assertTrue(c.contains("#VibePizzaiolo"))
    }

    @Test fun numbersOnTheCard() {
        assertEquals("1:28", CardRenderer.clock(88_000))
        assertEquals("0.5%", CardRenderer.pct(0.5f))
        assertEquals("70%", CardRenderer.pct(70f))
    }
}
