@file:OptIn(ExperimentalLayoutApi::class)

package com.vibepizzaiolo.ui.ferment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibepizzaiolo.dough.DoughState
import com.vibepizzaiolo.dough.FermentPlan
import com.vibepizzaiolo.dough.FermentPresets
import com.vibepizzaiolo.dough.FermentResult
import com.vibepizzaiolo.dough.FermentViewModel
import com.vibepizzaiolo.dough.Fermentation
import com.vibepizzaiolo.dough.Stage
import com.vibepizzaiolo.dough.YeastType
import com.vibepizzaiolo.ui.Group
import com.vibepizzaiolo.ui.GroupDivider
import com.vibepizzaiolo.ui.Mono
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.SectionLabel
import com.vibepizzaiolo.ui.community.BigButton
import com.vibepizzaiolo.ui.community.Pill
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

private val PL: Locale = Locale.forLanguageTag("pl-PL")

fun fmt(v: Double, decimals: Int): String = String.format(PL, "%.${decimals}f", v)

/** Whole numbers without decimals, the rest with one. */
fun fmtNum(v: Double): String = if (kotlin.math.abs(v - Math.round(v)) < 1e-6) Math.round(v).toString() else fmt(v, 1)

fun fmtHours(h: Double): String = if (h < 1) "${Math.round(h * 60)} min" else "${fmtNum(Math.round(h * 4) / 4.0)} h"

/** Grams: two decimals under 1 g (yeast), one under 50 g, whole above. */
fun fmtGrams(g: Double): String = when {
    g < 1 -> fmt(g, 2)
    g < 50 -> fmt(g, 1)
    else -> Math.round(g).toString()
}

/** A percentage of flour with as many decimals as small amounts of yeast need. */
fun fmtPct(v: Double): String = when {
    v < 0.1 -> fmt(v, 3)
    v < 1 -> fmt(v, 2)
    else -> fmt(v, 1)
} + "%"

private val STEPS = listOf("Styl", "Ciasto", "Czas", "Drożdże", "Wynik")

/** What the Dough tab gets when the result is saved: balls, ball weight, hydration, salt, yeast %. */
data class DoughSettingsFromPlan(val balls: Int, val ballWeight: Int, val hydration: Float, val salt: Float, val yeast: Float)

