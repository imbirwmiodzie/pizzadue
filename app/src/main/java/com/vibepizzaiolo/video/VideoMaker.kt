package com.vibepizzaiolo.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.util.Log
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.vibepizzaiolo.R
import com.vibepizzaiolo.avatar.Pizzaiolo
import com.vibepizzaiolo.share.CardInfo
import com.vibepizzaiolo.share.CardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Which parts of the recording go into the reel, in milliseconds of the recording. */
data class ReelPlan(val top: LongRange, val around: List<LongRange>, val rim: LongRange)

/**
 * Cuts the guided recording into a vertical reel: from above, around (two quick cuts), the rim
 * with its real crunch, the quality seal stamped on, and an ending with the pen-and-ink pizzaiolo
 * (with the user's own face if there is one) and "La pizza è pronta!".
 * Built on the phone with Media3 Transformer.
 */
@OptIn(markerClass = [UnstableApi::class])
class VideoMaker(private val context: Context) {

    companion object {
        const val W = 1080
        const val H = 1920
        const val STAMP_MS = 2600L
        const val STAMP_HIT_MS = 550L
        const val OUTRO_MS = 3800L
        const val VOICE_AT_MS = 700L
        const val MIN_RECORDING_MS = 5_000L

        /**
         * The guide asks for: ~2 s from above, ~8 s around, ~4 s on the rim with the press test.
         * Long enough recordings are cut on that rhythm; short ones by proportion.
         */
        fun plan(durationMs: Long): ReelPlan? {
            if (durationMs < MIN_RECORDING_MS) return null
            val d = durationMs
            val top: LongRange
            val orbit: LongRange
            val rim: LongRange
            if (d >= 12_000) {
                top = 300L..2_300L
                orbit = 2_600L..(d - 4_600)
                rim = (d - 4_300)..(d - 300)
            } else {
                top = 0L..(d * 18 / 100)
                orbit = (d * 20 / 100)..(d * 66 / 100)
                rim = (d * 70 / 100)..d
            }
            val len = orbit.last - orbit.first
            val cut = min(1_750L, len * 45 / 100)
            val a1 = orbit.first + (len - cut) * 25 / 100
            val a2 = orbit.first + (len - cut) * 75 / 100
            return ReelPlan(top, listOf(a1..(a1 + cut), a2..(a2 + cut)), rim)
        }

        /**
         * What to show when the reel fails. Release builds are obfuscated, so the exception's class
         * name is useless ("I"); Media3's error code and the root cause's message survive and say
         * what actually went wrong.
         */
        fun explain(e: Throwable): String {
            if (e is IllegalArgumentException && e.message != null) return e.message!!
            val code = (e as? ExportException)?.let {
                if (it.errorCode == ExportException.ERROR_CODE_UNSPECIFIED) "ERROR_CODE_UNSPECIFIED" else it.errorCodeName
            } ?: e.javaClass.name
            val hint = when ((e as? ExportException)?.errorCode ?: 0) {
                in 2000..2999 -> "The recording could not be opened. Try recording again."
                in 3000..5999 -> "This phone could not convert the video. In the camera settings choose 1080p at 30 fps with HDR off, then record again."
                else -> "Try recording again."
            }
            // the innermost cause says what broke; where it was thrown helps when it has no message
            val root = generateSequence(e) { it.cause }.last()
            val at = root.stackTrace.firstOrNull { f -> !f.className.endsWith("Assertions") && !f.className.endsWith("Preconditions") }?.let { " at ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" } ?: ""
            val detail = if (root === e) (root.message?.take(160)?.let { ": $it" } ?: "")
            else ": ${root.javaClass.simpleName}${root.message?.take(160)?.let { " $it" } ?: ""}$at"
            return "Could not make the reel. $hint\n($code$detail)"
        }
    }

    private val dir = File(context.cacheDir, "videos").apply { mkdirs() }

    /**
     * A still picture shown for [ms]. Media3 1.4 takes an image's length from the EditedMediaItem
     * (setDurationUs), not from MediaItem.setImageDurationMs; without it the image loader fails a
     * bare check and the whole reel fails with ERROR_CODE_UNSPECIFIED.
     */
    internal fun stillItem(uri: Uri, ms: Long, effects: Effects = Effects.EMPTY): EditedMediaItem {
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMimeType(MimeTypes.IMAGE_PNG)
            .setImageDurationMs(ms)
            .build()
        return EditedMediaItem.Builder(item)
            .setDurationUs(ms * 1000)
            .setFrameRate(30)
            .setEffects(effects)
            .build()
    }

