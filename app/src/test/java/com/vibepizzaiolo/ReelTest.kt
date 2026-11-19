package com.vibepizzaiolo

import android.graphics.Bitmap
import android.graphics.Canvas
import com.vibepizzaiolo.avatar.Cartoonizer
import com.vibepizzaiolo.avatar.Pizzaiolo
import com.vibepizzaiolo.video.AudioClip
import com.vibepizzaiolo.video.Soundtrack
import com.vibepizzaiolo.video.VideoMaker
import com.vibepizzaiolo.video.BakeCamClip
import com.vibepizzaiolo.video.Kind
import com.vibepizzaiolo.video.NightInfo
import com.vibepizzaiolo.video.ReelInfo
import com.vibepizzaiolo.video.ReelOverlays
import com.vibepizzaiolo.video.ReelPlanner
import com.vibepizzaiolo.video.ReelStyle
import com.vibepizzaiolo.video.Shot
import com.vibepizzaiolo.video.Source
import com.vibepizzaiolo.settings.SealText
import com.vibepizzaiolo.share.CaptionInfo
import com.vibepizzaiolo.share.CardInfo
import com.vibepizzaiolo.share.ShareUtils
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
    private val card = CardInfo(pizzaNo = 47, bakeMs = 92_000, whenMs = 0, hydration = 70f, salt = 3f, yeast = 0.2f, preheatMin = 30, balls = 4, ballWeight = 250)

    private fun assertInside(shots: List<Shot>, durationMs: Long, cam: BakeCamClip? = null) {
        for (s in shots) {
            val d = if (s.source == Source.BAKECAM) cam!!.durationMs else durationMs
            assertTrue("$s inside the recording", s.range.first >= 0 && s.range.last <= d)
            assertTrue("$s has a length", s.lengthMs > 0)
        }
    }

    @Test fun fiveStepRecordingOpensOnTheCrunchAndEndsOnTheSlice() {
        val d = 21_500L
        val shots = ReelPlanner.plan(d, crunchAtMs = 12_000)!!
        assertEquals(listOf(Kind.HOOK, Kind.TOP, Kind.AROUND, Kind.AROUND, Kind.RIM, Kind.UNDER, Kind.STAMP, Kind.SLICE), shots.map { it.kind })
        assertInside(shots, d)
        // the hook holds the loudest crunch
        assertTrue(12_000L in shots.first().range)
        assertEquals(ReelPlanner.HOOK_MS, shots.first().lengthMs)
        // the ending is the last part of the slice
        val last = shots.last()
        assertEquals(last.lengthMs - ReelPlanner.ENDING_MS, last.endingFromMs)
        // short enough for a reel that gets replayed
        assertTrue(shots.sumOf { it.lengthMs } in 12_000L..17_000L)
    }

    @Test fun withoutTheSealThereIsNoStampShot() {
        val shots = ReelPlanner.plan(21_500, seal = false)!!
        assertTrue(shots.none { it.kind == Kind.STAMP })
    }

    @Test fun shortRecordingIsCutIntoThreeStepsAndEndsFromAbove() {
        for (d in listOf(8_000L, 15_000L)) {
            val shots = ReelPlanner.plan(d)!!
            assertEquals(Kind.HOOK, shots.first().kind)
            assertEquals(Kind.ENDING, shots.last().kind)
            assertEquals(0L, shots.last().endingFromMs)
            assertTrue(shots.none { it.kind == Kind.UNDER || it.kind == Kind.SLICE })
            assertInside(shots, d)
        }
    }

    @Test fun tooShortRecordingIsRefused() {
        assertNull(ReelPlanner.plan(3_000))
    }

    @Test fun theBakeCamAddsLaunchTimeLapseAndPullOut() {
        val cam = BakeCamClip(java.io.File("bakecam.mp4"), durationMs = 110_000, launchMs = 8_000, outMs = 100_000)
        val shots = ReelPlanner.plan(21_500, bakeCam = cam)!!
        val fromCam = shots.filter { it.source == Source.BAKECAM }
        assertEquals(listOf(Kind.LAUNCH) + List(ReelPlanner.BAKE_PIECES) { Kind.BAKE } + Kind.PULL, fromCam.map { it.kind })
        assertTrue(8_000L in fromCam.first().range)
        assertTrue(100_000L in fromCam.last().range)
        // the time-lapse moves forward through the bake
        val bake = fromCam.filter { it.kind == Kind.BAKE }
        assertTrue(bake.zipWithNext().all { (a, b) -> a.range.last <= b.range.first })
        assertEquals(1, shots.count { it.kind == Kind.AROUND })
        assertInside(shots, 21_500, cam)
        // a bake too short to be a bake is left out
        val tiny = cam.copy(outMs = 12_000)
        assertTrue(ReelPlanner.plan(21_500, bakeCam = tiny)!!.none { it.source == Source.BAKECAM })
    }

    @Test fun loudestFindsTheCrunch() {
        val rate = Soundtrack.RATE
        val pcm = FloatArray(rate * 2) { 0.01f }
        for (i in (rate * 1.3).toInt() until (rate * 1.35).toInt()) pcm[i] = 0.9f
        val at = ReelPlanner.loudest(pcm, rate, fromMs = 10_000)!!
        assertTrue(at in 11_280L..11_370L)
        assertNull(ReelPlanner.loudest(FloatArray(rate), rate, 0))
    }

    @Test fun everyStyleDrawsEveryShotWithoutTrouble() {
        val frame = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val c = Canvas(frame)
        val cam = BakeCamClip(java.io.File("bakecam.mp4"), 110_000, 8_000, 100_000)
        val shots = ReelPlanner.plan(21_500, bakeCam = cam)!! + ReelPlanner.plan(9_000)!!.last()
        val night = NightInfo("Serata da Marco", 12, listOf("Anna", "Luca", "Marta", "Paolo", "Giulia", "Sofia", "Enzo"), "Diavola", 5)
        for (style in ReelStyle.entries) for (n in listOf(null, night)) {
            val o = ReelOverlays(ReelInfo(card, style, handle = "pizza_di_marco", ovenC = 450, fermentH = 48, seriesGoal = 100, night = n), null)
            for (s in shots) for (t in listOf(0f, 0.3f, 1f, s.lengthMs / 1000f - 0.1f)) o.draw(c, s, t, last = s == shots.last())
            o.cover(c, Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888))
        }
    }

    @Test fun theReelSaysYourNumbersAndYourName() {
        val info = ReelInfo(card, handle = "pizza_di_marco", ovenC = 450, fermentH = 48, seriesGoal = 100)
        assertEquals("@pizza_di_marco", info.handleText)
        assertEquals("Marco R.", info.copy(handle = "Marco R.").handleText)
        assertEquals("Pizza nº 47 / 100", info.pizzaTitle)
        assertEquals("Pizza nº 47", info.copy(seriesGoal = 40).pizzaTitle)
        val o = ReelOverlays(info, null)
        assertEquals(listOf("70% idratazione", "48 h lievitazione", "450 °C", "1:32 in forno"), o.stats())
        assertEquals(listOf("70% idratazione", "1:32 in forno"), ReelOverlays(ReelInfo(card), null).stats())
        assertTrue(info.seal)
        assertTrue(!info.copy(sealText = SealText.NONE).seal)
        assertTrue(!info.copy(style = ReelStyle.ASMR).seal)
    }

    @Test fun thanksWrapsTheGuestsOntoAtMostThreeLines() {
        val o = ReelOverlays(ReelInfo(card), null)
        val p = android.graphics.Paint().apply { textSize = 40f }
        assertEquals(listOf("Grazie a Anna e Luca"), o.thanks(listOf("Anna", "Luca"), p, 10_000f))
        val many = (1..30).map { "Guest$it" }
        val lines = o.thanks(many, p, 300f)
        assertTrue(lines.size <= 3)
    }

    @Test fun soundLayersFollowTheShots() {
        val shots = ReelPlanner.plan(21_500)!!
        val audio = FloatArray(Soundtrack.RATE * 22) { 0.1f }
        val voice = FloatArray(100) { 0.2f }
        val (layers, stampAt) = VideoMaker.layers(shots, audio, ReelStyle.STATS, voice)
        val stamp = shots.indexOfFirst { it.kind == Kind.STAMP }
        assertEquals(shots.take(stamp).sumOf { it.lengthMs } + VideoMaker.STAMP_HIT_MS, stampAt)
        // every shot has the recording's sound, plus the voice at the ending
        assertEquals(shots.size + 1, layers.size)
        val total = shots.sumOf { it.lengthMs }
        assertEquals(total - ReelPlanner.ENDING_MS + VideoMaker.VOICE_AT_MS, layers.last().atMs)
        // the crunch is louder than the rest
        assertTrue(layers.first().gain > layers[1].gain)
        // quiet styles: no voice
        assertEquals(shots.size, VideoMaker.layers(shots, audio, ReelStyle.ASMR, voice).first.size)
        // the bake cam's shots get the oven's roar
        val cam = BakeCamClip(java.io.File("bakecam.mp4"), 110_000, 8_000, 100_000)
        val withCam = ReelPlanner.plan(21_500, bakeCam = cam)!!
        assertTrue(VideoMaker.layers(withCam, null, ReelStyle.STATS, null).first.isNotEmpty())
    }

    @Test fun captionHasAHookNumbersAndSearchedHashtags() {
        val text = ShareUtils.caption(CaptionInfo(card, ovenC = 450, fermentH = 48, seriesGoal = 100), reel = true)
        assertTrue(text.startsWith("Pizza nº 47 of 100"))
        assertTrue(text.contains("1:32 in the oven at 450 °C"))
        assertTrue(text.contains("48 h ferment"))
        assertTrue(text.contains("Sound on"))
        assertTrue(text.contains("#pizzanapoletana") && text.contains("#100pizzas"))
        assertTrue(text.trimEnd().endsWith("#VibePizzaiolo"))
        assertTrue(!text.contains("#vai"))
        val nightText = ShareUtils.caption(CaptionInfo(card, night = NightInfo("Serata", 12, listOf("A", "B"), "Diavola", 5)), reel = true)
        assertTrue(nightText.startsWith("Pizza night: 12 pizzas for 2 friends"))
        assertTrue(nightText.contains("#pizzanight"))
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

}
