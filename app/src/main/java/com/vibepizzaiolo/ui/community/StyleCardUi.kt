package com.vibepizzaiolo.ui.community

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.Oven
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.StyleCard
import com.vibepizzaiolo.ui.Mono
import com.vibepizzaiolo.ui.Pal

private val Crust = Color(0xFFDEAA68)
private val Tomato = Color(0xFFCE3422)
private val Cream = Color(0xFFF6ECD4)

// ---- how spicy ----

private val spiceNames = listOf("Mild", "A little", "Piccante", "Calabrese")

/** Four chilies to tap; the flames behind them grow with the heat. */
@Composable
fun SpicePicker(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val flicker by rememberInfiniteTransition(label = "flame").animateFloat(
        0.9f, 1.1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "flicker",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            for (level in 0..StyleCard.MAX_SPICE) {
                val on = level <= value && value > 0 || level == 0 && value == 0
                val grow by animateFloatAsState(
                    if (on) 1f else 0.72f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "chili",
                )
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(if (level == value) Pal.raised else Pal.card)
                        .clickable { onChange(level) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (level > 0) {
                        Canvas(Modifier.fillMaxSize()) {
                            val h = size.height * (0.25f + 0.2f * level) * grow * (if (on) flicker else 0.6f)
                            flame(Offset(size.width / 2, size.height * 0.92f), size.width * 0.34f, h, if (on) 0.5f else 0.12f)
                        }
                    }
                    Text(
                        if (level == 0) "🌿" else "🌶", fontSize = 26.sp,
                        modifier = Modifier.graphicsLayer { scaleX = grow; scaleY = grow; alpha = if (on) 1f else 0.45f },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(value, label = "spice name") { Text(spiceNames[it.coerceIn(0, 3)], color = Pal.ember, fontWeight = FontWeight.SemiBold) }
    }
}

private fun DrawScope.flame(base: Offset, halfWidth: Float, height: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(base.x - halfWidth, base.y)
        quadraticTo(base.x - halfWidth * 1.1f, base.y - height * 0.55f, base.x, base.y - height)
        quadraticTo(base.x + halfWidth * 1.1f, base.y - height * 0.55f, base.x + halfWidth, base.y)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Pal.flame.copy(alpha = alpha), Pal.tomato.copy(alpha = alpha)), base.y - height, base.y))
}

// ---- red or white ----

/** A pizza whose sauce pours from red to white as you choose; "both" splits it down the middle. */
@Composable
fun BasePicker(value: Base, onChange: (Base) -> Unit, modifier: Modifier = Modifier, allowBoth: Boolean = true, pizzaSize: Int = 150) {
    val left by animateColorAsState(if (value == Base.BIANCA) Cream else Tomato, tween(450), label = "left")
    val right by animateColorAsState(if (value == Base.ROSSA) Tomato else Cream, tween(450), label = "right")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(pizzaSize.dp)) {
            val r = size.minDimension / 2
            drawCircle(Crust, r)
            drawArc(left, 90f, 180f, true, Offset(r * 0.16f, r * 0.16f), Size(r * 1.68f, r * 1.68f))
            drawArc(right, -90f, 180f, true, Offset(r * 0.16f, r * 0.16f), Size(r * 1.68f, r * 1.68f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (b in Base.entries) {
                if (b == Base.BOTH && !allowBoth) continue
                Pill(b.label, selected = b == value, color = if (b == Base.BIANCA) Cream else if (b == Base.ROSSA) Pal.tomato else Pal.flame) { onChange(b) }
            }
        }
    }
}

// ---- which tradition ----

@Composable
fun StylePicker(value: PizzaStyle, onChange: (PizzaStyle) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (s in PizzaStyle.entries) {
            val picked = s == value
            val lift by animateFloatAsState(if (picked) 1.05f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "style")
            Column(
                Modifier.width(132.dp).graphicsLayer { scaleX = lift; scaleY = lift }
                    .clip(RoundedCornerShape(16.dp)).background(if (picked) Pal.raised else Pal.card)
                    .border(1.5.dp, if (picked) Pal.flame else Pal.line, RoundedCornerShape(16.dp))
                    .clickable { onChange(s) }.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.size(72.dp)) { silhouette(s) }
                Spacer(Modifier.height(8.dp))
                Text(s.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
                Text(s.hint, color = Pal.muted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 14.sp)
            }
        }
    }
}