/** The fermentation simulator, a five-step wizard on the Dough tab. */
@Composable
fun FermentWizard(vm: FermentViewModel, onSaveToDough: (DoughSettingsFromPlan) -> Unit) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    val step by vm.step.collectAsStateWithLifecycle()
    BackHandler { vm.back() }
    val result = remember(plan) { Fermentation.simulate(plan) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::close) { Icon(Icons.Default.Close, contentDescription = "Zamknij symulator", tint = Pal.fg) }
            Column(Modifier.weight(1f)) {
                Text("Symulator fermentacji", style = MaterialTheme.typography.titleMedium)
                Text("model przybliżony, edukacyjny", color = Pal.muted, fontSize = 11.sp)
            }
            Text("${step + 1}/${STEPS.size}", color = Pal.muted, fontFamily = Mono, fontSize = 12.sp)
        }
        StepBar(step, vm::goTo)

        val scroll = rememberScrollState()
        LaunchedEffect(step) { scroll.scrollTo(0) }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                0 -> StyleStep(plan, vm)
                1 -> DoughStep(plan, vm::update)
                2 -> ScheduleStep(plan, result, vm::update)
                3 -> YeastStep(plan, result, vm::update)
                else -> ResultStep(plan, result, vm::update) { vm.goTo(it) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().background(Pal.bg).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BigButton(if (step == 0) "Zamknij" else "‹ Wstecz", Modifier.weight(1f), color = Pal.raised, onClick = vm::back)
            if (step < FermentViewModel.LAST_STEP) {
                BigButton(if (step == FermentViewModel.LAST_STEP - 1) "Symuluj ›" else "Dalej ›", Modifier.weight(1.4f), onClick = vm::next)
            } else {
                BigButton("Zapisz w Dough", Modifier.weight(1.4f), color = Pal.basil) {
                    val p = Fermentation.effective(plan)
                    val total = Fermentation.ingredients(p).total
                    onSaveToDough(
                        DoughSettingsFromPlan(
                            balls = p.balls,
                            ballWeight = Math.round(total / p.balls).toInt(),
                            hydration = p.hydration.toFloat(),
                            salt = p.salt.toFloat(),
                            yeast = (Math.round(p.yeastPct * 100) / 100.0).toFloat(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBar(step: Int, goTo: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        STEPS.forEachIndexed { i, name ->
            val color = when {
                i == step -> Pal.flame
                i < step -> Pal.flame.copy(alpha = 0.45f)
                else -> Pal.raised
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { goTo(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text(name, color = if (i == step) Pal.fg else Pal.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- step 1: style

@Composable
private fun StyleStep(plan: FermentPlan, vm: FermentViewModel) {
    Text("Od czego zaczynamy?", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Wybierz gotowy styl albo własny przepis. Wszystko zmienisz w kolejnych krokach.",
        color = Pal.muted, fontSize = 13.sp,
    )
    FermentPresets.all.forEach { p ->
        ChoiceCard(p.name, p.note, summary(p.plan)) { vm.applyPreset(p) }
    }
    ChoiceCard("Własny przepis", "zaczynasz od ostatnich ustawień", summary(plan), accent = Pal.basil) { vm.next() }
    Disclaimer()
}

private fun summary(p: FermentPlan) =
    "W ${p.w} · ${fmtNum(p.hydration)}% wody · ${fmtHours(p.totalHours)} · ${fmtPct(p.yeastPct)} ${p.yeastType.label.lowercase()}"

@Composable
private fun ChoiceCard(title: String, note: String, detail: String, accent: Color = Pal.flame, onClick: () -> Unit) {
    Surface(
        onClick = onClick, color = Pal.card, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Pal.line), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(note, color = Pal.muted, fontSize = 12.sp)
                Text(detail, color = Pal.muted, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text("›", color = accent, fontSize = 24.sp)
        }
    }
}

@Composable
private fun Disclaimer() {
    Surface(color = Pal.flame.copy(alpha = 0.10f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "To model przybliżony i edukacyjny, a nie dokładna symulacja. Mąki, drożdże, lodówki i kuchnie bardzo się różnią: " +
                "traktuj wynik jako punkt wyjścia i patrz na ciasto.",
            color = Pal.flame, fontSize = 12.sp, modifier = Modifier.padding(12.dp),
        )
    }
}

// ---------------------------------------------------------------- step 2: dough

@Composable
private fun DoughStep(plan: FermentPlan, update: ((FermentPlan) -> FermentPlan) -> Unit) {
    Text("Ciasto", style = MaterialTheme.typography.headlineSmall)
    val ing = Fermentation.ingredients(plan)
    Group {
        CompactStepper("Mąka", "cała partia", "${plan.flourG} g",
            onMinus = { update { it.copy(flourG = it.flourG - if (it.flourG > 1000) 100 else 50) } },
            onPlus = { update { it.copy(flourG = it.flourG + if (it.flourG >= 1000) 100 else 50) } })
        GroupDivider()
        CompactStepper("Kulki", "≈ ${Math.round(ing.total / plan.balls)} g na kulkę", plan.balls.toString(),
            onMinus = { update { it.copy(balls = it.balls - 1) } },
            onPlus = { update { it.copy(balls = it.balls + 1) } })
    }

    SectionLabel("Mąka i proporcje")
    Group {
        SliderRow(
            "Siła mąki W", flourHint(plan.w), "W ${plan.w}",
            plan.w.toFloat(), 180f..380f, 10f,
        ) { v -> update { it.copy(w = v.roundToInt()) } }
        GroupDivider()
        SliderRow(
            "Hydratacja", "woda w % mąki; więcej = szybsza fermentacja, słabsza struktura", "${fmtNum(plan.hydration)}%",
            plan.hydration.toFloat(), 55f..80f, 1f,
        ) { v -> update { it.copy(hydration = v.toDouble()) } }
        GroupDivider()
        SliderRow(
            "Sól", "zacieśnia gluten, spowalnia drożdże", fmt(plan.salt, 1) + "%",
            plan.salt.toFloat(), 0f..3.5f, 0.1f,
        ) { v -> update { it.copy(salt = v.toDouble()) } }
    }

    SectionLabel("Dodatki (opcjonalnie)")
    Group {
        SliderRow(
            "Cukier", "do ~2% przyspiesza, powyżej ~5% spowalnia; rumieni skórkę", fmtNum(plan.sugar) + "%",
            plan.sugar.toFloat(), 0f..10f, 0.5f,
        ) { v -> update { it.copy(sugar = v.toDouble()) } }
        GroupDivider()
        SliderRow(
            "Olej", "zmiękcza miąższ, trochę osłabia gluten", fmtNum(plan.oil) + "%",
            plan.oil.toFloat(), 0f..10f, 0.5f,
        ) { v -> update { it.copy(oil = v.toDouble()) } }
        GroupDivider()
        SliderRow(
            "Ocet 5%", "łyżeczki na 500 g mąki; obniża pH: bardziej rozciągliwe, mniej sprężyste", fmtNum(plan.vinegarTsp) + " łyż.",
            plan.vinegarTsp.toFloat(), 0f..4f, 0.5f,
        ) { v -> update { it.copy(vinegarTsp = v.toDouble()) } }
    }
}

private fun flourHint(w: Int): String = when {
    w < 200 -> "słaba: krótkie fermentacje, 2–6 h"
    w < 260 -> "średnia: ok. 6–12 h w temp. pokojowej"
    w < 310 -> "średnio mocna: do ok. 24 h, także w lodówce"
    w < 360 -> "mocna: 48–72 h w lodówce"
    else -> "bardzo mocna: długie, zimne fermentacje 72 h+"
}

// ---------------------------------------------------------------- step 3: schedule

@Composable
private fun ScheduleStep(plan: FermentPlan, r: FermentResult, update: ((FermentPlan) -> FermentPlan) -> Unit) {
    Text("Harmonogram", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Etapy od wyrobienia do pieczenia. Ciasto nie przyjmuje temperatury od razu: w modelu dogania lodówkę czy kuchnię w ok. 2–4 h.",
        color = Pal.muted, fontSize = 13.sp,
    )
    Group {
        CompactStepper("Ciasto po wyrobieniu", "zależy głównie od temperatury wody", "${fmtNum(plan.doughTempC)} °C",
            onMinus = { update { it.copy(doughTempC = it.doughTempC - 1) } },
            onPlus = { update { it.copy(doughTempC = it.doughTempC + 1) } })
    }
    plan.stages.forEachIndexed { i, s ->
        StageCard(i, s, canRemove = plan.stages.size > 1, canGrow = plan.totalHours < FermentViewModel.MAX_HOURS,
            onChange = { ns -> update { p -> p.copy(stages = p.stages.mapIndexed { j, o -> if (j == i) ns else o }) } },
            onRemove = { update { p -> p.copy(stages = p.stages.filterIndexed { j, _ -> j != i }) } })
    }
    if (plan.stages.size < FermentViewModel.MAX_STAGES) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("+ blat 22 °C", onClick = { update { it.copy(stages = it.stages + Stage(2.0, 22.0)) } })
            Pill("+ lodówka 4 °C", onClick = { update { it.copy(stages = it.stages + Stage(24.0, 4.0)) } })
            Pill("+ chłodno 16 °C", onClick = { update { it.copy(stages = it.stages + Stage(6.0, 16.0)) } })
            Pill("+ ciepło 28 °C", onClick = { update { it.copy(stages = it.stages + Stage(1.0, 28.0)) } })
        }
    }
    Text(
        "RAZEM ${fmtHours(plan.totalHours).uppercase()} OD WYROBIENIA",
        color = Pal.muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
    )
    LiveVerdict(r)
}

@Composable
private fun StageCard(i: Int, s: Stage, canRemove: Boolean, canGrow: Boolean, onChange: (Stage) -> Unit, onRemove: () -> Unit) {
    Group {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tempColor(s.tempC)))
            Text("  Etap ${i + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(placeName(s.tempC), color = Pal.muted, fontSize = 12.sp)
            if (canRemove) IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Usuń etap ${i + 1}", tint = Pal.muted) }
            else Spacer(Modifier.width(12.dp).height(48.dp))
        }
        CompactStepper("Czas", null, fmtHours(s.hours),
            onMinus = { onChange(s.copy(hours = s.hours - hourStep(s.hours - 0.01))) },
            onPlus = { if (canGrow) onChange(s.copy(hours = s.hours + hourStep(s.hours))) },
            label = "etap ${i + 1}, czas")
        CompactStepper("Temperatura", null, "${fmtNum(s.tempC)} °C",
            onMinus = { onChange(s.copy(tempC = s.tempC - 1)) },
            onPlus = { onChange(s.copy(tempC = s.tempC + 1)) },
            label = "etap ${i + 1}, temperatura")
        Spacer(Modifier.height(4.dp))
    }
}

private fun hourStep(h: Double) = when {
    h < 3 -> 0.25
    h < 12 -> 0.5
    else -> 1.0
}

fun tempColor(t: Double): Color = when {
    t <= 8 -> Cold
    t <= 18 -> Color(0xFF9FD3C7)
    t <= 26 -> Pal.flame
    else -> Pal.tomato
}

private fun placeName(t: Double) = when {
    t <= 8 -> "lodówka"
    t <= 18 -> "chłodno"
    t <= 26 -> "temp. pokojowa"
    t <= 40 -> "ciepło"
    else -> "gorąco! drożdże giną"
}

// ---------------------------------------------------------------- step 4: yeast

@Composable
private fun YeastStep(plan: FermentPlan, r: FermentResult, update: ((FermentPlan) -> FermentPlan) -> Unit) {
    Text("Drożdże", style = MaterialTheme.typography.headlineSmall)
    SectionLabel("Rodzaj")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        YeastType.entries.forEach { t ->
            // switching the kind keeps the same amount of yeast, recounted
            Pill(t.label, selected = plan.yeastType == t) {
                update { it.copy(yeastType = t, yeastPct = it.instantPct * t.perInstant) }
            }
        }
    }
    Text("Przelicznik: świeże ≈ 3 × instant, suche aktywne ≈ 1,25–1,5 × instant (w modelu 1,33).", color = Pal.muted, fontSize = 12.sp)

    SectionLabel("Ilość")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Znam ilość", selected = !plan.autoYeast) { update { it.copy(autoYeast = false) } }
        Pill("Policz za mnie", selected = plan.autoYeast) {
            update { it.copy(autoYeast = true) }
        }
    }

    val eff = Fermentation.effective(plan)
    if (!plan.autoYeast) {
        Group {
            val min = FermentViewModel.YEAST_MIN * plan.yeastType.perInstant
            val max = FermentViewModel.YEAST_MAX * plan.yeastType.perInstant
            val pos = (ln(plan.yeastPct / min) / ln(max / min)).toFloat().coerceIn(0f, 1f)
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${plan.yeastType.label} w % mąki", fontSize = 14.sp)
                        Text("skala logarytmiczna", color = Pal.muted, fontSize = 12.sp)
                    }
                    OutlinedIconButton(onClick = { update { it.copy(yeastPct = (it.yeastPct - yeastStep(it.yeastPct - 1e-6)).coerceAtLeast(min)) } }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = "Mniej drożdży")
                    }
                    Text(fmtPct(plan.yeastPct), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(80.dp))
                    OutlinedIconButton(onClick = { update { it.copy(yeastPct = it.yeastPct + yeastStep(it.yeastPct)) } }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Więcej drożdży")
                    }
                }
                Slider(
                    value = pos,
                    onValueChange = { t -> update { it.copy(yeastPct = round3(min * (max / min).pow(t.toDouble()))) } },
                    colors = sliderColors(),
                )
            }
        }
    } else {
        Group {
            CompactStepper("Chcę piec za", "zmieniam najdłuższy etap harmonogramu", fmtHours(plan.totalHours),
                onMinus = {
                    update { p -> Fermentation.fitTo(p, p.totalHours - hourStep(p.totalHours - 0.01)) ?: p }
                },
                onPlus = {
                    update { p -> if (p.totalHours >= FermentViewModel.MAX_HOURS) p else Fermentation.fitTo(p, p.totalHours + hourStep(p.totalHours)) ?: p }
                })
            GroupDivider()
            val sol = Fermentation.solveYeast(plan)
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("POTRZEBA", color = Pal.muted, fontSize = 11.sp, letterSpacing = 0.8.sp)
                Text(fmtPct(sol.pct), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
                Text("${plan.yeastType.label.lowercase()} = ${fmtGrams(Fermentation.ingredients(plan).yeast)} g na ${plan.flourG} g mąki", color = Pal.muted, fontSize = 13.sp)
                if (sol.tooLittle) Warn("Nawet ${fmtPct(sol.pct)} nie wystarczy, żeby ciasto zdążyło wyrosnąć: dodaj ciepły etap albo wydłuż czas.")
                if (sol.tooMuch) Warn("Tak długi i ciepły harmonogram wymagałby śladowych ilości drożdży: skróć go albo przenieś część do lodówki.")
            }
        }
    }
    YeastEquivalents(eff)
    LiveVerdict(r)
}

private fun yeastStep(v: Double) = when {
    v < 0.1 -> 0.005
    v < 0.5 -> 0.01
    v < 1.5 -> 0.05
    else -> 0.1
}

private fun round3(v: Double) = Math.round(v * 1000) / 1000.0

@Composable
private fun YeastEquivalents(p: FermentPlan) {
    val flour = p.flourG / 100.0
    val instant = p.instantPct
    Text(
        "To samo w gramach: ${fmtGrams(instant * 3 * flour)} g świeżych · ${fmtGrams(instant * flour)} g instant · " +
            "${fmtGrams(instant * 1.25 * flour)}–${fmtGrams(instant * 1.5 * flour)} g suchych aktywnych",
        color = Pal.muted, fontSize = 12.sp,
    )
}

// ---------------------------------------------------------------- step 5: result

@Composable
private fun ResultStep(plan: FermentPlan, r: FermentResult, update: ((FermentPlan) -> FermentPlan) -> Unit, goTo: (Int) -> Unit) {
    val eff = Fermentation.effective(plan)
    val end = r.end
    var selected by rememberSaveable(plan) { mutableStateOf(plan.totalHours) }
    VerdictCard(plan, r)

    Group {
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)) {
            val at = r.at(selected)
            Text(
                "po ${fmtHours(at.h)}: ciasto ${fmt(at.doughC, 1)} °C · +${Math.round(at.rise)}% · pH ${fmt(at.ph, 2)} · gluten ${Math.round(at.gluten)}",
                fontFamily = Mono, fontSize = 11.sp, color = Pal.fg, modifier = Modifier.padding(start = 8.dp, top = 10.dp),
            )
            Text(stateName(at.state), color = stateColor(at.state), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp))
            FermentChart(r, eff, selected, { selected = it })
            Legend()
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("Wzrost", "+${Math.round(end.rise)}%", Modifier.weight(1f))
        Stat("pH", fmt(end.ph, 2), Modifier.weight(1f))
        Stat("Gluten", "${Math.round(end.gluten)}", Modifier.weight(1f))
        Stat("Skórka", browningName(r.browning), Modifier.weight(1f))
    }

    Advice(plan, r, update, goTo)

    SectionLabel("Składniki")
    val ing = Fermentation.ingredients(eff)
    Group {
        IngRow("Mąka", "W ${eff.w}", ing.flour)
        GroupDivider()
        IngRow("Woda", "${fmtNum(eff.hydration)}%", ing.water)
        GroupDivider()
        IngRow("Sól", fmt(eff.salt, 1) + "%", ing.salt)
        GroupDivider()
        IngRow("Drożdże ${eff.yeastType.label.lowercase()}", fmtPct(eff.yeastPct), ing.yeast)
        if (eff.sugar > 0) { GroupDivider(); IngRow("Cukier", fmtNum(eff.sugar) + "%", ing.sugar) }
        if (eff.oil > 0) { GroupDivider(); IngRow("Olej", fmtNum(eff.oil) + "%", ing.oil) }
        if (eff.vinegarTsp > 0) { GroupDivider(); IngRow("Ocet 5%", "${fmtNum(eff.vinegarTsp)} łyż./500 g", ing.vinegarMl, unit = "ml") }
    }
    Text(
        "${eff.balls} × ${Math.round(ing.total / eff.balls)} G · RAZEM ${Math.round(ing.total)} G",
        color = Pal.muted, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
    )
    YeastEquivalents(eff)

    SectionLabel("Chemia ciasta")
    CHEMISTRY.forEach { (title, body) -> Explainer(title, body) }

    SectionLabel("Założenia modelu")
    Explainer("Na czym to stoi i czego nie umie", ASSUMPTIONS)
    Disclaimer()
}

