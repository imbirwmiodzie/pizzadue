package com.pizzadue.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pizzadue.Heard
import com.pizzadue.settings.Settings
import com.pizzadue.timer.Alert
import com.pizzadue.timer.Cue
import com.pizzadue.timer.Mode
import com.pizzadue.timer.Phase
import com.pizzadue.timer.TickSchedule
import com.pizzadue.timer.TimerState
import com.pizzadue.voice.VoiceStatus
import kotlin.math.cos
import kotlin.math.sin

class TimerActions(
    val selectMode: (Mode) -> Unit,
    val setDuration: (Mode, Int) -> Unit,
    val main: () -> Unit,
    val pauseResume: () -> Unit,
    val stop: () -> Unit,
    val toggleBright: () -> Unit,
)

private val BAKE_PRESETS = listOf(60, 90, 120, 180)
private val PREHEAT_PRESETS = listOf(20 * 60, 30 * 60, 45 * 60)

@Composable
fun TimerScreen(state: TimerState, settings: Settings, heard: Heard?, voice: VoiceStatus, a: TimerActions) {
    val bake = state.mode == Mode.BAKE
    val accent = if (bake) Pal.flame else Pal.ember
    val busy = state.isActive || state.phase == Phase.DONE

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("PizzaDue", voice)

        // mode selector
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.card).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeButton("Preheat oven", formatClock(settings.preheatSec * 1000L), !bake, Pal.ember, !busy, Modifier.weight(1f)) { a.selectMode(Mode.PREHEAT) }
            ModeButton("Bake pizza", formatClock(settings.bakeSec * 1000L), bake, Pal.flame, !busy, Modifier.weight(1f)) { a.selectMode(Mode.BAKE) }
        }

        Ring(state, accent, settings)

        // presets
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
            val current = (if (bake) settings.bakeSec else settings.preheatSec)
            (if (bake) BAKE_PRESETS else PREHEAT_PRESETS).forEach { sec ->
                val on = sec == current
                OutlinedButton(
                    onClick = { a.setDuration(state.mode, sec) },
                    enabled = !busy,
                    border = BorderStroke(1.dp, if (on) accent else Pal.line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (on) accent else Pal.fg),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) { Text(formatClock(sec * 1000L), fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // main button (+ pause)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                state.phase == Phase.DONE -> "Pizza out"
                state.isActive -> if (bake) "Pizza out" else "Stop preheat"
                bake -> "Vai!"
                else -> "Start preheat"
            }
            Button(
                onClick = a.main,
                colors = ButtonDefaults.buttonColors(containerColor = if (busy) Pal.tomato else accent, contentColor = Pal.ink),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).height(64.dp),
            ) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            if (state.isActive) {
                FilledTonalIconButton(
                    onClick = a.pauseResume, modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Pal.card, contentColor = Pal.fg),
                ) {
                    if (state.phase == Phase.PAUSED) Icon(Icons.Default.PlayArrow, "Resume")
                    else Icon(Icons.Default.Pause, "Pause")
                }
            }
        }

        if (state.alert != Alert.NONE) {
            OutlinedButton(onClick = a.stop, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Pal.tomato)) {
                Text(
                    if (state.alert == Alert.PRONTA) "La pizza è pronta! · Silence" else "Forno a legna caldo! · Silence",
                    color = Pal.tomato, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        BrightButton(settings.bright, a.toggleBright)

        Surface(color = Pal.card, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (heard == null) {
                    Text("Say “vai!” or “waj!” when the pizza goes in", color = Pal.muted, fontFamily = Mono, fontSize = 12.sp)
                    Text("Preheat: “preheat” / “rozgrzej piec”. Stop: “pizza out” / “wyjmij”.", fontSize = 13.sp)
                } else {
                    Text("Heard: “${heard.heard}”", color = Pal.muted, fontFamily = Mono, fontSize = 12.sp)
                    if (heard.reply.isNotBlank()) Text(heard.reply, fontSize = 14.sp)
                }
                if (voice is VoiceStatus.NoPermission) Text("Allow the microphone to use voice commands.", color = Pal.tomato, fontSize = 13.sp)
                if (voice is VoiceStatus.Error) Text(voice.message, color = Pal.tomato, fontSize = 13.sp)
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
private fun BrightButton(on: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (on) Pal.flame.copy(alpha = 0.10f) else Pal.card,
        border = BorderStroke(1.5.dp, if (on) Pal.flame else Pal.line),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Screen always on, full brightness: ${if (on) "on" else "off"}" },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WbSunny, null, tint = if (on) Pal.flame else Pal.muted, modifier = Modifier.size(26.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Screen always on", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("full brightness, never dims", color = Pal.muted, fontSize = 12.sp)
            }
            Text(
                if (on) "ON" else "OFF", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                color = if (on) Pal.ink else Pal.muted,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Pal.flame else Pal.raised).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Ring(state: TimerState, accent: Color, settings: Settings) {
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

    Box(Modifier.fillMaxWidth(0.78f).aspectRatio(1f).padding(4.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val r = size.minDimension / 2 - stroke * 1.8f
            val c = Offset(size.width / 2, size.height / 2)
            val tl = Offset(c.x - r, c.y - r)
            drawCircle(Pal.line, radius = r, center = c, style = Stroke(stroke))
            drawArc(
                color = lerp(accent, Color.White, flash.value * 0.7f),
                startAngle = -90f + 360f * frac, sweepAngle = 360f * (1 - frac), useCenter = false,
                topLeft = tl, size = Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // marks: every tick moment, bigger on full minutes / thirds
            cues.filter { it.cue !is Cue.BakeDone && it.cue !is Cue.PreheatDone && it.cue !is Cue.Gira }.forEach { tc ->
                val big = tc.cue is Cue.LongTick || state.mode == Mode.PREHEAT
                val ang = Math.toRadians((-90.0 + 360.0 * tc.atMs / state.durationMs))
                val r1 = r + stroke * (if (big) 0.9f else 1.1f)
                val r2 = r + stroke * 1.75f
                val past = state.phase != Phase.IDLE && tc.atMs <= state.elapsedMs
                drawLine(
                    color = if (past) accent else if (big) Pal.fg else Pal.muted,
                    start = Offset(c.x + r1 * cos(ang).toFloat(), c.y + r1 * sin(ang).toFloat()),
                    end = Offset(c.x + r2 * cos(ang).toFloat(), c.y + r2 * sin(ang).toFloat()),
                    strokeWidth = if (big) stroke * 0.32f else stroke * 0.16f, cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val label = when {
                state.alert == Alert.PRONTA -> "pronta!"
                state.alert == Alert.FORNO -> "forno caldo!"
                state.phase == Phase.PAUSED -> "paused"
                state.phase == Phase.RUNNING -> if (state.mode == Mode.BAKE) "pizza in oven" else "oven heating"
                state.mode == Mode.PREHEAT -> "ready to heat"
                else -> "ready"
            }
            Text(label.uppercase(), color = if (state.alert != Alert.NONE) Pal.tomato else if (state.phase == Phase.IDLE) Pal.muted else accent, fontSize = 12.sp, letterSpacing = 1.2.sp)
            Text(formatClock(state.remainingMs), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = if (state.durationMs >= 600_000) 52.sp else 60.sp)
            Text(cueLabel(state), color = Pal.muted, fontFamily = Mono, fontSize = 12.sp)
        }
    }
}

private fun cueLabel(state: TimerState): String {
    if (state.phase == Phase.DONE) return "say “pizza out” / “wyjmij”"
    if (!state.isActive) return if (state.mode == Mode.BAKE) "say “vai!” / “waj!”" else "say “preheat” / “rozgrzej piec”"
    val next = state.next ?: return ""
    val what = when (val c = next.cue) {
        is Cue.Ticks -> if (c.count == 1) "1 tick" else "${c.count} ticks"
        Cue.LongTick -> "long tick"
        Cue.Gira -> "gira gira"
        Cue.BakeDone -> "pronta"
        Cue.PreheatDone -> "3 ticks + forno"
    }
    return "$what in ${formatClock(next.atMs - state.elapsedMs)}"
}
