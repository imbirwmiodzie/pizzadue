package com.vibepizzaiolo.community

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a pizza's picture wherever it lives: drawn sample, file on the phone, or the server. */
object Photos {
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(ref: String, small: Boolean): Bitmap? = memory.get(key(ref, small))

    private fun key(ref: String, small: Boolean) = "$ref#$small"

    suspend fun load(context: Context, ref: String, small: Boolean): Bitmap? {
        cached(ref, small)?.let { return it }
        val side = if (small) 360 else 1080
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                when {
                    ref.startsWith("paint:") -> {
                        val (_, type, seed) = ref.split(":")
                        PizzaPainter.paint(PizzaType.valueOf(type), seed.toInt(), if (small) 360 else 720)
                    }
                    ref.startsWith("file:") -> decode(File(ref.removePrefix("file:")), side)
                    ref.startsWith("http") -> decode(download(context, if (small) ref.replace("/card.jpg", "/thumb.jpg") else ref), side)
                    else -> null
                }
            }.getOrNull()
        }
        if (bmp != null) memory.put(key(ref, small), bmp)
        return bmp
    }

    private fun decode(file: File, side: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Server photos never change under one address, so a downloaded one is kept. */
    private fun download(context: Context, url: String): File {
        val dir = File(context.cacheDir, "community").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
        val file = File(dir, "$name.jpg")
        if (file.exists() && file.length() > 0) return file
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 20000
        try {
            val tmp = File(dir, "$name.part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(file)
        } finally {
            c.disconnect()
        }
        return file
    }

    /** The photo as it is posted: at most 1600 px JPEG (re-encoding drops EXIF, so no GPS leaves the phone). */
    fun prepare(photo: Bitmap): PhotoData {
        val scale = 1600f / maxOf(photo.width, photo.height)
        val big = if (scale < 1f) Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt(), (photo.height * scale).toInt(), true) else photo
        val jpeg = ByteArrayOutputStream().also { big.compress(Bitmap.CompressFormat.JPEG, 86, it) }.toByteArray()
        val s = 160f / maxOf(photo.width, photo.height)
        val tiny = Bitmap.createScaledBitmap(photo, (photo.width * s).toInt().coerceAtLeast(1), (photo.height * s).toInt().coerceAtLeast(1), true)
        val pixels = IntArray(tiny.width * tiny.height)
        tiny.getPixels(pixels, 0, tiny.width, 0, 0, tiny.width, tiny.height)
        return PhotoData(jpeg, pixels, tiny.width, tiny.height)
    }
}
