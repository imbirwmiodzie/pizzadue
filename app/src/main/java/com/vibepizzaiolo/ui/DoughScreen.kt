package com.vibepizzaiolo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.dough.DoughCalc
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.voice.VoiceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val BALL_WEIGHT_STEP = 10

@Composable
fun DoughScreen(
    s: Settings, voice: VoiceStatus, update: ((Settings) -> Settings) -> Unit, openSettings: () -> Unit,
    openSimulator: () -> Unit = {},
) {
    val r = DoughCalc.calc(s.balls, s.ballWeight.toDouble(), s.hydration.toDouble(), s.salt.toDouble(), s.yeast.toDouble())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Dough", voice)

        BigStepper(
            label = "Dough balls", value = s.balls.toString(), unit = "",
            onMinus = { update { it.copy(balls = (it.balls - 1).coerceAtLeast(1)) } },
            onPlus = { update { it.copy(balls = (it.balls + 1).coerceAtMost(99)) } },
        )
        BigStepper(
            label = "Ball weight", value = s.ballWeight.toString(), unit = "g",
            onMinus = { update { it.copy(ballWeight = (it.ballWeight - BALL_WEIGHT_STEP).coerceAtLeast(50)) } },
            onPlus = { update { it.copy(ballWeight = (it.ballWeight + BALL_WEIGHT_STEP).coerceAtMost(2000)) } },
        )

        Surface(
            onClick = openSettings, shape = RoundedCornerShape(14.dp), color = Pal.bg,
            border = BorderStroke(1.dp, Pal.line), modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "hydration ${pct(s.hydration)} · salt ${pct(s.salt)} · yeast ${pct(s.yeast)}",
                    fontFamily = Mono, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                Text("edit ›", color = Pal.flame, fontFamily = Mono, fontSize = 13.sp)
            }
        }

        Surface(color = Pal.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ResultRow("Flour", "100%", r.flour)
                GroupDivider()
                ResultRow("Water", pct(s.hydration), r.water)
                GroupDivider()
                ResultRow("Salt", pct(s.salt), r.salt)
                GroupDivider()
                ResultRow("Yeast", pct(s.yeast), r.yeast)
            }
        }
        Text(
            "TOTAL DOUGH ${Math.round(r.total)} G", color = Pal.muted, fontFamily = Mono, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
        )

        Surface(
            onClick = openSimulator, shape = RoundedCornerShape(16.dp), color = Pal.card,
            border = BorderStroke(1.dp, Pal.flame.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Symulator fermentacji", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ile drożdży i kiedy ciasto będzie gotowe: harmonogram, wzrost, pH i gluten na wykresie",
                        color = Pal.muted, fontSize = 12.sp,
                    )
                }
                Text("›", color = Pal.flame, fontSize = 26.sp)
            }
        }
    }
}

fun pct(v: Float): String {
    val r = Math.round(v * 100) / 100.0
    return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()) + "%"
}

@Composable
private fun BigStepper(label: String, value: String, unit: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Surface(color = Pal.card, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RepeatButton(Icons.Default.Remove, "Less: $label", onMinus)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label.uppercase(), color = Pal.muted, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
                    if (unit.isNotEmpty()) Text(" $unit", color = Pal.muted, fontFamily = Mono, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            RepeatButton(Icons.Default.Add, "More: $label", onPlus)
        }
    }
}

/** Tap for one step; hold to keep stepping, faster after a moment. */
@Composable
private fun RepeatButton(icon: ImageVector, description: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Pal.raised)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick { step(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        step()
                        delay(450)
                        var pause = 140L
                        while (true) {
                            step()
                            delay(pause)
                            pause = (pause - 15).coerceAtLeast(50)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Pal.fg, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ResultRow(name: String, percent: String, grams: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(name, fontSize = 16.sp)
        Text("  $percent", color = Pal.muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(DoughCalc.formatGrams(grams), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp)
        Text(" g", color = Pal.muted, fontFamily = Mono, fontSize = 14.sp)
    }
}
