package com.vibepizzaiolo.ui.ferment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.dough.DoughState
import com.vibepizzaiolo.dough.FermentPlan
import com.vibepizzaiolo.dough.FermentPoint
import com.vibepizzaiolo.dough.FermentResult
import com.vibepizzaiolo.dough.Fermentation
import com.vibepizzaiolo.ui.Pal
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

val Cold = Color(0xFF6FB7FF)

private class Panel(
    val title: String,
    val heightDp: Float,
    val lo: Double,
    val hi: Double,
    val ticks: List<Double>,
    val label: (Double) -> String,
    val series: List<Pair<Color, (FermentPoint) -> Double>>,
    val guides: List<Pair<Double, Color>> = emptyList(),
)

/**
 * Four panels sharing the hours since mixing: temperature (room and dough), rise, pH, gluten.
 * Green bands: ready to bake; red: over-fermented. Tap or drag to read the values at an hour.
 */
@Composable
fun FermentChart(r: FermentResult, plan: FermentPlan, selected: Double?, onSelect: (Double) -> Unit, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val pts = r.points
    val total = max(plan.totalHours, 0.25)
    val panels = remember(r) {
        val temps = pts.flatMap { listOf(it.roomC, it.doughC) }
        val tLo = floor((temps.min() - 2) / 5) * 5
        val tHi = ceil((temps.max() + 2) / 5) * 5
        val riseHi = max(150.0, ceil(pts.maxOf { it.rise } / 50) * 50)
        val phLo = min(5.0, floor(pts.minOf { it.ph } * 10 - 1) / 10)
        listOf(
            Panel(
                "Temperatura °C", 70f, tLo, tHi, niceTicks(tLo, tHi, 3), { fmt(it, 0) },
                listOf(Pal.muted to { p: FermentPoint -> p.roomC }, Pal.ember to { p: FermentPoint -> p.doughC }),
            ),
            Panel(
                "Przyrost objętości %", 130f, 0.0, riseHi, niceTicks(0.0, riseHi, 4), { fmt(it, 0) },
                listOf(Pal.flame to { p: FermentPoint -> p.rise }),
                guides = listOf(100.0 to Pal.fg.copy(alpha = 0.35f)),
            ),
            Panel(
                "pH", 90f, phLo, 6.1, niceTicks(phLo, 6.1, 3), { fmt(it, 1) },
                listOf(Pal.basil to { p: FermentPoint -> p.ph }),
            ),
            Panel(
                "Siła glutenu (umowna)", 100f, 0.0, 100.0, listOf(0.0, 50.0, 100.0), { fmt(it, 0) },
                listOf(Cold to { p: FermentPoint -> p.gluten }),
                guides = listOf(Fermentation.READY_GLUTEN to Pal.flame.copy(alpha = 0.5f), Fermentation.COLLAPSE_GLUTEN to Pal.tomato.copy(alpha = 0.6f)),
            ),
        )
    }
    val gap = 14f
    val axis = 22f
    val heightDp = panels.sumOf { it.heightDp.toDouble() }.toFloat() + gap * panels.size + axis
    val left = 38.dp
    val right = 10.dp

    Canvas(
        modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .semantics { contentDescription = "Wykres fermentacji" }
            .pointerInput(r) {
                val l = left.toPx(); val w = size.width - l - right.toPx()
                detectTapGestures { onSelect(((it.x - l) / w * total).coerceIn(0.0, total)) }
            }
            .pointerInput(r) {
                val l = left.toPx(); val w = size.width - l - right.toPx()
                detectHorizontalDragGestures { change, _ ->
                    onSelect(((change.position.x - l) / w * total).coerceIn(0.0, total))
                }
            },
    ) {
        val l = left.toPx()
        val w = size.width - l - right.toPx()
        fun xOf(h: Double) = l + (h / total * w).toFloat()
        val small = TextStyle(color = Pal.muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        val titleStyle = TextStyle(color = Pal.fg.copy(alpha = 0.85f), fontSize = 11.sp)
        val step = max(1, pts.size / 500)
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

        // panels' vertical extents
        var y = 0f
        val boxes = panels.map { p ->
            val top = y + gap.dp.toPx()
            val bottom = top + p.heightDp.dp.toPx()
            y = bottom
            top to bottom
        }
        val chartTop = boxes.first().first
        val chartBottom = boxes.last().second

        // ready and over-fermented bands across all panels
        bands(pts, DoughState.READY).forEach { (a, b) ->
            drawRect(Pal.basil.copy(alpha = 0.13f), Offset(xOf(a), chartTop), Size(xOf(b) - xOf(a), chartBottom - chartTop))
        }
        bands(pts, DoughState.OVER).forEach { (a, b) ->
            drawRect(Pal.tomato.copy(alpha = 0.12f), Offset(xOf(a), chartTop), Size(xOf(b) - xOf(a), chartBottom - chartTop))
        }
        // stage boundaries
        var acc = 0.0
        plan.stages.dropLast(1).forEach { s ->
            acc += s.hours
            drawLine(Pal.line, Offset(xOf(acc), chartTop), Offset(xOf(acc), chartBottom), 1.5f, pathEffect = dash)
        }

        panels.forEachIndexed { i, p ->
            val (top, bottom) = boxes[i]
            fun yOf(v: Double) = bottom - ((v - p.lo) / (p.hi - p.lo)).coerceIn(-0.05, 1.05).toFloat() * (bottom - top)
            drawText(tm, p.title, Offset(l + 2f, top - 13.dp.toPx()), titleStyle)
            p.ticks.forEach { t ->
                drawLine(Pal.line.copy(alpha = 0.7f), Offset(l, yOf(t)), Offset(l + w, yOf(t)), 1f)
                label(tm, p.label(t), Offset(l - 4.dp.toPx(), yOf(t)), small)
            }
            p.guides.forEach { (v, c) -> drawLine(c, Offset(l, yOf(v)), Offset(l + w, yOf(v)), 2f, pathEffect = dash) }
            p.series.forEach { (color, value) ->
                val path = Path()
                var first = true
                for (k in pts.indices step step) {
                    val pt = pts[k]
                    val px = xOf(pt.h); val py = yOf(value(pt))
                    if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
                }
                path.lineTo(xOf(pts.last().h), yOf(value(pts.last())))
                drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // hours axis
        val hourTicks = hourTicks(total)
        hourTicks.forEach { h ->
            val tx = xOf(h)
            drawLine(Pal.line, Offset(tx, chartBottom), Offset(tx, chartBottom + 4.dp.toPx()), 1f)
            val tl = tm.measure("${fmtNum(h)} h", small)
            drawText(tl, topLeft = Offset((tx - tl.size.width / 2f).coerceIn(0f, size.width - tl.size.width), chartBottom + 6.dp.toPx()))
        }

        // the hour being read
        if (selected != null) {
            val sx = xOf(selected)
            drawLine(Pal.fg.copy(alpha = 0.8f), Offset(sx, chartTop), Offset(sx, chartBottom), 1.5f)
            val pt = r.at(selected)
            panels.forEachIndexed { i, p ->
                val (top, bottom) = boxes[i]
                p.series.forEach { (color, value) ->
                    val v = ((value(pt) - p.lo) / (p.hi - p.lo)).coerceIn(0.0, 1.0).toFloat()
                    drawCircle(color, 4.dp.toPx(), Offset(sx, bottom - v * (bottom - top)))
                }
            }
        }
    }
}

/** Right-aligned at [end], centred vertically on it. */
private fun DrawScope.label(tm: TextMeasurer, text: String, end: Offset, style: TextStyle) {
    val m = tm.measure(text, style)
    drawText(m, topLeft = Offset(end.x - m.size.width, end.y - m.size.height / 2f))
}

/** Hours where the dough is in [state], as (from, to) spans. */
fun bands(pts: List<FermentPoint>, state: DoughState): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>()
    var start: Double? = null
    for (i in pts.indices) {
        val inside = pts[i].state == state
        if (inside && start == null) start = pts[i].h
        if (!inside && start != null) { out += start to pts[i].h; start = null }
    }
    if (start != null) out += start to pts.last().h
    return out
}

private fun niceTicks(lo: Double, hi: Double, n: Int): List<Double> {
    val raw = (hi - lo) / n
    val mag = Math.pow(10.0, floor(Math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val out = ArrayList<Double>()
    var v = ceil(lo / step - 1e-9) * step
    while (v <= hi + 1e-9) { out += v; v += step }
    return out
}

private fun hourTicks(total: Double): List<Double> {
    val step = when {
        total <= 3 -> 0.5
        total <= 8 -> 1.0
        total <= 16 -> 2.0
        total <= 36 -> 6.0
        total <= 96 -> 12.0
        else -> 24.0
    }
    return generateSequence(0.0) { it + step }.takeWhile { it <= total + 1e-9 }.toList()
}
