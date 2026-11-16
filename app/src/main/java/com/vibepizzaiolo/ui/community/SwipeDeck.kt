package com.vibepizzaiolo.ui.community

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.Pizza
import com.vibepizzaiolo.ui.Pal
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The pile of pizzas to judge. The top card follows the finger and tilts; past a third of the
 * screen it flies off (right: Buona!, left: Passo) and the next one rises into its place.
 */
@Composable
fun SwipeDeck(
    deck: List<Pizza>, loading: Boolean, shared: Shared?,
    onSwipe: (Pizza, Boolean) -> Unit, onOpen: (Pizza) -> Unit, onRefresh: () -> Unit,
) {
    if (deck.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (loading) CircularProgressIndicator(color = Pal.flame) else {
                Text("🍽", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text("You have judged every pizza.", fontWeight = FontWeight.SemiBold)
                Text("New ones show up here. The gallery has them all.", color = Pal.muted, textAlign = TextAlign.Center)
                TextButton(onClick = onRefresh) { Text("Look again", color = Pal.flame) }
            }
        }
        return
    }
    val top = deck.first()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // a fresh offset for every new top card
        val offset = remember(top.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
        val progress = (abs(offset.value.x) / (widthPx * 0.33f)).coerceIn(0f, 1f)

        fun fling(like: Boolean) = scope.launch {
            offset.animateTo(Offset(if (like) widthPx * 1.6f else -widthPx * 1.6f, offset.value.y), tween(260))
            onSwipe(top, like)
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // the next card waits underneath, a little smaller, and grows as the top one leaves
                deck.getOrNull(1)?.let { next ->
                    key(next.id) {
                        PizzaCard(next, null, Modifier.fillMaxSize().graphicsLayer {
                            val s = 0.93f + 0.07f * progress
                            scaleX = s; scaleY = s; alpha = 0.6f + 0.4f * progress
                        })
                    }
                }
                key(top.id) {
                    PizzaCard(
                        top, shared,
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                translationX = offset.value.x
                                translationY = offset.value.y
                                rotationZ = offset.value.x / widthPx * 18f
                            }
                            .pointerInput(top.id) {
                                detectDragGestures(
                                    onDrag = { change, drag ->
                                        change.consume()
                                        scope.launch { offset.snapTo(offset.value + drag) }
                                    },
                                    onDragEnd = {
                                        val x = offset.value.x
                                        if (abs(x) > widthPx * 0.33f) fling(x > 0)
                                        else scope.launch { offset.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
                                    },
                                    onDragCancel = { scope.launch { offset.animateTo(Offset.Zero) } },
                                )
                            }
                            .clickable { onOpen(top) },
                        stamp = if (offset.value.x > 0) progress else -progress,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Pal.tomato, onClick = { fling(false) }) { Icon(Icons.Default.Close, "Pass", tint = Pal.tomato, modifier = Modifier.size(30.dp)) }
                TextButton(onClick = { onOpen(top) }) { Text("💬 ${top.comments}  ·  details", color = Pal.muted) }
                RoundButton(Pal.basil, onClick = { fling(true) }) { Icon(Icons.Default.Favorite, "Like", tint = Pal.basil, modifier = Modifier.size(30.dp)) }
            }
        }
    }
}

@Composable
private fun RoundButton(color: Color, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(Pal.card).border(2.dp, color.copy(alpha = 0.7f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}

/** One pizza as a card. [stamp] is how far it is dragged: +1 fully right (Buona!), −1 fully left (Passo). */
@Composable
fun PizzaCard(pizza: Pizza, shared: Shared?, modifier: Modifier = Modifier, stamp: Float = 0f) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Pal.card)) {
        PizzaPhoto(pizza.photo, Modifier.fillMaxSize().sharedPhoto(shared, pizza.id))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xE6000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(pizza.type.label, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("by ${pizza.author.name}" + if (pizza.author.demo) " · sample" else "", color = Color(0xCCFFFFFF))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Fact(pizza.style.label)
                Fact(pizza.base.label.lowercase())
                Fact("${pizza.tempC} °C")
                if (pizza.spice > 0) Fact("🌶".repeat(pizza.spice))
            }
        }
        if (stamp > 0.02f) Stamp("BUONA!", Pal.basil, stamp, -14f, Modifier.align(Alignment.TopStart).padding(22.dp))
        if (stamp < -0.02f) Stamp("PASSO", Pal.tomato, -stamp, 14f, Modifier.align(Alignment.TopEnd).padding(22.dp))
    }
}

@Composable
private fun Stamp(text: String, color: Color, strength: Float, tilt: Float, modifier: Modifier) {
    Text(
        text, color = color, fontSize = 34.sp, fontWeight = FontWeight.Black,
        modifier = modifier
            .graphicsLayer { alpha = strength; rotationZ = tilt; val s = 1.3f - 0.3f * strength; scaleX = s; scaleY = s }
            .border(4.dp, color, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
    )
}