@Composable
private fun VerdictCard(plan: FermentPlan, r: FermentResult) {
    val end = r.end
    val color = stateColor(end.state)
    val body = when (end.state) {
        DoughState.READY -> "Na końcu harmonogramu ciasto urosło o ${Math.round(end.rise)}%." +
            (r.readyFrom?.let { from -> " Okno pieczenia: od ${fmtHours(from)} do ${fmtHours(r.readyTo ?: plan.totalHours)}." } ?: "")
        DoughState.UNDER -> "Urosło dopiero o ${Math.round(end.rise)}%. Potrzebuje więcej czasu, ciepła albo drożdży."
        DoughState.LATE -> "Najlepszy moment" + (r.readyFrom?.let { " był między ${fmtHours(it)} a ${fmtHours(r.readyTo ?: it)}" } ?: " minął") +
            ". Upieczesz, ale będzie lepkie i trudniejsze do rozciągnięcia."
        DoughState.OVER -> "Od ok. ${fmtHours(r.overFrom ?: plan.totalHours)} ciasto jest przefermentowane: " +
            (if (end.gluten < Fermentation.COLLAPSE_GLUTEN) "gluten nie trzyma gazu, ciasto opada i się rwie." else "zjadło cukry, mocno kwaśnieje i opada.")
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(stateName(end.state), color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 14.sp)
            if (r.yeastAlive < 0.5) Text("Uwaga: temperatura ciasta przekroczyła ~50 °C, większość drożdży zginęła.", color = Pal.tomato, fontSize = 13.sp)
        }
    }
}

