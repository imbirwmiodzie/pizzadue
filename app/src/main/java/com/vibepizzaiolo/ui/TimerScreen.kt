package com.vibepizzaiolo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.timer.Alert
import com.vibepizzaiolo.timer.Cue
import com.vibepizzaiolo.timer.Mode
import com.vibepizzaiolo.timer.Phase
import com.vibepizzaiolo.timer.TickSchedule
import com.vibepizzaiolo.timer.TimerState
import com.vibepizzaiolo.voice.VoiceStatus
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class TimerActions(
    val selectMode: (Mode) -> Unit,
    val setDuration: (Mode, Int) -> Unit,
    val main: () -> Unit,
    val pauseResume: () -> Unit,
    val stop: () -> Unit,
    val share: () -> Unit,
)

private val BAKE_PRESETS = listOf(60, 90, 120, 180)
private val PREHEAT_PRESETS = listOf(20 * 60, 30 * 60, 45 * 60)

@Composable
fun TimerScreen(state: TimerState, settings: Settings, voice: VoiceStatus, a: TimerActions, justBaked: Boolean, avatar: Bitmap? = null) {
    Box(Modifier.fillMaxSize()) {
        TimerContent(state, settings, voice, a, justBaked, avatar)
        if (state.alert != Alert.NONE) SilenceOverlay(state.alert, onSilence = a.stop, onStart = a.main)
    }
}

/**
 * While the end call plays, the whole screen is one big "silence" button: no aiming with floury hands.
 * "Vai!" stays where it always is and starts the next bake straight away (which also ends the call);
 * hidden under the overlay, a tap on it only silenced and the next pizza baked with no timer.
 */
@Composable
private fun SilenceOverlay(alert: Alert, onSilence: () -> Unit, onStart: () -> Unit) {
    Surface(
        onClick = onSilence,
        color = Pal.bg.copy(alpha = 0.88f),
        modifier = Modifier.fillMaxSize(),
    ) {
        // same paddings and button size as the timer underneath, so "Vai!" does not move
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (alert == Alert.PRONTA) "La pizza è pronta!" else "Forno a legna caldo!",
                    color = Pal.tomato, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    "Tap anywhere to silence",
                    color = Pal.fg, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    "or press a volume button, or say “stop”",
                    color = Pal.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Pal.flame, contentColor = Pal.ink),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(68.dp),
            ) {
                Text(
                    if (alert == Alert.PRONTA) "Vai!  Next pizza" else "Vai!  Pizza in",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TimerContent(state: TimerState, settings: Settings, voice: VoiceStatus, a: TimerActions, justBaked: Boolean, avatar: Bitmap?) {
    val bake = state.mode == Mode.BAKE
    val accent = if (bake) Pal.flame else Pal.ember
    val busy = state.isActive || state.phase == Phase.DONE

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("VibePizzaiolo", voice) {
            if (avatar != null) {
                Image(
                    bitmap = avatar.asImageBitmap(), contentDescription = "You, the pizzaiolo",
                    modifier = Modifier.padding(start = 8.dp).size(36.dp).clip(CircleShape),
                )
            }
            IconButton(onClick = a.share) { Icon(Icons.Default.PhotoCamera, contentDescription = "Share your pizza", tint = Pal.flame) }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.card).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeButton("Preheat oven", formatClock(settings.preheatSec * 1000L), !bake, Pal.ember, !busy, Modifier.weight(1f)) { a.selectMode(Mode.PREHEAT) }
            ModeButton("Bake pizza", formatClock(settings.bakeSec * 1000L), bake, Pal.flame, !busy, Modifier.weight(1f)) { a.selectMode(Mode.BAKE) }
        }

        // the timer: as wide as the screen, centred in all the space that is left
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value, maxHeight.value).dp
            Ring(state, accent, settings, Modifier.size(side))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            val current = if (bake) settings.bakeSec else settings.preheatSec
            (if (bake) BAKE_PRESETS else PREHEAT_PRESETS).forEach { sec ->
                val on = sec == current
                OutlinedButton(
                    onClick = { a.setDuration(state.mode, sec) },
                    enabled = !busy,
                    border = BorderStroke(1.dp, if (on) accent else Pal.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (on) accent else Pal.fg),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp),
                ) { Text(formatClock(sec * 1000L), fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        if (justBaked) {
            Button(
                onClick = a.share,
                colors = ButtonDefaults.buttonColors(containerColor = Pal.raised, contentColor = Pal.fg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Pal.flame)
                Text("  Share this pizza", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                state.isActive -> if (bake) "Pizza out" else "Stop preheat"
                bake -> "Vai!"
                else -> "Start preheat"
            }
            Button(
                onClick = a.main,
                colors = ButtonDefaults.buttonColors(containerColor = if (state.isActive) Pal.tomato else accent, contentColor = Pal.ink),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).height(68.dp),
            ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            if (state.isActive) {
                FilledTonalIconButton(
                    onClick = a.pauseResume, modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Pal.card, contentColor = Pal.fg),
                ) {
                    if (state.phase == Phase.PAUSED) Icon(Icons.Default.PlayArrow, "Resume")
                    else Icon(Icons.Default.Pause, "Pause")
                }
            }
        }
    }
}

@Composable
private fun ModeButton(title: String, time: String, selected: Boolean, accent: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(10.dp), color = if (selected) Pal.raised else Color.Transparent,
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (selected) Pal.fg else Pal.muted)
            Text(time, fontFamily = Mono, fontSize = 12.sp, color = if (selected) accent else Pal.muted)
        }
    }
}

