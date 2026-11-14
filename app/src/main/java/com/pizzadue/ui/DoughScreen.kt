package com.pizzadue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pizzadue.dough.DoughCalc
import com.pizzadue.settings.Settings
import com.pizzadue.voice.VoiceStatus

@Composable
fun DoughScreen(s: Settings, voice: VoiceStatus, update: ((Settings) -> Settings) -> Unit, openSettings: () -> Unit) {
    val r = DoughCalc.calc(s.balls, s.ballWeight.toDouble(), s.hydration.toDouble(), s.salt.toDouble(), s.yeast.toDouble())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Dough", voice)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Dough balls", s.balls, Modifier.weight(1f)) { v -> update { it.copy(balls = v) } }
            NumberField("Ball weight, g", s.ballWeight, Modifier.weight(1f)) { v -> update { it.copy(ballWeight = v) } }
        }
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
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text("Say “dough for six balls” / “ciasto na sześć kulek”.", color = Pal.muted, fontSize = 13.sp)
    }
}

fun pct(v: Float): String {
    val r = Math.round(v * 100) / 100.0
    return (if (r == Math.floor(r)) r.toLong().toString() else r.toString()) + "%"
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

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onValue: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    // follow outside changes (voice command) without fighting the user's typing
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            val digits = t.filter { it.isDigit() }.take(4)
            text = digits
            digits.toIntOrNull()?.takeIf { it > 0 }?.let(onValue)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.flame, focusedLabelColor = Pal.flame),
        modifier = modifier,
    )
}