/** Each tradition's shape, seen from above. */
private fun DrawScope.silhouette(style: PizzaStyle) {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2, h / 2)
    when (style) {
        PizzaStyle.NAPOLETANA -> { drawCircle(Crust, w * 0.46f, c); drawCircle(Tomato, w * 0.31f, c) }
        PizzaStyle.ROMANA -> { drawCircle(Crust, w * 0.48f, c); drawCircle(Tomato, w * 0.43f, c) }
        PizzaStyle.TEGLIA -> {
            drawRoundRect(Crust, Offset(w * 0.04f, h * 0.2f), Size(w * 0.92f, h * 0.6f), CornerRadius(8f))
            drawRoundRect(Tomato, Offset(w * 0.1f, h * 0.27f), Size(w * 0.8f, h * 0.46f), CornerRadius(6f))
        }
        PizzaStyle.PINSA -> {
            drawOval(Crust, Offset(w * 0.02f, h * 0.2f), Size(w * 0.96f, h * 0.6f))
            drawOval(Cream, Offset(w * 0.1f, h * 0.28f), Size(w * 0.8f, h * 0.44f))
        }
        PizzaStyle.NEW_YORK -> {
            val slice = Path().apply { moveTo(w * 0.5f, h * 0.96f); lineTo(w * 0.1f, h * 0.12f); lineTo(w * 0.9f, h * 0.12f); close() }
            drawPath(slice, Tomato)
            drawRoundRect(Crust, Offset(w * 0.06f, h * 0.04f), Size(w * 0.88f, h * 0.14f), CornerRadius(10f))
        }
        PizzaStyle.DETROIT -> {
            drawRoundRect(Color(0xFF8A4B22), Offset(w * 0.1f, h * 0.1f), Size(w * 0.8f, h * 0.8f), CornerRadius(8f))
            drawRoundRect(Cream, Offset(w * 0.17f, h * 0.17f), Size(w * 0.66f, h * 0.66f), CornerRadius(5f))
            drawRect(Tomato, Offset(w * 0.3f, h * 0.17f), Size(w * 0.12f, h * 0.66f))
            drawRect(Tomato, Offset(w * 0.58f, h * 0.17f), Size(w * 0.12f, h * 0.66f))
        }
        PizzaStyle.CHICAGO -> {
            drawCircle(Color(0xFF8A4B22), w * 0.47f, c); drawCircle(Crust, w * 0.41f, c); drawCircle(Tomato, w * 0.33f, c)
        }
        PizzaStyle.OTHER -> {
            drawCircle(Crust, w * 0.44f, c); drawCircle(Pal.raised, w * 0.3f, c)
        }
    }
}

// ---- how hot ----

/** The slider's glow goes from orange embers to white-hot as the temperature climbs. */
@Composable
fun HeatPicker(tempC: Int, oven: Oven, onTemp: (Int) -> Unit, onOven: (Oven) -> Unit, modifier: Modifier = Modifier, glow: Boolean = true) {
    val t = ((tempC - StyleCard.TEMP_MIN).toFloat() / (StyleCard.TEMP_MAX - StyleCard.TEMP_MIN)).coerceIn(0f, 1f)
    val hot = lerp(lerp(Pal.ember, Pal.flame, (t * 2).coerceAtMost(1f)), Color.White, ((t - 0.5f) * 2).coerceIn(0f, 1f))
    val pulse by rememberInfiniteTransition(label = "heat").animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween((1400 - t * 900).toInt()), RepeatMode.Reverse), label = "pulse",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            if (glow) {
                Canvas(Modifier.size(170.dp)) {
                    val r = size.minDimension / 2 * (0.55f + 0.45f * t) * pulse
                    drawCircle(Brush.radialGradient(listOf(hot.copy(alpha = 0.55f), hot.copy(alpha = 0f)), radius = r), r)
                }
            }
            Text("$tempC °C", color = hot, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = if (glow) 40.sp else 22.sp)
        }
        Slider(
            value = tempC.toFloat(), onValueChange = { onTemp((it / 10).toInt() * 10) },
            valueRange = StyleCard.TEMP_MIN.toFloat()..StyleCard.TEMP_MAX.toFloat(),
            colors = SliderDefaults.colors(thumbColor = hot, activeTrackColor = hot, inactiveTrackColor = Pal.raised),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in Oven.entries) Pill(o.label, selected = o == oven) { onOven(o) }
        }
    }
}

// ---- the quiz after signing up, and the editor later ----

private val questions = listOf("How spicy are you?", "Red or white?", "Which pizza is yours?", "How hot do you bake?")

/** Four questions, one screen each, sliding sideways. */
@Composable
fun StyleQuiz(name: String, busy: Boolean, onDone: (StyleCard) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var card by remember { mutableStateOf(StyleCard()) }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ciao $name!", color = Pal.muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(questions.size) { i ->
                val wide by animateFloatAsState(if (i == step) 28f else 8f, label = "dot")
                Box(Modifier.height(8.dp).width(wide.dp).clip(CircleShape).background(if (i <= step) Pal.flame else Pal.raised))
            }
        }
        AnimatedContent(
            step, modifier = Modifier.weight(1f).fillMaxWidth(), label = "quiz",
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir } + fadeOut())
            },
        ) { s ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(questions[s], fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(28.dp))
                when (s) {
                    0 -> SpicePicker(card.spice, { card = card.copy(spice = it) })
                    1 -> BasePicker(card.base, { card = card.copy(base = it) })
                    2 -> StylePicker(card.style, { card = card.copy(style = it) })
                    else -> HeatPicker(card.tempC, card.oven, { card = card.copy(tempC = it) }, { card = card.copy(oven = it) })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Back", color = Pal.muted) }
            Spacer(Modifier.weight(1f))
            BigButton(
                if (step < questions.lastIndex) "Next" else "That's me", Modifier.width(180.dp), busy = busy,
            ) { if (step < questions.lastIndex) step++ else onDone(card) }
        }
    }
}

/** All four on one scrolling page, for changing your style later. */
@Composable
fun StyleEditor(initial: StyleCard, busy: Boolean, onSave: (StyleCard) -> Unit, onClose: () -> Unit) {
    var card by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your style", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Cancel", color = Pal.muted) }
        }
        SpicePicker(card.spice, { card = card.copy(spice = it) })
        BasePicker(card.base, { card = card.copy(base = it) }, pizzaSize = 110)
        StylePicker(card.style, { card = card.copy(style = it) })
        HeatPicker(card.tempC, card.oven, { card = card.copy(tempC = it) }, { card = card.copy(oven = it) })
        BigButton("Save", busy = busy) { onSave(card) }
    }
}
