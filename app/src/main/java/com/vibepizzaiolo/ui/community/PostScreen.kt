package com.vibepizzaiolo.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.PizzaDraft
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.PostUi
import com.vibepizzaiolo.community.Rules
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.SectionLabel

/** After the photo: the app sniffs out the type, proposes it, and you confirm the rest. */
@Composable
fun PostScreen(post: PostUi, onEdit: ((PizzaDraft) -> PizzaDraft) -> Unit, onPost: () -> Unit, onCancel: () -> Unit) {
    val d = post.draft
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Post your pizza", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel", color = Pal.muted) }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1.2f).clip(RoundedCornerShape(20.dp)).background(Pal.card), contentAlignment = Alignment.Center) {
            post.photo?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            if (post.sniffing) Sniffing()
        }

        AnimatedVisibility(!post.sniffing, enter = fadeIn() + expandVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val sniff = post.sniff
                Text(
                    when {
                        sniff == null || sniff.suggestions.isEmpty() -> "Which pizza is it?"
                        !sniff.isPizza -> "This does not look like a pizza. If it is, pick the type:"
                        else -> "Looks like ${sniff.suggestions.first().type.label}. Right?"
                    },
                    fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val proposed = sniff?.suggestions.orEmpty()
                    for (s in proposed) Pill("${s.type.label} ${s.confidence}%", selected = d.type == s.type) { onEdit { it.copy(type = s.type, base = s.type.base) } }
                    for (t in PizzaType.entries.filter { t -> proposed.none { it.type == t } }) {
                        Pill(t.label, selected = d.type == t) { onEdit { it.copy(type = t, base = t.base) } }
                    }
                }
                if (sniff?.source == "colours") {
                    Text("A rough guess from the colours in the photo, made on this phone.", color = Pal.muted, fontSize = 12.sp)
                }

                SectionLabel("Sauce")
                BasePicker(d.base, { b -> onEdit { it.copy(base = b) } }, Modifier.fillMaxWidth(), allowBoth = false, pizzaSize = 84)
                SectionLabel("How spicy")
                SpicePicker(d.spice, { s -> onEdit { it.copy(spice = s) } }, Modifier.fillMaxWidth())
                SectionLabel("Style")
                StylePicker(d.style, { s -> onEdit { it.copy(style = s) } })
                SectionLabel("Baked at")
                HeatPicker(d.tempC, d.oven, { t -> onEdit { it.copy(tempC = t) } }, { o -> onEdit { it.copy(oven = o) } }, glow = false)
                Field(d.caption, { c -> if (c.length <= Rules.MAX_CAPTION) onEdit { it.copy(caption = c) } }, "A line about it (optional)", singleLine = false)
                BigButton("Post ${d.type.label}", busy = post.posting, onClick = onPost)
            }
        }
    }
}

/** While the photo is being looked at: a ring sweeps round it and a band of light passes over. */
@Composable
private fun Sniffing() {
    val t = rememberInfiniteTransition(label = "sniff")
    val turn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "turn")
    val sweep by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "sweep")
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * sweep
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Pal.flame.copy(alpha = 0.45f), Color.Transparent), y - 60f, y + 60f),
                topLeft = Offset(0f, y - 60f), size = androidx.compose.ui.geometry.Size(size.width, 120f),
            )
            val r = size.minDimension * 0.3f
            drawArc(
                Pal.flame, turn, 110f, false, topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(10f, cap = StrokeCap.Round),
            )
        }
        Text("Sniffing your pizza…", color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}
