package com.vibepizzaiolo.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The finished reel and its cover picture for the profile grid. */
data class Reel(val video: File, val cover: File)

/**
 * Cuts the guided recording into a vertical reel (see [ReelPlanner] for the order of the shots and
 * [ReelOverlays] for what is drawn on them). The sound is mixed by the app into one WAV: the
 * recording's own sound under every shot, the crunch loud, the seal's thud and the voice.
 * Built on the phone with Media3 Transformer.
 */
@OptIn(markerClass = [UnstableApi::class])
class VideoMaker(private val context: Context) {

    companion object {
        const val W = 1080
        const val H = 1920
        const val STAMP_HIT_MS = 550L
        const val VOICE_AT_MS = 400L

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

        /** [ms] of mono samples at [Soundtrack.RATE], from [fromMs] of [all] (which starts at 0). */
        internal fun slice(all: FloatArray, fromMs: Long, toMs: Long): FloatArray {
            val a = (fromMs * Soundtrack.RATE / 1000).toInt().coerceIn(0, all.size)
            val b = (toMs * Soundtrack.RATE / 1000).toInt().coerceIn(a, all.size)
            val out = all.copyOfRange(a, b)
            // 10 ms in and out, so cuts do not click
            val ramp = minOf(Soundtrack.RATE / 100, out.size / 2)
            for (i in 0 until ramp) {
                val k = i.toFloat() / ramp
                out[i] *= k
                out[out.size - 1 - i] *= k
            }
            return out
        }

        /**
         * The reel's sound, layer by layer, for [shots] in order: the recording's own sound
         * ([audio], the whole recording) under each pizza shot, louder on the crunch; an oven's
         * roar and a tick under the bake cam's shots; the voice at the ending. Also returns
         * where the seal lands, if it does.
         */
        internal fun layers(shots: List<Shot>, audio: FloatArray?, style: ReelStyle, voice: FloatArray?): Pair<List<Soundtrack.Layer>, Long?> {
            val out = mutableListOf<Soundtrack.Layer>()
            val norm = audio?.let { Soundtrack.normalizer(it) } ?: 1f
            val bed = if (style == ReelStyle.ASMR) 0.75f else 0.45f
            var at = 0L
            var stampAt: Long? = null
            for (s in shots) {
                when (s.source) {
                    Source.PIZZA -> if (audio != null) {
                        val loud = s.kind == Kind.HOOK || s.kind == Kind.RIM
                        out += Soundtrack.Layer(at, slice(audio, s.range.first, s.range.last), norm * (if (loud) 1.1f else bed))
                    }
                    Source.BAKECAM -> {
                        out += Soundtrack.Layer(at, Soundtrack.rumble(s.lengthMs, seed = at), 0.7f)
                        if (s.kind == Kind.BAKE) out += Soundtrack.Layer(at, Soundtrack.tick())
                    }
                }
                if (s.kind == Kind.STAMP) stampAt = at + STAMP_HIT_MS
                val ending = s.endingFromMs
                if (ending != null && voice != null && style.voice) out += Soundtrack.Layer(at + ending + VOICE_AT_MS, voice)
                at += s.lengthMs
            }
            return out to stampAt
        }
    }

    private val dir = File(context.cacheDir, "videos").apply { mkdirs() }

    /** Makes the reel; [onProgress] gets 0–100. */
    suspend fun make(video: Uri, info: ReelInfo, face: Bitmap?, bakeCam: BakeCamClip?, onProgress: (Int) -> Unit): Reel {
        val overlays = ReelOverlays(info, face)
        val prep = withContext(Dispatchers.IO) { prepare(video, info, overlays, bakeCam) }
        val out = File(dir, "vibepizzaiolo_${System.currentTimeMillis()}.mp4")
        dir.listFiles { f -> f.name.startsWith("vibepizzaiolo_") && f.name.endsWith(".mp4") }?.forEach { it.delete() }
        val composition = compose(video, bakeCam, prep.shots, overlays, prep.soundtrack)
        val file = withContext(Dispatchers.Main) { export(composition, out, onProgress) }
        return Reel(file, prep.cover)
    }

    private class Prepared(val shots: List<Shot>, val cover: File, val soundtrack: File)

    private fun prepare(video: Uri, info: ReelInfo, overlays: ReelOverlays, bakeCam: BakeCamClip?): Prepared {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, video)
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val parts = ReelPlanner.parts(duration) ?: throw IllegalArgumentException("The recording is too short. Film at least 6 seconds.")
            val audio = AudioClip.fromUri(context, video, 0L, duration)
            val crunchAt = audio?.let { ReelPlanner.loudest(slice(it, parts.rim.first, parts.rim.last), Soundtrack.RATE, parts.rim.first) }
            val shots = ReelPlanner.plan(duration, crunchAt, info.seal, bakeCam)!!

            // the cover: the pizza from above with the seal and the title
            val frame = upright(mmr, mmr.getFrameAtTime((parts.top.first + parts.top.last) / 2 * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                ?: throw IllegalArgumentException("Could not read the recording.")
            val cover = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            overlays.cover(Canvas(cover), frame)
            val coverFile = File(dir, "cover.jpg").also { f -> f.outputStream().use { cover.compress(Bitmap.CompressFormat.JPEG, 92, it) } }

            val voice = if (info.style.voice) AudioClip.fromRaw(context, R.raw.voice_pronta) else null
            val (layers, stampAt) = layers(shots, audio, info.style, voice)
            val soundFile = File(dir, "soundtrack.wav")
            Soundtrack.write(soundFile, lengthMs = shots.sumOf { it.lengthMs }, stampAtMs = stampAt, layers = layers)
            return Prepared(shots, coverFile, soundFile)
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

    private fun compose(video: Uri, bakeCam: BakeCamClip?, shots: List<Shot>, overlays: ReelOverlays, soundtrack: File): Composition {
        val pool = OverlayPool(W, H)
        val presentation = Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)
        val camUri = bakeCam?.let { Uri.fromFile(it.file) }
        val items = shots.mapIndexed { i, shot ->
            val last = i == shots.lastIndex
            val item = MediaItem.Builder()
                .setUri(if (shot.source == Source.BAKECAM) camUri!! else video)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(shot.range.first)
                        .setEndPositionMs(shot.range.last)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(item)
                .setRemoveAudio(true)
                .setEffects(Effects(ImmutableList.of(), ImmutableList.of<Effect>(presentation, OverlayEffect(ImmutableList.of(FrameOverlay(pool) { c, t -> overlays.draw(c, shot, t, last) })))))
                .build()
        }
        // the pictures carry no sound; all of it is one WAV the app mixed itself
        val sound = ImmutableList.of(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(soundtrack))).build())
        return Composition.Builder(ImmutableList.of(EditedMediaItemSequence(ImmutableList.copyOf(items)), EditedMediaItemSequence(sound)))
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
