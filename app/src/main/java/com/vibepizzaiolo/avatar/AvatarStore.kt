package com.vibepizzaiolo.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Your cartoon portrait, kept in the app's own storage. */
object AvatarStore {
    private fun file(context: Context) = File(context.filesDir, "avatar.png")

    fun load(context: Context): Bitmap? =
        file(context).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }

    fun save(context: Context, avatar: Bitmap) {
        file(context).outputStream().use { avatar.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}