    /** Makes the reel; [onProgress] gets 0–100. Returns the finished MP4. */
    suspend fun make(video: Uri, info: CardInfo, face: Bitmap?, onProgress: (Int) -> Unit): File {
        val prep = withContext(Dispatchers.IO) { prepare(video, info) }
        val out = File(dir, "vibepizzaiolo_${System.currentTimeMillis()}.mp4")
        dir.listFiles { f -> f.name.startsWith("vibepizzaiolo_") && f.name.endsWith(".mp4") }?.forEach { it.delete() }
        val composition = compose(video, prep, info, face)
        return withContext(Dispatchers.Main) { export(composition, out, onProgress) }
    }

    private class Prepared(val plan: ReelPlan, val stampImage: File, val outroImage: File, val soundtrack: File)

    private fun prepare(video: Uri, info: CardInfo): Prepared {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, video)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val plan = plan(duration) ?: throw IllegalArgumentException("The recording is too short. Film at least 6 seconds.")
            val frame = upright(mmr, mmr.getFrameAtTime((plan.top.first + plan.top.last) / 2 * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                ?: throw IllegalArgumentException("Could not read the recording.")

            // still for the stamp: the top view, darkened so the seal stands out
            val stamp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            Canvas(stamp).apply {
                cover(this, frame)
                drawColor(Color.argb(95, 0, 0, 0))
            }
            val stampFile = File(dir, "stamp.png").also { f -> f.outputStream().use { stamp.compress(Bitmap.CompressFormat.PNG, 100, it) } }

            // ending background: warm glow on the app's dark colour
            val outro = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            Canvas(outro).apply {
                drawColor(0xFF1A1E23.toInt())
                drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                    shader = RadialGradient(W / 2f, H * 0.62f, H * 0.6f, 0xFF4A2A14.toInt(), 0xFF1A1E23.toInt(), Shader.TileMode.CLAMP)
                })
            }
            val outroFile = File(dir, "outro.png").also { f -> f.outputStream().use { outro.compress(Bitmap.CompressFormat.PNG, 100, it) } }

            val beforeRimMs = (plan.top.last - plan.top.first) + plan.around.sumOf { it.last - it.first }
            val clipsMs = beforeRimMs + (plan.rim.last - plan.rim.first)
            val layers = listOfNotNull(
                AudioClip.fromUri(context, video, plan.rim.first, plan.rim.last)?.let { Soundtrack.Layer(beforeRimMs, it) },
                AudioClip.fromRaw(context, R.raw.voice_pronta)?.let { Soundtrack.Layer(clipsMs + STAMP_MS + VOICE_AT_MS, it) },
            )
            val soundFile = File(dir, "soundtrack.wav")
            Soundtrack.write(soundFile, lengthMs = clipsMs + STAMP_MS + OUTRO_MS, stampAtMs = clipsMs + STAMP_HIT_MS, layers = layers)
            return Prepared(plan, stampFile, outroFile, soundFile)
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** Some phones return frames without the recording's rotation applied. */
    private fun upright(mmr: MediaMetadataRetriever, frame: Bitmap?): Bitmap? {
        frame ?: return null
        val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val rawW = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: frame.width
        val rawH = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: frame.height
        val wantLandscape = if (rot % 180 == 0) rawW > rawH else rawH > rawW
        if ((frame.width > frame.height) == wantLandscape || rot == 0) return frame
        return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
    }

    private fun compose(video: Uri, prep: Prepared, info: CardInfo, face: Bitmap?): Composition {
        val pool = OverlayPool(W, H)
        val presentation = Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)

