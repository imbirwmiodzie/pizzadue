package com.vibepizzaiolo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.ShareUi
import com.vibepizzaiolo.share.Design
import com.vibepizzaiolo.share.Format
import com.vibepizzaiolo.share.Target

private val INSTAGRAM = Color(0xFFE1306C)
private val TIKTOK = Color(0xFF111111)
private val FACEBOOK = Color(0xFF1877F2)
private val WHATSAPP = Color(0xFF25D366)

@Composable
fun ShareScreen(
    ui: ShareUi,
    onDesign: (Design) -> Unit,
    onFormat: (Format) -> Unit,
    onShare: (Target) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Share your pizza", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // preview
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val card = ui.card
            when {
                ui.error != null -> Text(ui.error, color = Pal.tomato)
                card != null -> Image(
                    bitmap = card.asImageBitmap(),
                    contentDescription = "Your pizza card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                )
            }
            if (ui.working && ui.error == null) CircularProgressIndicator(color = Pal.flame)
        }

        ChoiceRow(Design.entries.map { it.label }, ui.design.ordinal) { onDesign(Design.entries[it]) }
        ChoiceRow(Format.entries.map { it.label }, ui.format.ordinal) { onFormat(Format.entries[it]) }

        ShareButtons(ui.card != null && !ui.working, onShare, onSave)
    }
}

/** Instagram, TikTok, WhatsApp, Facebook, the share menu and save: used for cards and reels alike. */
@Composable
fun ShareButtons(ready: Boolean, onShare: (Target) -> Unit, onSave: () -> Unit, onSaveCover: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton("Instagram", INSTAGRAM, ready, Modifier.weight(1f)) { onShare(Target.INSTAGRAM) }
            BrandButton("TikTok", TIKTOK, ready, Modifier.weight(1f)) { onShare(Target.TIKTOK) }
            BrandButton("WhatsApp", WHATSAPP, ready, Modifier.weight(1f)) { onShare(Target.WHATSAPP) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrandButton("Facebook", FACEBOOK, ready, Modifier.weight(1f)) { onShare(Target.FACEBOOK) }
            OutlinedButton(onClick = { onShare(Target.OTHER) }, enabled = ready, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("More…", color = Pal.fg, maxLines = 1)
            }
            OutlinedButton(onClick = onSave, enabled = ready, modifier = Modifier.weight(1f).height(52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                Text("Save", color = Pal.fg, maxLines = 1)
            }
            if (onSaveCover != null) {
                OutlinedButton(onClick = onSaveCover, enabled = ready, modifier = Modifier.weight(1f).height(52.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                    Text("Cover", color = Pal.fg, maxLines = 1)
                }
            }
        }
        Text(
            if (onSaveCover != null) "The caption with hashtags is copied when you share: paste it in your post. Add a trending sound in Instagram or TikTok; the crunch stays loud under it. \"Cover\" saves a picture for the reel's cover."
            else "The caption with hashtags is copied when you share: paste it in your post.",
            color = Pal.muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun BrandButton(label: String, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
    ) { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(10.dp),
                color = if (on) Pal.raised else Pal.card,
                border = BorderStroke(1.dp, if (on) Pal.flame else Pal.line),
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 13.sp, color = if (on) Pal.flame else Pal.fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
