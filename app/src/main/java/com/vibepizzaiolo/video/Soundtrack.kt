package com.vibepizzaiolo.video

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Writes the reel's whole sound as one WAV file: the real crunch of the rim and the voice clip
 * ([layers], each placed at its time), plus a whoosh and a heavy "thud" where the quality seal lands.
 * Mixing it here, and handing Media3 a single audio file, keeps Media3 from joining sounds of
 * different formats across items, which crashed on some phones.
 */
object Soundtrack {
    const val RATE = 44_100

    /** A sound to mix in: mono samples at [RATE], starting at [atMs] into the reel. */
    class Layer(val atMs: Long, val samples: FloatArray)

    fun write(file: File, lengthMs: Long, stampAtMs: Long, layers: List<Layer> = emptyList()) {
        val n = (RATE * lengthMs / 1000).toInt()
        val pcm = FloatArray(n)
        for (layer in layers) {
            val start = (layer.atMs * RATE / 1000).toInt()
            for (i in layer.samples.indices) {
                val j = start + i
                if (j >= n) break
                if (j >= 0) pcm[j] += layer.samples[i]
            }
        }
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
        writeWav(file, pcm)
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
