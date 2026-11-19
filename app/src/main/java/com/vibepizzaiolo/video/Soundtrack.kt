package com.vibepizzaiolo.video

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Writes the reel's whole sound as one WAV file: the recording's own sound under every shot, the
 * real crunch of the rim loud and the voice clip ([layers], each placed at its time), plus a
 * whoosh and a heavy "thud" where the quality seal lands (if it does).
 * Mixing it here, and handing Media3 a single audio file, keeps Media3 from joining sounds of
 * different formats across items, which crashed on some phones.
 */
object Soundtrack {
    const val RATE = 44_100
    private const val KNEE = 0.8f

    /** A sound to mix in: mono samples at [RATE], starting at [atMs] into the reel, times [gain]. */
    class Layer(val atMs: Long, val samples: FloatArray, val gain: Float = 1f)

    /**
     * The gain that brings [samples] up to a strong level (the loudest point near full scale),
     * at most [max] times louder, so a quiet phone recording still has a crunch you hear over music.
     */
    fun normalizer(samples: FloatArray, max: Float = 8f): Float {
        var peak = 0f
        for (v in samples) peak = kotlin.math.max(peak, kotlin.math.abs(v))
        if (peak < 1e-4f) return 1f
        return (0.9f / peak).coerceAtMost(max)
    }

    /** The low roar of a wood oven, for the bake cam's shots (filmed without sound). */
    fun rumble(ms: Long, seed: Long = 11): FloatArray {
        val n = (RATE * ms / 1000).toInt()
        val rnd = java.util.Random(seed)
        var brown = 0f
        var lp = 0f
        return FloatArray(n) { i ->
            brown = (brown + (rnd.nextFloat() * 2 - 1) * 0.02f) * 0.995f
            lp += 0.02f * (brown - lp)
            // crackles: rare short clicks
            val crackle = if (rnd.nextInt(RATE / 6) == 0) rnd.nextFloat() * 0.5f else 0f
            val edge = minOf(1f, i / (0.15f * RATE), (n - i) / (0.15f * RATE))
            (lp * 6f + crackle) * edge
        }
    }

    /** A short clock tick, for each jump of the bake's time-lapse. */
    fun tick(): FloatArray {
        val n = (0.03 * RATE).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            (sin(2 * PI * 2_400 * t) * exp(-t * 180) * 0.5).toFloat()
        }
    }

    fun write(file: File, lengthMs: Long, stampAtMs: Long?, layers: List<Layer> = emptyList()) {
        val n = (RATE * lengthMs / 1000).toInt()
        val pcm = FloatArray(n)
        for (layer in layers) {
            val start = (layer.atMs * RATE / 1000).toInt()
            for (i in layer.samples.indices) {
                val j = start + i
                if (j >= n) break
                if (j >= 0) pcm[j] += layer.samples[i] * layer.gain
            }
        }
        if (stampAtMs != null) seal(pcm, stampAtMs)
        // loud parts are rounded off instead of clipping hard
        for (i in pcm.indices) {
            val v = pcm[i]
            val a = kotlin.math.abs(v)
            if (a > KNEE) pcm[i] = kotlin.math.sign(v) * (KNEE + (1 - KNEE) * kotlin.math.tanh((a - KNEE) / (1 - KNEE)))
        }
        writeWav(file, pcm)
    }

    private fun seal(pcm: FloatArray, stampAtMs: Long) {
        // whoosh as the seal comes down: filtered noise rising in pitch
        val rnd = java.util.Random(7)
        val wStart = ((stampAtMs - 450) * RATE / 1000).toInt().coerceAtLeast(0)
        val wLen = (0.45 * RATE).toInt()
        var lp = 0f
        for (i in 0 until wLen) {
            val j = wStart + i
            if (j !in pcm.indices) break
            val k = i.toFloat() / wLen
            val a = 0.05f + 0.6f * k
            lp += (0.08f + 0.5f * k) * ((rnd.nextFloat() * 2 - 1) - lp)
            pcm[j] += lp * a * 0.5f
        }
        // the thud: a low sine drop plus a short knock
        val s0 = (stampAtMs * RATE / 1000).toInt()
        for (i in 0 until (0.5 * RATE).toInt()) {
            val j = s0 + i
            if (j !in pcm.indices) break
            val t = i.toDouble() / RATE
            val freq = 95.0 * exp(-t * 3) + 45.0
            val body = sin(2 * PI * freq * t) * exp(-t * 9)
            val knock = (rnd.nextFloat() * 2 - 1) * exp(-t * 70)
            pcm[j] += (0.85 * body + 0.35 * knock).toFloat()
        }
    }

    private fun writeWav(file: File, pcm: FloatArray) {
        val data = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val v = (pcm[i].coerceIn(-1f, 1f) * 32767).toInt()
            data[2 * i] = (v and 0xFF).toByte()
            data[2 * i + 1] = (v shr 8 and 0xFF).toByte()
        }
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun str(s: String) = f.write(s.toByteArray(Charsets.US_ASCII))
            fun i32(v: Int) = f.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte(), (v shr 16 and 0xFF).toByte(), (v shr 24 and 0xFF).toByte()))
            fun i16(v: Int) = f.write(byteArrayOf((v and 0xFF).toByte(), (v shr 8 and 0xFF).toByte()))
            str("RIFF"); i32(36 + data.size); str("WAVE")
            str("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
            str("data"); i32(data.size)
            f.write(data)
        }
    }
}
