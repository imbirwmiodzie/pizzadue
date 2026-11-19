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
import com.vibepizzaiolo.BuildConfig
import java.io.File
import kotlin.math.max

/** Where friends get the app. Shown in the caption that goes with every share. */
const val DOWNLOAD_URL = "https://github.com/imbirwmiodzie/pizzadue/releases/tag/latest"

/** Apps with their own share button; anything else goes through Android's share menu. */
enum class Target(val label: String, val pkg: String?, val alsoPkg: String? = null) {
    INSTAGRAM("Instagram", "com.instagram.android"),
    TIKTOK("TikTok", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
    FACEBOOK("Facebook", "com.facebook.katana"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    OTHER("More…", null),
}

/** What the caption needs to know: the pizza (or the night) and the pizzaiolo's own numbers. */
data class CaptionInfo(
    val card: CardInfo,
    val ovenC: Int = 0,
    val fermentH: Int = 0,
    val seriesGoal: Int = 0,
    val night: com.vibepizzaiolo.video.NightInfo? = null,
)

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

    /**
     * The post's text: a hook line, the numbers pizza people look for, and the hashtags they
     * search; the app's own tag comes last. [reel] adds the line about the sound.
     */
    fun caption(info: CaptionInfo, reel: Boolean): String = buildString {
        val c = info.card
        val n = info.night
        if (n != null) {
            append("Pizza night: ${n.pizzas} pizzas")
            if (n.guests.isNotEmpty()) append(" for ${n.guests.size} friends")
            append(" 🍕🔥\n")
            if (n.favourite != null) append("Tonight's favourite: ${n.favourite} (×${n.favouriteCount})\n")
        } else {
            val series = if (info.seriesGoal > 0 && c.pizzaNo <= info.seriesGoal) " of ${info.seriesGoal}" else ""
            append("Pizza nº ${c.pizzaNo}$series 🍕 ")
            append(listOfNotNull(
                "${CardRenderer.clock(c.bakeMs)} in the oven" + (if (info.ovenC > 0) " at ${info.ovenC} °C" else ""),
                "${CardRenderer.pct(c.hydration)} hydration",
                info.fermentH.takeIf { it > 0 }?.let { "$it h ferment" },
            ).joinToString(" · "))
            append("\n")
        }
        if (reel) append("Sound on for the crunch 🔊\n")
        append("Timed by voice with VibePizzaiolo: $DOWNLOAD_URL\n\n")
        val tags = buildList {
            if (n != null) addAll(listOf("#pizzanight", "#pizzaparty"))
            addAll(listOf("#pizzanapoletana", "#neapolitanpizza", "#homemadepizza", "#cornicione", "#pizzaoven", "#pizzalover"))
            if (n == null && info.seriesGoal > 0) add("#${info.seriesGoal}pizzas")
            add("#VibePizzaiolo")
        }
        append(tags.joinToString(" "))
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
        send(context, send, target)
    }

    private fun copyCaption(context: Context, caption: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("VibePizzaiolo caption", caption))
        // Android 13+ shows its own "copied" message
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(context, "Caption copied: paste it in your post", Toast.LENGTH_LONG).show()
        }
    }

    /** A fresh file for the camera app to record the pizza video into. */
    fun newVideoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "videos").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("recording_") }?.forEach { it.delete() }
        val file = File(dir, "recording_${System.currentTimeMillis()}.mp4")
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /**
     * Sends the finished reel to [target], like [share] does for images. With a Meta app id
     * (BuildConfig.FB_APP_ID) Instagram opens straight in its Reels editor; without one,
     * Instagram asks where it goes (Reels, Story, Feed or a message).
     */
    fun shareVideo(context: Context, video: File, caption: String, target: Target) {
        val uri = FileProvider.getUriForFile(context, authority(context), video)
        copyCaption(context, caption)
        if (target == Target.INSTAGRAM && BuildConfig.FB_APP_ID.isNotBlank()) {
            val reels = Intent("com.instagram.share.ADD_TO_REEL").apply {
                setDataAndType(uri, "video/mp4")
                putExtra("com.facebook.platform.extra.APPLICATION_ID", BuildConfig.FB_APP_ID)
                setPackage(target.pkg)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(reels)
                return
            } catch (_: ActivityNotFoundException) {
                // an Instagram without Reels sharing: its usual share menu below
            }
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        send(context, send, target)
    }

    /** Opens [target]'s own app with [send], or Android's share menu if it is not installed. */
    private fun send(context: Context, send: Intent, target: Target) {
        for (pkg in listOfNotNull(target.pkg, target.alsoPkg)) {
            try {
                context.startActivity(Intent(send).setPackage(pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // try the next package, then the share menu
            }
        }
        if (target.pkg != null) Toast.makeText(context, "${target.label} is not installed", Toast.LENGTH_SHORT).show()
        context.startActivity(Intent.createChooser(send, "Share your pizza"))
    }

    /** Saves the reel to Movies/VibePizzaiolo (Android 10 or newer). */
    fun saveVideoToGallery(context: Context, video: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VibePizzaiolo_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VibePizzaiolo")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> video.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
    }

    /** Saves the reel's cover (a JPEG file) to Pictures/VibePizzaiolo, for Instagram's "Add from camera roll". */
    fun saveImageFileToGallery(context: Context, image: File): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "VibePizzaiolo_cover_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VibePizzaiolo")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> image.inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
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