@Composable
private fun Advice(plan: FermentPlan, r: FermentResult, update: ((FermentPlan) -> FermentPlan) -> Unit, goTo: (Int) -> Unit) {
    val end = r.end
    val solved = remember(plan) { Fermentation.solveYeast(plan.copy(autoYeast = true)) }
    val minW = remember(plan) { Fermentation.minFlourW(plan) }
    val eff = Fermentation.effective(plan)
    val tips = ArrayList<Pair<String, (() -> Unit)?>>()
    if (!plan.autoYeast && end.state != DoughState.READY && !solved.tooLittle && !solved.tooMuch) {
        tips += "Żeby było gotowe na koniec: ${fmtPct(solved.pct)} ${plan.yeastType.label.lowercase()} zamiast ${fmtPct(plan.yeastPct)}. Ustaw ›" to {
            update { it.copy(yeastPct = round3(solved.pct)) }
        }
    }
    if (end.gluten < Fermentation.READY_GLUTEN + 5) {
        tips += if (minW != null && minW > eff.w) {
            "Mąka W ${eff.w} jest za słaba na ten harmonogram: weź W ≥ $minW albo skróć ciepłe etapy. Ustaw W $minW ›" to {
                update { it.copy(w = minW.coerceAtMost(380)) }
            }
        } else {
            "Ten harmonogram jest za długi lub za ciepły nawet dla najmocniejszej mąki: przenieś część do lodówki." to { goTo(2) }
        }
    }
    if (end.ph < 5.3) tips += "pH ${fmt(end.ph, 2)}: skórka będzie bledsza (Maillard słabnie w kwaśnym), smak wyraźnie kwaśny." to null
    if (eff.vinegarTsp > 0 && r.browning < 0.5) tips += "Ocet obniża pH i rumienienie; łyżeczka cukru albo krótszy ocet to rekompensuje." to null
    if (tips.isEmpty()) return
    Group {
        tips.forEachIndexed { i, (text, action) ->
            if (i > 0) GroupDivider()
            Text(
                text, fontSize = 13.sp, color = if (action != null) Pal.flame else Pal.fg,
                modifier = Modifier.fillMaxWidth().then(if (action != null) Modifier.clickable(onClick = action) else Modifier).padding(14.dp),
            )
        }
    }
}

