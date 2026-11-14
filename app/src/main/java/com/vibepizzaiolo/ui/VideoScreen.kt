package com.vibepizzaiolo.ui

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vibepizzaiolo.VideoUi
import com.vibepizzaiolo.share.Target

/** Making the reel, then watching and sharing it. */
@Composable
fun VideoScreen(ui: VideoUi, onShare: (Target) -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Your pizza reel", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (ui) {
                is VideoUi.Working -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Making your reel…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { ui.progress / 100f }, color = Pal.flame, trackColor = Pal.card, modifier = Modifier.fillMaxWidth(0.7f))
                    Text("${ui.progress}%", color = Pal.muted)
                    Text("Cutting from above, around and the rim, stamping the seal, adding you as the pizzaiolo.", color = Pal.muted, fontSize = 13.sp)
                }
                is VideoUi.Failed -> Text(ui.message, color = Pal.tomato, fontSize = 16.sp)
                is VideoUi.Ready -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(Uri.fromFile(ui.file))
                            setOnPreparedListener { mp -> mp.isLooping = true; start() }
                        }
                    },
                    modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)),
                )
                VideoUi.Guide -> Unit
            }
        }
        ShareButtons(ui is VideoUi.Ready, onShare, onSave)
    }
}
