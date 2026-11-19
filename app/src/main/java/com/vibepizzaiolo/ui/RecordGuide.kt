package com.vibepizzaiolo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.share.RecordPizzaVideo
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val LOOP_S = 18.5f
private val WOOD_DARK = Color(0xFF3D2716)
private val WOOD = Color(0xFF5C3B22)
private val CRUST = Color(0xFFDCA867)
private val SAUCE = Color(0xFFC0392B)
private val CHEESE = Color(0xFFFFF6E0)

private data class Step(val n: Int, val title: String, val text: String)

private val STEPS = listOf(
    Step(1, "Above", "Hold the phone flat over the pizza. The whole pizza in the frame, 2 seconds."),
    Step(2, "Around", "Walk slowly once around the pizza, at rim height, lens towards the centre. About 8 seconds."),
    Step(3, "Rim", "Film the rim up close and press it with a finger 2–3 times, near the phone. Let it crunch! About 4 seconds."),
    Step(4, "Under", "Lift the edge with the peel or a fork and show the underside, its leopard spots. About 3 seconds."),
    Step(5, "Slice", "Pull out a slice slowly, cheese stretching. About 4 seconds."),
)

/** When each step of the animation starts, in seconds of its loop. */
private val STARTS = floatArrayOf(0f, 3f, 8.5f, 12f, 15f)