@Composable
private fun Legend() {
    FlowRow(Modifier.padding(start = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem(Pal.basil.copy(alpha = 0.35f), "gotowe do pieczenia")
        LegendItem(Pal.tomato.copy(alpha = 0.35f), "przefermentowane")
        LegendItem(Pal.muted, "otoczenie")
        LegendItem(Pal.ember, "ciasto")
    }
    Text("Dotknij lub przeciągnij po wykresie, żeby odczytać wartości.", color = Pal.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(" $text", color = Pal.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Surface(color = Pal.card, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), color = Pal.muted, fontSize = 10.sp, letterSpacing = 0.6.sp)
            Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
        }
    }
}

@Composable
private fun IngRow(name: String, pct: String, grams: Double, unit: String = "g") {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.Bottom) {
        Text(name, fontSize = 15.sp)
        Text("  $pct", color = Pal.muted, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(fmtGrams(grams), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(" $unit", color = Pal.muted, fontFamily = Mono, fontSize = 13.sp)
    }
}

@Composable
private fun Explainer(title: String, body: String) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Surface(onClick = { open = !open }, color = Pal.card, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (open) "−" else "+", color = Pal.flame, fontSize = 18.sp)
            }
            if (open) Text(body, color = Pal.fg.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun LiveVerdict(r: FermentResult) {
    val end = r.end
    val text = when (end.state) {
        DoughState.READY -> "gotowe na koniec" + (r.readyFrom?.let { " (okno od ${fmtHours(it)})" } ?: "")
        DoughState.UNDER -> "na koniec za mało wyrośnięte (+${Math.round(end.rise)}%)"
        DoughState.LATE -> "na koniec już po szczycie"
        DoughState.OVER -> "na koniec przefermentowane"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(stateColor(end.state).copy(alpha = 0.12f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(stateColor(end.state)))
        Text("  Przy tych ustawieniach: $text", fontSize = 13.sp, color = stateColor(end.state))
    }
}

@Composable
private fun Warn(text: String) {
    Text(text, color = Pal.tomato, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
}

fun stateName(s: DoughState) = when (s) {
    DoughState.UNDER -> "Jeszcze nie gotowe"
    DoughState.READY -> "Gotowe do pieczenia"
    DoughState.LATE -> "Po szczycie"
    DoughState.OVER -> "Przefermentowane"
}

fun stateColor(s: DoughState) = when (s) {
    DoughState.UNDER -> Cold
    DoughState.READY -> Pal.basil
    DoughState.LATE -> Pal.flame
    DoughState.OVER -> Pal.tomato
}

private fun browningName(b: Double) = when {
    b < 0.45 -> "blada"
    b < 0.7 -> "dobra"
    else -> "rumiana"
}

// ---------------------------------------------------------------- shared controls

@Composable
private fun CompactStepper(title: String, hint: String?, value: String, onMinus: () -> Unit, onPlus: () -> Unit, label: String = title) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            if (hint != null) Text(hint, color = Pal.muted, fontSize = 12.sp)
        }
        OutlinedIconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, contentDescription = "Mniej: $label") }
        Text(value, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(84.dp))
        OutlinedIconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, contentDescription = "Więcej: $label") }
    }
}

