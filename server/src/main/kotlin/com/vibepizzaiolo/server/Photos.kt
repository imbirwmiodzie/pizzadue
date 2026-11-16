package com.vibepizzaiolo.server

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream

/**
 * Photos on disk under [dir]: `<id>/card.jpg` (at most 1080 px) and `<id>/thumb.jpg` (at most 360 px).
 * Every upload is decoded and written again, so only real images are kept and nothing of the
 * original file (EXIF, GPS) survives.
 */
class PhotoStore(private val dir: File) {
    init { dir.mkdirs() }

    /** false: [bytes] is not an image this server can read. */
    fun save(id: String, bytes: ByteArray): Boolean {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return false
        if (image.width < 64 || image.height < 64) return false
        val folder = File(dir, id).apply { mkdirs() }
        write(scaled(image, 1080), File(folder, "card.jpg"), 0.86f)
        write(scaled(image, 360), File(folder, "thumb.jpg"), 0.8f)
        return true
    }

    fun file(id: String, name: String): File? {
        if (!ID.matches(id) || name !in FILES) return null
        return File(File(dir, id), name).takeIf { it.isFile }
    }

    fun delete(id: String) {
        if (ID.matches(id)) File(dir, id).deleteRecursively()
    }

    private fun scaled(src: BufferedImage, maxSide: Int): BufferedImage {
        val k = minOf(1.0, maxSide.toDouble() / maxOf(src.width, src.height))
        val w = (src.width * k).toInt().coerceAtLeast(1)
        val h = (src.height * k).toInt().coerceAtLeast(1)
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        out.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(src, 0, 0, w, h, null)
            dispose()
        }
        return out
    }

    private fun write(image: BufferedImage, to: File, quality: Float) {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        FileImageOutputStream(to).use { out ->
            writer.output = out
            writer.write(null, IIOImage(image, null, null), param)
        }
        writer.dispose()
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        private val ID = Regex("[0-9a-f-]{36}")
        private val FILES = setOf("card.jpg", "thumb.jpg")
    }
}
