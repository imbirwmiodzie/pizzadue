package com.vibepizzaiolo.video

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes a piece of a file's sound into mono samples at [Soundtrack.RATE], so the reel's sound can
 * be mixed into one WAV by the app instead of by Media3 (whose audio mixing across items crashed
 * on some phones).
 */
object AudioClip {

    /** The sound of [uri] from [fromMs] to [toMs], or null if it has none or cannot be read. */
    fun fromUri(context: Context, uri: Uri, fromMs: Long, toMs: Long): FloatArray? =
        decode(fromMs, toMs) { it.setDataSource(context, uri, null) }

    /** A whole raw resource (the voice clip), or null if it cannot be read. */
    fun fromRaw(context: Context, resId: Int): FloatArray? =
        context.resources.openRawResourceFd(resId)?.use { fd ->
            decode(0L, null) { it.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length) }
        }

    private fun decode(fromMs: Long, toMs: Long?, open: (MediaExtractor) -> Unit): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            open(extractor)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val c = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(format, null, null, 0)
            c.start()
            if (fromMs > 0) extractor.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            val fromUs = fromMs * 1000
            val toUs = toMs?.let { it * 1000 } ?: Long.MAX_VALUE
            val out = Samples()
            var outStartUs = -1L
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val deadline = System.currentTimeMillis() + 15_000

            while (System.currentTimeMillis() < deadline) {
                if (!inputDone) {
                    val i = c.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(c.getInputBuffer(i)!!, 0)
                        val t = extractor.sampleTime
                        if (n < 0 || t > toUs) {
                            c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(i, 0, n, t, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = c.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = c.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) encoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else if (o >= 0) {
                    val buf = c.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    val frames = info.size / (bytesPerSample * channels)
                    for (k in 0 until frames) {
                        var sum = 0f
                        for (ch in 0 until channels) {
                            sum += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buf.float else buf.short / 32768f
                        }
                        val t = info.presentationTimeUs + k * 1_000_000L / rate
                        if (t in fromUs until toUs) {
                            if (outStartUs < 0) outStartUs = t
                            out.add(sum / channels)
                        }
                    }
                    c.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            if (out.size == 0) return null
            // a gap between where we asked to start and the first decoded sample stays silent
            val lead = ((outStartUs - fromUs).coerceAtLeast(0) * rate / 1_000_000).toInt()
            return resample(FloatArray(lead) + out.toArray(), rate, Soundtrack.RATE)
        } catch (e: Exception) {
            android.util.Log.w("AudioClip", "Could not decode the sound", e)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /** Linear resampling, enough for a crunch and a voice. */
    internal fun resample(src: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || src.isEmpty()) return src
        val n = (src.size.toLong() * to / from).toInt()
        return FloatArray(n) { i ->
            val x = i.toDouble() * from / to
            val j = x.toInt().coerceAtMost(src.size - 1)
            val f = (x - j).toFloat()
            val next = src[(j + 1).coerceAtMost(src.size - 1)]
            src[j] * (1 - f) + next * f
        }
    }

    private class Samples {
        private var data = FloatArray(1 shl 16)
        var size = 0
            private set

        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
