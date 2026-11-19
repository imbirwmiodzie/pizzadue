package com.vibepizzaiolo.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Your face, kept in the app's own storage: the square around your eyes cut from the selfie, and
 * the pen-and-ink face drawn from it. The square is kept so a new drawing style can redraw you
 * without a new selfie.
 */
object AvatarStore {
    private fun sketchFile(context: Context) = File(context.filesDir, "face-sketch.png")
    private fun cropFile(context: Context) = File(context.filesDir, "face-crop.jpg")

    /** The cartoon portrait of earlier versions: a square photo that no longer fits the drawing. */
    private fun oldFile(context: Context) = File(context.filesDir, "avatar.png")

    /** The drawn face, redrawn from the kept square if the drawing is missing. Call off the main thread. */
    fun load(context: Context): Bitmap? {
        oldFile(context).delete()
        read(sketchFile(context))?.let { return it }
        val crop = read(cropFile(context)) ?: return null
        return runCatching { FaceSketch.draw(crop) }.getOrNull()?.also { writePng(sketchFile(context), it) }
    }

    fun save(context: Context, crop: Bitmap, sketch: Bitmap) {
        cropFile(context).outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        writePng(sketchFile(context), sketch)
    }

    fun delete(context: Context) {
        sketchFile(context).delete()
        cropFile(context).delete()
        oldFile(context).delete()
    }

    private fun read(f: File): Bitmap? =
        f.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }

    private fun writePng(f: File, b: Bitmap) {
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
