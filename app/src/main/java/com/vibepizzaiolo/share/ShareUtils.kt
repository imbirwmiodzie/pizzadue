package com.vibepizzaiolo.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max

/** Where friends get the app. Shown in the caption that goes with every share. */
const val DOWNLOAD_URL = "https://github.com/imbirwmiodzie/pizzadue/releases/tag/latest"

/** Apps with their own share button; anything else goes through Android's share menu. */
enum class Target(val label: String, val pkg: String?) {
    INSTAGRAM("Instagram", "com.instagram.android"),
    FACEBOOK("Facebook", "com.facebook.katana"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    OTHER("More…", null),
}

object ShareUtils {
    private fun authority(context: Context) = "${context.packageName}.files"

    /** A fresh file for the camera app to write the photo into. */
    fun newPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Loads a photo at a sensible size (long side about 2000 px), turned the right way up. */
    fun loadPhoto(context: Context, uri: Uri, maxSide: Int = 2160): Bitmap? = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        if (rotation == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }.getOrNull()

    fun caption(info: CardInfo): String = buildString {
        append("Pizza nº ${info.pizzaNo} 🍕 ${CardRenderer.clock(info.bakeMs)} in the oven, ${CardRenderer.pct(info.hydration)} hydration.\n")
        append("Timed by voice with VibePizzaiolo. Vai!\n")
        append("Get the app: $DOWNLOAD_URL\n")
        append("#VibePizzaiolo #vai #pizzanapoletana #homemadepizza")
    }

    /**
     * Sends the card to [target]. The caption goes along as text (WhatsApp, Messenger and most
     * apps use it) and is also copied, because Instagram and Facebook ignore text from other apps.
     */
    fun share(context: Context, card: Bitmap, caption: String, target: Target) {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("vibepizzaiolo_") }?.forEach { it.delete() }
        val file = File(dir, "vibepizzaiolo_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { card.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = FileProvider.getUriForFile(context, authority(context), file)

        copyCaption(context, caption)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (target.pkg != null) {
            try {
                context.startActivity(Intent(send).setPackage(target.pkg))
                return
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "${target.label} is not installed", Toast.LENGTH_SHORT).show()
            }
        }
        context.startActivity(Intent.createChooser(send, "Share your pizza"))
    }

    private fun copyCaption(context: Context, caption: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("VibePizzaiolo caption", caption))
        // Android 13+ shows its own "copied" message
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, "Caption copied: paste it in your post", Toast.LENGTH_LONG).show()
        }
    }

    /** Saves the card to Pictures/VibePizzaiolo. Needs Android 10 or newer (no storage permission). */
    fun saveToGallery(context: Context, card: Bitmap): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "VibePizzaiolo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VibePizzaiolo")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { card.compress(Bitmap.CompressFormat.JPEG, 92, it) } != null
        }.getOrDefault(false)
    }
}
