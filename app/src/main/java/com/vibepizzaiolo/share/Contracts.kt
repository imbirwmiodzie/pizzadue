package com.vibepizzaiolo.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContract

/** Records a video with the phone's camera app into [Uri], at most [MAX_SECONDS] long. */
class RecordPizzaVideo : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, MAX_SECONDS)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK

    companion object {
        const val MAX_SECONDS = 30
    }
}

/** Takes a photo into [Uri], asking the camera app to start with the front (selfie) camera. */
class TakeSelfie : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            // different camera apps listen to different hints; none is guaranteed
            .putExtra("android.intent.extras.CAMERA_FACING", 1)
            .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}
