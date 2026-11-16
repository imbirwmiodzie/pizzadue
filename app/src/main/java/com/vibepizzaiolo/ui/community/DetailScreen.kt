package com.vibepizzaiolo.ui.community

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.DetailUi
import com.vibepizzaiolo.community.Pizzaiolo
import com.vibepizzaiolo.community.Rules
import com.vibepizzaiolo.community.badge
import com.vibepizzaiolo.community.inCommonWith
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.formatClock

class DetailActions(
    val close: () -> Unit, val rate: (Boolean) -> Unit, val comment: (String) -> Unit,
    val invite: () -> Unit, val report: (String) -> Unit, val block: () -> Unit,
)

/** One pizza: the photo, how it was baked, what people think, comments, and "Mangiamo insieme?". */
@Composable
fun DetailScreen(d: DetailUi, me: Pizzaiolo?, busy: Boolean, shared: Shared?, a: DetailActions) {
    val p = d.pizza
    var menu by remember { mutableStateOf(false) }
    var confirmReport by remember { mutableStateOf(false) }
    var draft by rememberSaveable(p.id) { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box {
                PizzaPhoto(p.photo, Modifier.fillMaxWidth().aspectRatio(1f).sharedPhoto(shared, p.id))
                IconButton(onClick = a.close, modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(50)).background(Pal.bg.copy(alpha = 0.6f))) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.fg)
                }
                if (!p.mine) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        IconButton(onClick = { menu = true }, modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.bg.copy(alpha = 0.6f))) {
                            Icon(Icons.Default.MoreVert, "More", tint = Pal.fg)
                        }
                        DropdownMenu(menu, { menu = false }, containerColor = Pal.card) {
                            DropdownMenuItem({ Text("Report this pizza") }, { menu = false; confirmReport = true })
                            DropdownMenuItem({ Text("Block ${p.author.name}") }, { menu = false; a.block() })
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(p.type.label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    "by ${if (p.mine) "you" else p.author.name}" + (if (p.author.demo) " · sample pizzaiolo" else "") + " · ${ago(p.createdAt)}",
                    color = Pal.muted,
                )
                if (p.caption.isNotBlank()) Text(p.caption)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fact(p.style.label); Fact(p.base.label.lowercase()); Fact("${p.tempC} °C ${p.oven.label}")
                    Fact(if (p.spice == 0) "mild" else "🌶".repeat(p.spice))
                    p.bakeSec?.let { Fact("${formatClock(it * 1000L)} bake") }
                    p.hydration?.let { Fact("$it% hydration") }
                }

                LikeBar(p.likes, p.passes)

                if (!p.mine) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(if (p.mySwipe == false) "Passed" else "Passo", Modifier.weight(1f), color = if (p.mySwipe == false) Pal.tomato else Pal.raised) { a.rate(false) }
                        BigButton(if (p.mySwipe == true) "Buona! ♥" else "Buona!", Modifier.weight(1f), color = if (p.mySwipe == true) Pal.basil else Pal.raised) { a.rate(true) }
                    }
                    val common = me?.card?.let { mine -> p.author.card?.inCommonWith(mine) }.orEmpty()
                    val beat by rememberInfiniteTransition(label = "invite").animateFloat(
                        1f, 1.03f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat",
                    )
                    BigButton(
                        if (p.invited) "Invitation sent ✓" else "🍕 Mangiamo insieme?", enabled = !p.invited, busy = busy,
                        modifier = Modifier.graphicsLayer { if (!p.invited) { scaleX = beat; scaleY = beat } },
                    ) { a.invite() }
                    Text(
                        when {
                            p.invited -> "The answer and the chat are under Tavola."
                            common.isNotEmpty() -> "Ask ${p.author.name} to eat together: " + common.joinToString(", ") + "."
                            else -> "Ask ${p.author.name} to eat together. A chat opens once they say yes."
                        },
                        color = Pal.muted, fontSize = 12.sp,
                    )
                }
                p.author.card?.let { Text("${p.author.name}: ${it.badge()}", color = Pal.muted, fontSize = 12.sp) }

                Text("Comments", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                if (d.comments.isEmpty()) Text(if (d.loading) "…" else "Nobody said anything yet.", color = Pal.muted)
                for (c in d.comments) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.card).padding(10.dp)) {
                        Text("${if (c.mine) "You" else c.author} · ${ago(c.at)}", color = if (c.mine) Pal.flame else Pal.muted, fontSize = 11.sp)
                        Text(c.text)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(Pal.bg).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(draft, { if (it.length <= Rules.MAX_COMMENT) draft = it }, "Say something about this pizza", Modifier.weight(1f))
            IconButton(onClick = { a.comment(draft); draft = "" }, enabled = draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send comment", tint = if (draft.isNotBlank()) Pal.flame else Pal.muted)
            }
        }
    }

    if (confirmReport) {
        AlertDialog(
            onDismissRequest = { confirmReport = false }, containerColor = Pal.card,
            title = { Text("Report this pizza?") },
            text = { Text("It disappears for you and is sent for review. Use this for photos that are not pizza, or are offensive.", color = Pal.muted) },
            confirmButton = { TextButton(onClick = { confirmReport = false; a.report("not a pizza or offensive") }) { Text("Report", color = Pal.tomato) } },
            dismissButton = { TextButton(onClick = { confirmReport = false }) { Text("Cancel") } },
        )
    }
}

/** How the swipes fell: the green part grows to the share of likes. */
@Composable
private fun LikeBar(likes: Int, passes: Int) {
    val total = likes + passes
    val share by animateFloatAsState(if (total == 0) 0f else likes.toFloat() / total, tween(700), label = "likes")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(if (total == 0) Pal.raised else Pal.tomato.copy(alpha = 0.55f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(Pal.basil))
        }
        Row {
            Text(if (total == 0) "No swipes yet" else "♥ $likes buona", color = Pal.basil, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            if (total > 0) Text("✕ $passes passo", color = Pal.tomato, fontSize = 12.sp)
        }
    }
}