/** Shown before recording: an animation of how to film the pizza for the reel. */
@Composable
fun RecordGuideScreen(
    onRecord: () -> Unit,
    onGallery: () -> Unit,
    onClose: () -> Unit,
    bakeCam: Boolean = false,
    night: String? = null,
    onDropBakeCam: () -> Unit = {},
) {
    BackHandler(onBack = onClose)
    val transition = rememberInfiniteTransition(label = "guide")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = LOOP_S,
        animationSpec = infiniteRepeatable(tween((LOOP_S * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val step = STEPS[STARTS.indexOfLast { t >= it }.coerceAtLeast(0)]

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(if (night != null) "Film the night's pizza" else "Film your pizza", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        if (bakeCam) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                Text("🔥 The bake cam's launch, bake and pull-out go in too.", fontSize = 13.sp, color = Pal.basil, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = onDropBakeCam) { Text("Leave out", color = Pal.muted, fontSize = 13.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 8.dp)) {
            STEPS.forEach { s ->
                val on = s.n == step.n
                Text(
                    "${s.n} ${s.title}", fontSize = 12.sp,
                    color = if (on) Pal.flame else Pal.muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Canvas(Modifier.weight(1f).fillMaxWidth()) { scene(t) }
        Text("${step.n} · ${step.title}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(step.text, fontSize = 15.sp, color = Pal.fg)
        Text(
            "One take, about 20 s. The app cuts it by itself: it opens on the crunch, then your numbers, the seal, and you as the pizzaiolo at the end.",
            fontSize = 12.sp, color = Pal.muted,
        )
        Button(
            onClick = onRecord,
            colors = ButtonDefaults.buttonColors(containerColor = Pal.tomato, contentColor = Pal.ink),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) { Text("● Record (max ${RecordPizzaVideo.MAX_SECONDS} s)", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Use a video from the gallery", color = Pal.fg)
        }
    }
}

private fun DrawScope.scene(t: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2
    val cy = h * 0.6f
    // table
    val table = Path().apply {
        moveTo(w * 0.08f, h * 0.38f); lineTo(w * 0.92f, h * 0.38f); lineTo(w * 1.05f, h); lineTo(-w * 0.05f, h); close()
    }
    drawPath(table, WOOD_DARK)
    drawPath(Path().apply { moveTo(w * 0.1f, h * 0.42f); lineTo(w * 0.9f, h * 0.42f); lineTo(w, h); lineTo(0f, h); close() }, WOOD)

    when {
        t < STARTS[1] -> stepAbove(t, cx, cy, w, h)
        t < STARTS[2] -> stepAround((t - STARTS[1]) / (STARTS[2] - STARTS[1]), cx, cy, w, h)
        t < STARTS[3] -> stepRim(t - STARTS[2], w, h)
        t < STARTS[4] -> stepUnder(t - STARTS[3], cx, cy, w, h)
        else -> stepSlice(t - STARTS[4], cx, cy, w, h)
    }
}

/** The edge lifted with the peel: the underside with its dark spots, the phone low looking at it. */
private fun DrawScope.stepUnder(t: Float, cx: Float, cy: Float, w: Float, h: Float) {
    val lift = (t / 0.8f).coerceIn(0f, 1f) * 22f
    val r = w * 0.32f
    rotate(-lift, Offset(cx + r, cy)) {
        // the underside, seen as the pizza tilts up: a golden oval with leopard spots
        drawOval(Color(0xFFC98A4B), Offset(cx - r, cy - r * 0.42f), Size(2 * r, 2 * r * 0.42f))
        for (i in 0 until 16) {
            val a = i * 2.39f
            val d = 0.2f + (i % 5) * 0.15f
            drawCircle(Color(0xFF2A1A0E), 4f + (i % 3) * 3f, Offset(cx + kotlin.math.cos(a) * r * d, cy + sin(a) * r * 0.42f * d))
        }
    }
    // the peel under the edge
    drawRoundRect(Color(0xFFB9824B), Offset(cx - r * 1.3f, cy + r * 0.05f), Size(r * 0.9f, 18f), CornerRadius(8f))
    phone(cx + r * 0.2f, cy + h * 0.2f, -70f, 1.4f)
    ring(w - 60f, 60f, 32f, (t / 3f).coerceAtMost(1f))
}

/** A slice pulled away, cheese stretching between it and the pizza. */
private fun DrawScope.stepSlice(t: Float, cx: Float, cy: Float, w: Float, h: Float) {
    val r = w * 0.3f
    pizza(cx, cy, r, 0.42f)
    val pull = (t / 2.5f).coerceIn(0f, 1f)
    val sx = cx + r * 0.4f + pull * w * 0.22f
    val sy = cy - pull * h * 0.12f
    // the gap where the slice was
    drawPath(Path().apply { moveTo(cx, cy); lineTo(cx + r, cy - r * 0.2f); lineTo(cx + r, cy + r * 0.25f); close() }, WOOD)
    // cheese strands
    for (i in 0 until 4) {
        drawLine(CHEESE, Offset(cx + r * 0.3f, cy - 10f + i * 10f), Offset(sx - 10f, sy - 10f + i * 10f), 5f - i)
    }
    val slice = Path().apply { moveTo(sx - r * 0.1f, sy); lineTo(sx + r * 0.75f, sy - r * 0.22f); lineTo(sx + r * 0.75f, sy + r * 0.28f); close() }
    drawPath(slice, SAUCE)
    drawLine(CRUST, Offset(sx + r * 0.75f, sy - r * 0.22f), Offset(sx + r * 0.75f, sy + r * 0.28f), 18f)
    phone(cx - w * 0.3f, cy - h * 0.15f, 30f, 1.4f)
    ring(w - 60f, 60f, 32f, (t / 3.5f).coerceAtMost(1f))
}

private fun DrawScope.pizza(cx: Float, cy: Float, r: Float, squash: Float) {
    drawOval(Color(0xFF9C6533), Offset(cx - r, cy - r * squash + 6f), Size(2 * r, 2 * r * squash))
    drawOval(CRUST, Offset(cx - r, cy - r * squash), Size(2 * r, 2 * r * squash))
    val s = r * 0.8f
    drawOval(SAUCE, Offset(cx - s, cy - s * squash), Size(2 * s, 2 * s * squash))
    for ((dx, dy) in listOf(-0.35f to -0.2f, 0.25f to -0.1f, -0.05f to 0.3f, 0.4f to 0.25f, -0.4f to 0.2f)) {
        drawOval(CHEESE, Offset(cx + dx * r - r * 0.12f, cy + dy * r * squash - r * 0.12f * squash), Size(r * 0.24f, r * 0.24f * squash))
    }
}

private fun DrawScope.phone(x: Float, y: Float, angleDeg: Float, scale: Float) {
    rotate(angleDeg, Offset(x, y)) {
        drawRoundRect(Color(0xFFE9EDF1), Offset(x - 15 * scale, y - 28 * scale), Size(30 * scale, 56 * scale), CornerRadius(7 * scale))
        drawRoundRect(Color(0xFF1A1E23), Offset(x - 12 * scale, y - 24 * scale), Size(24 * scale, 48 * scale), CornerRadius(4 * scale))
        drawCircle(Color(0xFFFF3B30), 3.5f * scale, Offset(x, y - 16 * scale))
    }
}

private fun DrawScope.ring(x: Float, y: Float, r: Float, p: Float) {
    drawCircle(Color.White.copy(alpha = 0.15f), r, Offset(x, y), style = Stroke(7f))
    drawArc(Pal.flame, -90f, 360f * p, false, Offset(x - r, y - r), Size(2 * r, 2 * r), style = Stroke(7f))
}

private fun DrawScope.stepAbove(t: Float, cx: Float, cy: Float, w: Float, h: Float) {
    val r = w * 0.32f
    pizza(cx, cy, r, 0.42f)
    val py = h * 0.18f + sin(t * 2) * 3f
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
    drawLine(Pal.flame.copy(alpha = 0.8f), Offset(cx - 26f, py + 10f), Offset(cx - r, cy), 3f, pathEffect = dash)
    drawLine(Pal.flame.copy(alpha = 0.8f), Offset(cx + 26f, py + 10f), Offset(cx + r, cy), 3f, pathEffect = dash)
    phone(cx, py, 90f, 1.5f)
    ring(w - 60f, 60f, 32f, (t / 2.5f).coerceAtMost(1f))
}

private fun DrawScope.stepAround(u: Float, cx: Float, cy: Float, w: Float, h: Float) {
    val rx = w * 0.4f
    val ry = rx * 0.42f
    val a = (PI / 2 + 2 * PI * u).toFloat()
    val px = cx + cos(a) * rx
    val ground = cy + sin(a) * ry
    val py = ground - h * 0.1f
    val behind = sin(a) < 0
    val sc = 1.1f + 0.4f * (sin(a) + 1) / 2
    drawOval(Color.White.copy(alpha = 0.3f), Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry), style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))))
    drawArc(Pal.flame, 90f, 360f * u, false, Offset(cx - rx, cy - ry), Size(2 * rx, 2 * ry), style = Stroke(5f))
    fun phoneAndSight() {
        drawOval(Color.Black.copy(alpha = 0.35f), Offset(px - 22f * sc, ground - 7f * sc), Size(44f * sc, 14f * sc))
        drawLine(Pal.flame.copy(alpha = 0.85f), Offset(px, py), Offset(cx, cy - 6f), 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        val ang = Math.toDegrees(atan2((cy - py).toDouble(), (cx - px).toDouble())).toFloat() - 90f
        phone(px, py, ang, sc)
    }
    if (behind) phoneAndSight()
    pizza(cx, cy, w * 0.29f, 0.42f)
    if (!behind) phoneAndSight()
    ring(w - 60f, 60f, 32f, u)
}

private fun DrawScope.stepRim(t: Float, w: Float, h: Float) {
    // close-up of the rim with a finger pressing it, crunch shown as sound bars
    val cy = h * 0.55f
    drawRect(SAUCE, Offset(0f, h * 0.38f), Size(w, h * 0.2f))
    val rim = Path().apply {
        moveTo(0f, cy); cubicTo(w * 0.3f, cy - h * 0.12f, w * 0.7f, cy - h * 0.12f, w, cy)
        lineTo(w, cy + h * 0.14f); cubicTo(w * 0.7f, cy + h * 0.05f, w * 0.3f, cy + h * 0.05f, 0f, cy + h * 0.14f); close()
    }
    drawPath(rim, CRUST)
    for (i in 0 until 14) drawCircle(Color(0xFF2A1A0E), 4f + (i % 4) * 2f, Offset(w * (0.06f + i * 0.067f), cy - h * 0.02f + ((i * 37) % 5) * 6f))
    val presses = listOf(0.3f, 1.2f, 2.1f)
    var depth = 0f
    presses.forEach { s -> val u = (t - s) / 0.45f; if (u in 0f..1f) depth = sin(PI.toFloat() * u) }
    val fx = w * 0.5f
    val tip = h * 0.28f + depth * h * 0.12f
    drawRoundRect(Color(0xFFE3AB84), Offset(fx - 34f, -10f), Size(68f, tip + 10f), CornerRadius(34f))
    drawRoundRect(Color(0xFFF4D4C0), Offset(fx - 20f, tip - 44f), Size(40f, 32f), CornerRadius(12f))
    phone(w * 0.86f, h * 0.2f, -20f, 1.4f)
    // crunch bars
    val n = 24
    for (i in 0 until n) {
        var e = 0f
        presses.forEach { s -> val d = t - s - 0.15f; if (d in 0f..0.6f) e = maxOf(e, kotlin.math.exp(-d * 7)) }
        val noise = 0.3f + 0.7f * kotlin.math.abs(sin(i * 12.9898f + (t * 24).toInt() * 78.233f))
        val bh = maxOf(6f, h * 0.12f * e * noise)
        drawRect(if (e > 0.06f) Pal.flame else Color.White.copy(alpha = 0.25f), Offset(w * 0.08f + i * w * 0.84f / n, h * 0.9f - bh / 2), Size(w * 0.84f / n - 4f, bh))
    }
}