        fun clip(range: LongRange, draw: (Canvas, Float) -> Unit): EditedMediaItem {
            val item = MediaItem.Builder()
                .setUri(video)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(range.first)
                        .setEndPositionMs(range.last)
                        .build()
                )
                .build()
            return EditedMediaItem.Builder(item)
                .setRemoveAudio(true)
                .setEffects(Effects(ImmutableList.of(), ImmutableList.of<Effect>(presentation, OverlayEffect(ImmutableList.of(FrameOverlay(pool, draw))))))
                .build()
        }

        fun still(file: File, ms: Long, draw: (Canvas, Float) -> Unit): EditedMediaItem =
            stillItem(Uri.fromFile(file), ms, Effects(ImmutableList.of(), ImmutableList.of<Effect>(presentation, OverlayEffect(ImmutableList.of(FrameOverlay(pool, draw))))))

        val p = prep.plan
        val clock = CardRenderer.clock(info.bakeMs)
        val videoItems = ImmutableList.of(
            clip(p.top) { c, t -> brand(c, t); label(c, t, "DALL'ALTO", "Pizza nº ${info.pizzaNo}") },
            clip(p.around[0]) { c, t -> brand(c, t); label(c, t, "TUTTO INTORNO", null) },
            clip(p.around[1]) { c, t -> brand(c, t); label(c, t, "TUTTO INTORNO", null) },
            clip(p.rim) { c, t -> brand(c, t); label(c, t, "IL CORNICIONE", "$clock in forno · croccante") },
            still(prep.stampImage, STAMP_MS) { c, t -> stamp(c, t, info) },
            still(prep.outroImage, OUTRO_MS) { c, t -> outro(c, t, info, face) },
        )
        // the pictures carry no sound; all of it (crunch, thud, voice) is one WAV the app mixed itself
        val sound = ImmutableList.of(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(prep.soundtrack))).build())
        return Composition.Builder(ImmutableList.of(EditedMediaItemSequence(videoItems), EditedMediaItemSequence(sound)))
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    private suspend fun export(composition: Composition, out: File, onProgress: (Int) -> Unit): File = coroutineScope {
        lateinit var transformer: Transformer
        val progress = launch {
            val holder = ProgressHolder()
            while (true) {
                delay(300)
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
            }
        }
        try {
            suspendCancellableCoroutine { cont ->
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (cont.isActive) cont.resume(out)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            Log.e("VideoMaker", "Reel export failed: ${exportException.errorCodeName}", exportException)
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                transformer.start(composition, out.absolutePath)
            }
        } finally {
            progress.cancel()
        }
    }

    // ---------------- overlays ----------------

    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private fun paint(size: Float, color: Int, face: Typeface = bold, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = face; textAlign = align }

    private fun fade(t: Float, start: Float = 0f, len: Float = 0.3f) = ((t - start) / len).coerceIn(0f, 1f)

    /** Logo and name, top left, below the area Stories cover with their own buttons. */
    private fun brand(c: Canvas, t: Float) {
        val a = (255 * fade(t)).toInt()
        c.saveLayerAlpha(null, a)
        CardRenderer.drawLogo(c, 64f, 262f, 70f, 0xFFFFB020.toInt())
        c.drawText("VibePizzaiolo", 150f, 318f, paint(50f, Color.WHITE).apply { setShadowLayer(8f, 0f, 2f, 0x99000000.toInt()) })
        c.restore()
    }

    /** The scene's name, low on the screen, sliding in. */
    private fun label(c: Canvas, t: Float, title: String, sub: String?) {
        val k = fade(t, 0.1f, 0.35f)
        if (k <= 0f) return
        val x = 64f - (1 - k) * 120f
        val y = H - 470f
        val titleP = paint(64f, Color.WHITE)
        val w = titleP.measureText(title)
        c.saveLayerAlpha(null, (255 * k).toInt())
        c.drawRoundRect(RectF(x - 28f, y - 82f, x + w + 28f, y + 30f), 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB0000000.toInt() })
        c.drawText(title, x, y, titleP)
        if (sub != null) c.drawText(sub, x, y + 76f, paint(36f, 0xFFFFB020.toInt(), monoBold).apply { setShadowLayer(8f, 0f, 2f, 0xCC000000.toInt()) })
        c.restore()
    }

    /** The quality seal falls onto the pizza and lands with a bounce. */
    private fun stamp(c: Canvas, t: Float, info: CardInfo) {
        val hit = STAMP_HIT_MS / 1000f
        val cx = W / 2f
        val cy = H * 0.46f
        val r = 300f
        val scale: Float
        val turn: Float
        val alpha: Float
        if (t < hit) {
            val e = (t / hit) * (t / hit)
            scale = 3.2f - 2.2f * e
            turn = -35f * (1 - e)
            alpha = 0.25f + 0.75f * (t / hit)
        } else {
            val u = ((t - hit) / 0.3f).coerceIn(0f, 1f)
            scale = 1f + 0.09f * sin(u * Math.PI.toFloat()) * (1 - u)
            turn = 0f
            alpha = 1f
        }
        // dust ring when it lands
        if (t >= hit && t < hit + 0.55f) {
            val u = (t - hit) / 0.55f
            c.drawCircle(cx, cy, r * (1.02f + 0.9f * u), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 34f * (1 - u); color = Color.argb((190 * (1 - u)).toInt(), 255, 244, 220)
            })
        }
        c.saveLayerAlpha(null, (255 * alpha).toInt())
        c.scale(scale, scale, cx, cy)
        c.rotate(turn, cx, cy)
        CardRenderer.qualitySeal(c, cx, cy, r, info)
        c.restore()
        val k = fade(t, hit + 0.35f, 0.4f)
        if (k > 0f) {
            c.saveLayerAlpha(null, (255 * k).toInt())
            c.drawText("Pizza nº ${info.pizzaNo} · ${CardRenderer.clock(info.bakeMs)} in forno", cx, cy + r * 1.75f,
                paint(46f, Color.WHITE, monoBold, Paint.Align.CENTER).apply { setShadowLayer(10f, 0f, 2f, 0xCC000000.toInt()) })
            c.restore()
        }
    }

    /** The ending: the app's name and the pizzaiolo, who waves and calls the pizza ready. */
    private fun outro(c: Canvas, t: Float, info: CardInfo, face: Bitmap?) {
        val cx = W / 2f
        val k = fade(t, 0f, 0.4f)
        c.saveLayerAlpha(null, (255 * k).toInt())
        CardRenderer.drawLogo(c, cx - 70f, 270f, 140f, 0xFFFFB020.toInt())
        c.drawText("VibePizzaiolo", cx, 520f, paint(104f, 0xFFF3EFE6.toInt(), bold, Paint.Align.CENTER))
        c.drawText("The voice pizza timer · say “ciao pizza”", cx, 590f, paint(38f, 0xFFFFB020.toInt(), monoBold, Paint.Align.CENTER))
        c.restore()

        // pizzaiolo slides up and waves
        val up = 1 - fade(t, 0.15f, 0.55f).let { 1 - (1 - it) * (1 - it) }
        val size = 840f // the toque clears the speech bubble
        val bottom = H - 300f + up * 900f
        Pizzaiolo.draw(c, cx - 40f, bottom, size, t, face)

        // speech bubble, timed with the voice
        val b = fade(t, VOICE_AT_MS / 1000f, 0.25f)
        if (b > 0f) {
            val text = "La pizza è pronta!"
            val p = paint(52f, 0xFF1A1E23.toInt(), bold, Paint.Align.CENTER)
            val bw = p.measureText(text) + 70f
            val bx = W - 40f - bw / 2
            val by = 690f
            c.saveLayerAlpha(null, (255 * b).toInt())
            val bubble = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            c.drawRoundRect(RectF(bx - bw / 2, by - 70f, bx + bw / 2, by + 40f), 40f, 40f, bubble)
            val tail = android.graphics.Path().apply {
                moveTo(bx - bw * 0.25f, by + 30f); lineTo(bx - bw * 0.42f, by + 110f); lineTo(bx - bw * 0.05f, by + 30f); close()
            }
            c.drawPath(tail, bubble)
            c.drawText(text, bx, by, p)
            c.restore()
        }

        val f = fade(t, 1.2f, 0.4f)
        if (f > 0f) {
            c.saveLayerAlpha(null, (255 * f).toInt())
            c.drawText("Pizza nº ${info.pizzaNo} · fatta a mano con amore", cx, H - 230f, paint(36f, 0xFFF3EFE6.toInt(), monoBold, Paint.Align.CENTER))
            c.drawText("Get the app: link in the post", cx, H - 175f, paint(34f, 0xFF9AA3AD.toInt(), monoBold, Paint.Align.CENTER))
            c.restore()
        }
    }

    private fun cover(c: Canvas, photo: Bitmap) {
        val scale = max(W.toFloat() / photo.width, H.toFloat() / photo.height)
        val sw = (W / scale).toInt().coerceAtMost(photo.width)
        val sh = (H / scale).toInt().coerceAtMost(photo.height)
        val sx = (photo.width - sw) / 2
        val sy = (photo.height - sh) / 2
        c.drawBitmap(photo, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

/** Two full-frame bitmaps shared by all overlays (the reel's parts are drawn one after another). */
private class OverlayPool(private val w: Int, private val h: Int) {
    private val bitmaps = arrayOfNulls<Bitmap>(2)
    private var i = 0
    fun next(): Bitmap {
        i = 1 - i
        return bitmaps[i] ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmaps[i] = it }
    }
}

/** A full-frame overlay drawn fresh for every video frame; [draw] gets seconds since the part began. */
@OptIn(markerClass = [UnstableApi::class])
private class FrameOverlay(private val pool: OverlayPool, private val draw: (Canvas, Float) -> Unit) : BitmapOverlay() {
    private var startUs = Long.MIN_VALUE

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (startUs == Long.MIN_VALUE) startUs = presentationTimeUs
        val t = ((presentationTimeUs - startUs) / 1_000_000.0).toFloat().coerceAtLeast(0f)
        val b = pool.next()
        b.eraseColor(Color.TRANSPARENT)
        draw(Canvas(b), t)
        return b
    }
}