@Composable
private fun SliderRow(
    title: String, hint: String?, display: String,
    value: Float, range: ClosedFloatingPointRange<Float>, step: Float, onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(display, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        if (hint != null) Text(hint, color = Pal.muted, fontSize = 12.sp)
        val steps = ((range.endInclusive - range.start) / step).roundToInt() - 1
        Slider(
            value = value.coerceIn(range), valueRange = range, steps = steps.coerceAtLeast(0),
            onValueChange = { v -> onChange((Math.round((v - range.start) / step) * step + range.start).let { Math.round(it * 100) / 100f }) },
            colors = sliderColors(),
        )
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Pal.flame, activeTrackColor = Pal.flame, inactiveTrackColor = Pal.raised,
    activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
)

// ---------------------------------------------------------------- texts

private val CHEMISTRY = listOf(
    "Enzymy: skrobia → cukry" to
        "Mąka przynosi własne amylazy. α- i β-amylaza tną uszkodzone przy mieleniu ziarna skrobi na maltozę i glukozę, a drożdże " +
        "żywią się właśnie nimi. Enzymy w zimnie zwalniają mniej niż drożdże, więc po długiej fermentacji w lodówce w cieście " +
        "zostaje więcej wolnych cukrów i skórka lepiej się rumieni.",
    "Fermentacja: CO₂ i etanol" to
        "Drożdże (Saccharomyces cerevisiae) bez dostępu tlenu przerabiają cukry na dwutlenek węgla i etanol: " +
        "C₆H₁₂O₆ → 2 C₂H₅OH + 2 CO₂. Gaz trafia do pęcherzyków zamkniętych w sieci glutenu i ciasto rośnie. " +
        "Etanol, estry i aldehydy to duża część aromatu. Tempo zależy głównie od temperatury: prawie stoi przy 2–4 °C, " +
        "najszybciej idzie przy 30–35 °C, powyżej 40 °C gwałtownie spada, a przy 55–60 °C drożdże giną.",
    "Kwasy organiczne i pH" to
        "Część CO₂ rozpuszcza się w wodzie ciasta jako kwas węglowy, drożdże wydzielają m.in. kwas bursztynowy, " +
        "a bakterie obecne w mące, przy długiej fermentacji, kwas mlekowy i octowy. pH spada z ok. 5,9–6,0 do ok. 5,4–5,6 " +
        "(zakwas schodzi do 3,8–4,3). Ocet od razu obniża pH, zanim fermentacja w ogóle ruszy.",
    "Sieć glutenu: gliadyna i glutenina" to
        "Po zmieszaniu z wodą dwa rodzaje białek łączą się w sieć. Gluteniny tworzą długie łańcuchy spięte mostkami " +
        "disiarczkowymi: dają sprężystość, ciasto „oddaje”. Gliadyny działają jak smar między nimi: dają rozciągliwość i lepkość. " +
        "Wyrabianie i odpoczynek porządkują sieć; potem proteazy z mąki, glutation z obumierających drożdży i kwasy powoli ją " +
        "rozluźniają. Mąka o wyższym W ma więcej mocnych glutenin i wytrzymuje dłużej.",
    "pH a rozciągliwość" to
        "W niższym pH białka glutenu przyjmują protony i zyskują ładunek dodatni. Jednoimienne ładunki się odpychają, łańcuchy " +
        "się rozsuwają i słabiej sklejają: ciasto jest bardziej rozciągliwe i mniej sprężyste. Dlatego ocet pomaga przy strudlu. " +
        "Przy zbyt niskim pH proteazy pracują szybciej, a sieć słabnie i zaczyna się rwać.",
    "pH a rumienienie (Maillard)" to
        "Reakcja Maillarda, czyli cukry redukujące + aminokwasy w wysokiej temperaturze, przyspiesza w wyższym pH. " +
        "Kwaśniejsze ciasto rumieni się wolniej i jaśniej. Pomagają resztkowe cukry (dodany cukier, długa zimna fermentacja); " +
        "ocet i przefermentowanie, które obniżają pH i zjadają cukry, przeszkadzają.",
    "Sól, cukier, woda" to
        "Sól ekranuje ładunki białek i zacieśnia gluten, hamuje proteazy i spowalnia drożdże przez ciśnienie osmotyczne. " +
        "Cukier w małej ilości to szybka pożywka; od ok. 5% wyciąga wodę z komórek drożdży i je spowalnia. " +
        "Więcej wody to szybsza fermentacja i szybsza praca enzymów, ale słabsza, bardziej lejąca się struktura.",
)

private val ASSUMPTIONS =
    "• Aktywność drożdży od temperatury: model temperatur kardynalnych CTMI (Rosso i in., 1993) z Tmin −2 °C, Topt 33 °C, " +
        "Tmax 45 °C; powyżej 45 °C dodatkowo obumieranie (minuty przy 55–60 °C). Parametry dobrane ręcznie w zakresie " +
        "podawanym dla S. cerevisiae.\n" +
        "• Temperatura ciasta goni temperaturę otoczenia ze stałą czasową 1,5 h.\n" +
        "• Postęp fermentacji = suma „godzin w 22 °C”. Ciasto podwaja się, gdy suma osiąga 2,8 h × (drożdże instant w %)^−0,6. " +
        "Wykładnik < 1 oznacza, że drożdże się rozmnażają: połowa drożdży nie oznacza dwa razy dłużej. " +
        "Skalibrowane do reguł kciuka (ok. 0,2% instant: 8 h w 22 °C; ok. 0,25%: 48 h w lodówce; ok. 1,2%: 2 h w cieple). " +
        "Nie korzysta z tabeli TXCraig1 / PizzaBlab, choć daje wyniki w podobnym rzędzie wielkości.\n" +
        "• Sól, cukier, hydratacja, olej i ocet mnożą tempo przez proste współczynniki.\n" +
        "• Objętość: krzywa sigmoidalna postępu, sufit zależny od W i hydratacji; gdy gluten słabnie, ciasto opada.\n" +
        "• pH: start ok. 5,95, spadek z postępem fermentacji i z czasem/temperaturą (bakterie, enzymy), ocet od razu.\n" +
        "• Gluten (umowne 0–100): rośnie przez pierwsze godziny, potem zużywa się szybciej w cieple (Q10 = 2), w kwaśnym, " +
        "przy dużej hydratacji i bez soli; wytrzymałość rośnie wykładniczo z W.\n\n" +
        "Czego model nie uwzględnia: różnic między partiami drożdży i mąki, zakwasu, wielkości i kształtu kulek, " +
        "intensywności wyrabiania, składanie ciasta, zawartości popiołu i uszkodzonej skrobi, a także tego, że " +
        "lodówka rzadko trzyma równe 4 °C. Liczby z wykresu to orientacja, nie przepis laboratoryjny."