@Composable
private fun Ring(state: TimerState, accent: Color, settings: Settings, modifier: Modifier) {
    val cues = remember(state.mode, state.durationMs, settings.gira) {
        TickSchedule.forMode(state.mode, state.durationMs, settings.gira)
    }
    // flash the ring on every tick
    val flash = remember { Animatable(0f) }
    LaunchedEffect(state.next?.atMs) {
        if (state.phase == Phase.RUNNING && state.elapsedMs > 500) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(600))
        }
    }
    val frac = if (state.durationMs > 0) (state.elapsedMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.045f
            val r = size.minDimension / 2 - stroke * 1.9f
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Pal.line, radius = r, center = c, style = Stroke(stroke))
            drawArc(
                color = lerp(accent, Color.White, flash.value * 0.7f),
                startAngle = -90f + 360f * frac, sweepAngle = 360f * (1 - frac), useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // a mark at every tick moment, bigger on full minutes (bake) and thirds (preheat)
            cues.filter { it.cue is Cue.Ticks || it.cue is Cue.LongTick }.forEach { tc ->
                val big = tc.cue is Cue.LongTick || state.mode == Mode.PREHEAT
                val ang = Math.toRadians(-90.0 + 360.0 * tc.atMs / state.durationMs)
                val r1 = r + stroke * (if (big) 0.9f else 1.1f)
                val r2 = r + stroke * 1.8f
                val past = state.phase != Phase.IDLE && tc.atMs <= state.elapsedMs
                drawLine(
                    color = if (past) accent else if (big) Pal.fg else Pal.muted,
                    start = Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat()),
                    end = Offset(c.x + r2 * cos(ang).toFloat(), c.y + r2 * sin(ang).toFloat()),
                    strokeWidth = if (big) stroke * 0.32f else stroke * 0.16f, cap = StrokeCap.Round,
                )
            }
        }
        val density = LocalDensity.current
        val time = formatClock(state.remainingMs)
        // digits sized from the ring, not from the font-size setting, so they always fit
        val digits = with(density) { (side * (if (time.length > 4) 0.19f else 0.23f)).toSp() }
        val small = with(density) { (side * 0.042f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val label = when {
                state.alert == Alert.PRONTA -> "pronta!"
                state.alert == Alert.FORNO -> "forno caldo!"
                state.phase == Phase.PAUSED -> "paused"
                state.phase == Phase.RUNNING -> if (state.mode == Mode.BAKE) "pizza in oven" else "oven heating"
                state.mode == Mode.PREHEAT -> "preheat"
                else -> "bake"
            }
            Text(
                label.uppercase(), fontSize = small, letterSpacing = 1.5.sp,
                color = if (state.alert != Alert.NONE) Pal.tomato else if (state.phase == Phase.IDLE) Pal.muted else accent,
            )
            Text(time, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = digits, color = Pal.fg)
        }
    }
}
