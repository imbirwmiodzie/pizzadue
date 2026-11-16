package com.vibepizzaiolo.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.Filter
import com.vibepizzaiolo.community.Pizza
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.Sort
import com.vibepizzaiolo.community.StyleCard
import com.vibepizzaiolo.ui.Mono
import com.vibepizzaiolo.ui.Pal
import kotlinx.coroutines.delay

/** Every pizza, searchable by type and by style. Tiles pop in one after another. */
@Composable
fun GalleryScreen(filter: Filter, results: List<Pizza>, searching: Boolean, shared: Shared?, onFilter: (Filter) -> Unit, onOpen: (Pizza) -> Unit) {
    var more by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(filter.query, { onFilter(filter.copy(query = it)) }, "Search: margherita, napoletana, a name…")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (more) "Fewer filters" else "Style, sauce, heat…", selected = more || filter.copy(type = null, query = "").let { !it.isEmpty }, color = Pal.basil) { more = !more }
                Pill("All types", selected = filter.type == null) { onFilter(filter.copy(type = null)) }
                for (t in PizzaType.entries) Pill(t.label, selected = filter.type == t) { onFilter(filter.copy(type = if (filter.type == t) null else t)) }
            }
            AnimatedVisibility(more) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (s in PizzaStyle.entries) Pill(s.label, selected = filter.style == s) { onFilter(filter.copy(style = if (filter.style == s) null else s)) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (b in listOf(Base.ROSSA, Base.BIANCA)) Pill(b.label, selected = filter.base == b, color = Pal.tomato) { onFilter(filter.copy(base = if (filter.base == b) null else b)) }
                        for (n in 1..StyleCard.MAX_SPICE) Pill("🌶".repeat(n) + "+", selected = filter.minSpice == n, color = Pal.ember) { onFilter(filter.copy(minSpice = if (filter.minSpice == n) 0 else n)) }
                        Pill(if (filter.sort == Sort.BEST) "Best first" else "Newest first", selected = filter.sort == Sort.BEST) {
                            onFilter(filter.copy(sort = if (filter.sort == Sort.BEST) Sort.NEWEST else Sort.BEST))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${filter.minTemp}–${filter.maxTemp} °C", color = Pal.muted, fontFamily = Mono, fontSize = 12.sp)
                        RangeSlider(
                            value = filter.minTemp.toFloat()..filter.maxTemp.toFloat(),
                            onValueChange = { r -> onFilter(filter.copy(minTemp = (r.start / 10).toInt() * 10, maxTemp = (r.endInclusive / 10).toInt() * 10)) },
                            valueRange = StyleCard.TEMP_MIN.toFloat()..StyleCard.TEMP_MAX.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = Pal.ember, activeTrackColor = Pal.ember, inactiveTrackColor = Pal.raised),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Pal.flame, trackColor = Color.Transparent)
        }
        if (results.isEmpty() && !searching) {
            Text(
                if (filter.isEmpty) "No pizzas yet. Post the first one." else "No pizza matches. Loosen the filters.",
                color = Pal.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(40.dp),
            )
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalItemSpacing = 10.dp, modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(results, key = { _, p -> p.id }) { index, pizza ->
                Tile(pizza, index, shared, Modifier.animateItem()) { onOpen(pizza) }
            }
        }
    }
}

// tiles that already popped in stay put when scrolled back to
private val shown = HashSet<String>()

@Composable
private fun Tile(pizza: Pizza, index: Int, shared: Shared?, modifier: Modifier, onClick: () -> Unit) {
    val pop = remember(pizza.id) { Animatable(if (pizza.id in shown) 1f else 0f) }
    LaunchedEffect(pizza.id) {
        if (pop.value < 1f) {
            delay((index % 8) * 55L)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
            shown += pizza.id
        }
    }
    // three heights in turn, so the columns never line up
    val ratio = when (pizza.id.hashCode().mod(3)) { 0 -> 0.8f; 1 -> 1f; else -> 0.68f }
    Box(
        modifier.fillMaxWidth().aspectRatio(ratio)
            .graphicsLayer { val s = 0.7f + 0.3f * pop.value; scaleX = s; scaleY = s; alpha = pop.value.coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(16.dp)).background(Pal.card).clickable(onClick = onClick),
    ) {
        PizzaPhoto(pizza.photo, Modifier.fillMaxSize().sharedPhoto(shared, pizza.id), small = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xD9000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(pizza.type.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            Text(
                listOfNotNull(pizza.style.label, pizza.likePercent?.let { "♥ $it%" }, "🌶".repeat(pizza.spice).ifEmpty { null }).joinToString(" · "),
                color = Color(0xCCFFFFFF), fontSize = 11.sp, maxLines = 1,
            )
        }
    }
}
