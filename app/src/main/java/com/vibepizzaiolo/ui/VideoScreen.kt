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
import com.vibepizzaiolo.video.ReelStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface

/** Making the reel, then watching it, choosing its look, and sharing it. */
@Composable
fun VideoScreen(
    ui: VideoUi,
    style: ReelStyle,
    onStyle: (ReelStyle) -> Unit,
    onShare: (Target) -> Unit,
    onSave: () -> Unit,
    onSaveCover: () -> Unit,
    onClose: () -> Unit,
) {
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
                    Text("Opening on the crunch, cutting the shots, stamping the seal, adding you as the pizzaiolo.", color = Pal.muted, fontSize = 13.sp)
                }
                is VideoUi.Failed -> Text(ui.message, color = Pal.tomato, fontSize = 16.sp)
                is VideoUi.Ready -> AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp -> mp.isLooping = true; start() }
                        }
                    },
                    // a new style is a new file: play it in the same view
                    update = { v -> if (v.tag != ui.file) { v.tag = ui.file; v.setVideoURI(Uri.fromFile(ui.file)) } },
                    modifier = Modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)),
                )
                VideoUi.Guide -> Unit
            }
        }
        StyleRow(style, enabled = ui !is VideoUi.Working, onStyle)
        ShareButtons(ui is VideoUi.Ready, onShare, onSave, onSaveCover)
    }
}

/** The reel's looks; choosing one makes the reel again from the same recording. */
@Composable
private fun StyleRow(selected: ReelStyle, enabled: Boolean, onStyle: (ReelStyle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            ReelStyle.entries.forEach { st ->
                val on = st == selected
                Surface(
                    onClick = { onStyle(st) },
                    enabled = enabled,
                    shape = RoundedCornerShape(10.dp),
                    color = if (on) Pal.raised else Pal.card,
                    border = if (on) BorderStroke(1.5.dp, Pal.flame) else null,
                    modifier = Modifier.weight(1f).height(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(st.label, fontSize = 13.sp, color = if (on) Pal.flame else Pal.fg, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
        Text(selected.hint, color = Pal.muted, fontSize = 12.sp)
    }
}
