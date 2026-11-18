package com.vibepizzaiolo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.voice.VoiceStatus

@Composable
fun ScreenHeader(title: String, voice: VoiceStatus?, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (voice != null) VoiceChip(voice)
        if (action != null) action()
    }
}

@Composable
fun VoiceChip(voice: VoiceStatus) {
    val (label, color) = when (voice) {
        is VoiceStatus.Listening -> "listening" to Pal.basil
        VoiceStatus.Preparing -> "preparing voice…" to Pal.flame
        VoiceStatus.NoPermission -> "mic off" to Pal.tomato
        is VoiceStatus.Error -> "voice error" to Pal.tomato
        VoiceStatus.Off -> "voice off" to Pal.muted
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = color, fontFamily = Mono, fontSize = 11.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = Pal.muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** Rows grouped in one rounded card with thin dividers. */
@Composable
fun Group(content: @Composable () -> Unit) {
    Surface(color = Pal.card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(color = Pal.line, thickness = 1.dp)

@Composable
fun RowLabel(title: String, hint: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, fontSize = 14.sp)
        if (hint != null) Text(hint, color = Pal.muted, fontSize = 12.sp)
    }
}

@Composable
fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        RowLabel(title, hint, Modifier.weight(1f))
        if (extra != null) { extra(); Spacer(Modifier.size(8.dp)) }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Pal.basil, checkedThumbColor = Pal.fg),
        )
    }
}

@Composable
fun StepperRow(title: String, hint: String?, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RowLabel(title, hint, Modifier.weight(1f))
        OutlinedIconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, contentDescription = "Less $title") }
        Text(
            value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp),
        )
        OutlinedIconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, contentDescription = "More $title") }
    }
}

fun formatClock(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
