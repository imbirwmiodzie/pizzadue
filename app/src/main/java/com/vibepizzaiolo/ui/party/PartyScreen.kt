@file:OptIn(ExperimentalLayoutApi::class)

package com.vibepizzaiolo.ui.party

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vibepizzaiolo.video.NightInfo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vibepizzaiolo.dough.DoughCalc
import com.vibepizzaiolo.party.BringItem
import com.vibepizzaiolo.party.MenuPizza
import com.vibepizzaiolo.party.Party
import com.vibepizzaiolo.party.PartyDraft
import com.vibepizzaiolo.party.PartyGuest
import com.vibepizzaiolo.party.PartyRules
import com.vibepizzaiolo.party.PartyStage
import com.vibepizzaiolo.party.PartyUi
import com.vibepizzaiolo.party.PartyViewModel
import com.vibepizzaiolo.party.QueueItem
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.ui.Group
import com.vibepizzaiolo.ui.GroupDivider
import com.vibepizzaiolo.ui.Mono
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.SectionLabel
import com.vibepizzaiolo.ui.StepperRow
import com.vibepizzaiolo.ui.community.BigButton
import com.vibepizzaiolo.ui.community.Field
import com.vibepizzaiolo.ui.community.Pill
import java.util.Locale
import kotlinx.coroutines.delay

/** How often the pizzaiolo's phone asks the server what friends ordered. */
private const val REFRESH_MS = 5000L

/** The Serata tab: a pizza night with friends, from the invitation to the last pizza. */
@Composable
fun PartyScreen(vm: PartyViewModel, settings: Settings, onReel: (NightInfo) -> Unit = {}, onTimer: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val nightOpen = ui.party != null

    LaunchedEffect(Unit) { vm.start() }
    // friends order from their browsers: keep the board fresh while it is on screen
    LaunchedEffect(nightOpen, owner) {
        if (nightOpen) owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(REFRESH_MS); vm.refresh() }
        }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.messageShown()
        }
    }
    BackHandler(enabled = ui.form != null) { vm.closeForm() }

    val form = ui.form
    val party = ui.party
    when {
        ui.serverUrl.isEmpty() -> NoServer(vm::setServerUrl)
        form != null -> NightForm(form, ui.editing, ui.busy, vm::editForm, vm::submitForm, vm::closeForm)
        ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.flame) }
        party != null -> Board(ui, party, settings, vm, onReel, onTimer)
        else -> Welcome(vm::newNight)
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun Title(text: String, sub: String? = null) {
    Column {
        Text(text, style = MaterialTheme.typography.headlineSmall)
        if (!sub.isNullOrEmpty()) Text(sub, color = Pal.muted, fontSize = 13.sp)
    }
}

@Composable
private fun Welcome(onNew: () -> Unit) {
    Page {
        Title("Serata", "A pizza night with friends")
        Spacer(Modifier.height(8.dp))
        Group {
            Step("1", "You set the menu and share one link", "WhatsApp, or wherever your friends are.")
            GroupDivider()
            Step("2", "Friends pick their pizzas in the browser", "No app, no account: a name is enough. They also see who brings what.")
            GroupDivider()
            Step("3", "The app counts the dough and keeps the queue", "When you start baking, their phones say: put me away.")
        }
        Spacer(Modifier.height(8.dp))
        BigButton("New pizza night", onClick = onNew)
    }
}

@Composable
private fun Step(n: String, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            n, color = Pal.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.size(26.dp).clip(CircleShape).background(Pal.flame).padding(top = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(text, color = Pal.muted, fontSize = 13.sp)
        }
    }
}

/** A night lives on the server (friends open it in a browser), so there has to be one. */
@Composable
private fun NoServer(onSet: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    Page {
        Title("Serata", "A pizza night with friends")
        Text(
            "Friends order through a link, so a pizza night needs your server. Set it up once (see server/README.md), then enter its address. The Pizzaioli tab uses the same one.",
            color = Pal.muted, fontSize = 14.sp,
        )
        Field(url, { url = it }, "Server address (https://…)")
        BigButton("Connect", enabled = url.isNotBlank()) { onSet(url) }
    }
}

// ---- the form ----

@Composable
private fun NightForm(d: PartyDraft, editing: Boolean, busy: Boolean, edit: ((PartyDraft) -> PartyDraft) -> Unit, submit: () -> Unit, close: () -> Unit) {
    var pizza by rememberSaveable { mutableStateOf("") }
    var pizzaNote by rememberSaveable { mutableStateOf("") }
    var thing by rememberSaveable { mutableStateOf("") }
    // a tap on an idea or a button puts the keyboard away, so the list below stays where it is
    val focus = LocalFocusManager.current
    fun addPizza(m: MenuPizza) = edit { f ->
        if (m.name.isBlank() || f.menu.any { it.name.equals(m.name.trim(), true) } || f.menu.size >= PartyRules.MAX_MENU) f
        else f.copy(menu = f.menu + m.copy(name = m.name.trim(), note = m.note.trim()))
    }
    fun addThing(name: String) = edit { f ->
        if (name.isBlank() || f.bring.any { it.name.equals(name.trim(), true) } || f.bring.size >= PartyRules.MAX_BRING) f
        else f.copy(bring = f.bring + BringItem(name = name.trim()))
    }
    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title(if (editing) "Edit the night" else "New pizza night") }
            IconButton(onClick = close) { Icon(Icons.Default.Close, "Close", tint = Pal.muted) }
        }
        Field(d.title, { v -> edit { it.copy(title = v.take(PartyRules.TITLE)) } }, "Name of the night")
        if (!editing) Field(d.hostName, { v -> edit { it.copy(hostName = v.take(PartyRules.NAME)) } }, "Your name")
        Field(d.whenText, { v -> edit { it.copy(whenText = v.take(PartyRules.WHEN)) } }, "When (e.g. Saturday 19:00)")
        Field(d.note, { v -> edit { it.copy(note = v.take(PartyRules.NOTE)) } }, "A word for your friends (optional)", singleLine = false)

        SectionLabel("Menu · ${d.menu.size}")
        if (d.menu.isNotEmpty()) {
            Group {
                d.menu.forEachIndexed { i, m ->
                    if (i > 0) GroupDivider()
                    Removable(m.name, m.note, "Remove ${m.name}") { edit { f -> f.copy(menu = f.menu - m) } }
                }
            }
        }
        val pizzaIdeas = PartyRules.MENU_IDEAS.filter { idea -> d.menu.none { it.name.equals(idea.name, true) } }
        if (pizzaIdeas.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pizzaIdeas.forEach { idea -> Pill("+ ${idea.name}") { focus.clearFocus(); addPizza(idea) } }
            }
        }
        Field(pizza, { pizza = it.take(PartyRules.ITEM) }, "Another pizza")
        if (pizza.isNotBlank()) {
            Field(pizzaNote, { pizzaNote = it.take(PartyRules.PIZZA_NOTE) }, "What is on it (optional)")
            BigButton("Add to the menu", color = Pal.raised) { addPizza(MenuPizza(name = pizza, note = pizzaNote)); pizza = ""; pizzaNote = "" }
        }

        SectionLabel("Who brings what · ${d.bring.size}")
        if (d.bring.isNotEmpty()) {
            Group {
                d.bring.forEachIndexed { i, b ->
                    if (i > 0) GroupDivider()
                    Removable(b.name, null, "Remove ${b.name}") { edit { f -> f.copy(bring = f.bring - b) } }
                }
            }
        }
        val thingIdeas = PartyRules.BRING_IDEAS.filter { idea -> d.bring.none { it.name.equals(idea, true) } }
        if (thingIdeas.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                thingIdeas.forEach { idea -> Pill("+ $idea") { focus.clearFocus(); addThing(idea) } }
            }
        }
        Field(thing, { thing = it.take(PartyRules.ITEM) }, "Something else to bring")
        if (thing.isNotBlank()) BigButton("Add to the list", color = Pal.raised) { addThing(thing); thing = "" }

        SectionLabel("Dough")
        Group {
            StepperRow(
                "Spare balls", "The first is baked as a test pizza for everyone", d.spare.toString(),
                onMinus = { edit { it.copy(spare = (it.spare - 1).coerceAtLeast(0)) } },
                onPlus = { edit { it.copy(spare = (it.spare + 1).coerceAtMost(PartyRules.MAX_SPARE)) } },
            )
        }
        Spacer(Modifier.height(4.dp))
        BigButton(
            if (editing) "Save" else "Create and get the link", busy = busy,
            enabled = d.title.isNotBlank() && d.menu.isNotEmpty() && (editing || d.hostName.isNotBlank()),
        ) { focus.clearFocus(); submit() }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Removable(title: String, sub: String?, removeLabel: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!sub.isNullOrEmpty()) Text(sub, color = Pal.muted, fontSize = 12.sp)
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, removeLabel, tint = Pal.muted) }
    }
}

// ---- the night ----

/** The night's numbers for its reel: pizzas out of the oven, the guests, the favourite pizza. */
internal fun nightInfo(party: Party): NightInfo {
    val fav = party.menu.maxByOrNull { party.count(it.id) }?.takeIf { party.count(it.id) > 0 }
    return NightInfo(
        title = party.title,
        pizzas = party.queue().count { it.done }.takeIf { it > 0 } ?: party.ordered,
        guests = party.guests.filter { !it.host }.map { it.name },
        favourite = fav?.name,
        favouriteCount = fav?.let { party.count(it.id) } ?: 0,
    )
}

private fun ordersText(party: Party, g: PartyGuest): String {
    val names = party.menu.associate { it.id to it.name }
    return g.orders.filter { it.count > 0 }.joinToString { o -> names[o.pizzaId].orEmpty() + if (o.count > 1) " ×${o.count}" else "" }
}

/** The message that goes out with the link, in the phone's language. */
private fun invitation(party: Party, link: String): String {
    val polish = Locale.getDefault().language == "pl"
    val whenLine = if (party.whenText.isNotEmpty()) " · ${party.whenText}" else ""
    return if (polish) "🍕 ${party.title}$whenLine\nWybierz swoją pizzę i zobacz, co przynieść:\n$link"
    else "🍕 ${party.title}$whenLine\nPick your pizza and see what to bring:\n$link"
}

@Composable
private fun Board(ui: PartyUi, party: Party, settings: Settings, vm: PartyViewModel, onReel: (NightInfo) -> Unit, onTimer: () -> Unit) {
    var addingGuest by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ui.orderFor?.let { id -> party.guests.firstOrNull { it.id == id } }?.let { guest ->
        OrderDialog(party, guest, { pizzaId, by -> vm.changeOrder(guest.id, pizzaId, by) }, { vm.removeGuest(guest.id) }) { vm.openOrder(null) }
    }
    if (addingGuest) NameDialog({ addingGuest = false }) { name -> addingGuest = false; vm.addGuest(name) }
    assigning?.let { id -> party.bring.firstOrNull { it.id == id } }?.let { item ->
        AssignDialog(party, item, { guestId -> assigning = null; vm.assign(item.id, guestId) }) { assigning = null }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = Pal.card,
            title = { Text("Delete this night?") },
            text = { Text("The link stops working and the orders are gone.", color = Pal.muted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteNight() }) { Text("Delete", color = Pal.tomato) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }

    Page {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title(party.title, party.whenText) }
            Pill(
                when (party.stage) { PartyStage.ORDERS -> "orders open"; PartyStage.BAKING -> "baking"; PartyStage.DONE -> "done" },
                selected = true, color = if (party.stage == PartyStage.ORDERS) Pal.basil else Pal.flame,
            )
        }
        when (party.stage) {
            PartyStage.ORDERS -> {
                Invite(party, ui.link)
                OrdersSummary(party)
                Dough(party, settings, vm::useBalls)
                Guests(party, vm::openOrder) { addingGuest = true }
                BringList(party) { assigning = it }
                Spacer(Modifier.height(4.dp))
                BigButton("Close orders · start the bake", busy = ui.busy) { vm.setStage(PartyStage.BAKING) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = vm::editNight) { Text("Edit menu and details", color = Pal.fg) }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = Pal.tomato) }
                }
            }
            PartyStage.BAKING -> {
                Queue(party, vm::toggle, vm::bakedNext, onTimer)
                Spacer(Modifier.height(4.dp))
                BigButton("Finish the night", color = Pal.raised, busy = ui.busy) { vm.setStage(PartyStage.DONE) }
                TextButton(onClick = { vm.setStage(PartyStage.ORDERS) }) { Text("Open orders again", color = Pal.muted) }
            }
            PartyStage.DONE -> {
                val baked = party.queue().count { it.done }
                Group {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🍕", fontSize = 44.sp)
                        Text("Grazie!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("$baked ${if (baked == 1) "pizza" else "pizzas"} for ${party.guests.size} at the table", color = Pal.muted, fontSize = 14.sp)
                    }
                }
                OrdersSummary(party)
                Spacer(Modifier.height(4.dp))
                BigButton("🎬  Reel of the night") { onReel(nightInfo(party)) }
                Text("Film one pizza of the night; the reel adds the numbers and thanks everyone by name.", color = Pal.muted, fontSize = 12.sp)
                BigButton("Start a new night", color = Pal.raised, busy = ui.busy) { confirmDelete = true }
                TextButton(onClick = { vm.setStage(PartyStage.BAKING) }) { Text("Back to the oven", color = Pal.muted) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Invite(party: Party, link: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    SectionLabel("Invite")
    Group {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(link, fontFamily = Mono, fontSize = 12.sp, color = Pal.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BigButton("Share the link") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, invitation(party, link))
                context.startActivity(Intent.createChooser(send, "Invite friends"))
            }
            Text(
                "Copy the link", color = Pal.fg, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(8.dp)).clickable {
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun OrdersSummary(party: Party) {
    SectionLabel("Orders · ${party.ordered} ${if (party.ordered == 1) "pizza" else "pizzas"}")
    Group {
        party.menu.forEachIndexed { i, m ->
            if (i > 0) GroupDivider()
            val n = party.count(m.id)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, fontSize = 15.sp, modifier = Modifier.weight(1f), color = if (n > 0) Pal.fg else Pal.muted)
                Text(n.toString(), fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = if (n > 0) Pal.flame else Pal.muted)
            }
        }
    }
}

@Composable
private fun Dough(party: Party, settings: Settings, onUse: () -> Unit) {
    val dough = DoughCalc.calc(party.balls, settings.ballWeight.toDouble(), settings.hydration.toDouble(), settings.salt.toDouble(), settings.yeast.toDouble())
    SectionLabel("Dough")
    Group {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${party.balls} balls of ${settings.ballWeight} g", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${party.ordered} ordered + ${party.spare} spare", color = Pal.muted, fontSize = 13.sp,
            )
            Text(
                "flour ${DoughCalc.formatGrams(dough.flour)} g · water ${DoughCalc.formatGrams(dough.water)} g · salt ${DoughCalc.formatGrams(dough.salt)} g · yeast ${DoughCalc.formatGrams(dough.yeast)} g",
                fontFamily = Mono, fontSize = 12.sp, color = Pal.fg,
            )
            if (settings.balls != party.balls) {
                Text(
                    "Set the Dough tab to ${party.balls} balls", color = Pal.flame, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUse).padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Guests(party: Party, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    SectionLabel("At the table · ${party.guests.size}")
    Group {
        party.guests.forEach { g ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(g.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(g.name + if (g.host) " · you" else "", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(ordersText(party, g).ifEmpty { if (g.host) "tap to pick your pizza" else "still choosing" }, color = Pal.muted, fontSize = 13.sp)
                }
                if (g.pizzas > 0) Text(g.pizzas.toString(), fontFamily = Mono, color = Pal.flame, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            GroupDivider()
        }
        Text(
            "+ Add a guest without a phone", color = Pal.flame, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(14.dp),
        )
    }
}

@Composable
private fun BringList(party: Party, onAssign: (String) -> Unit) {
    if (party.bring.isEmpty()) return
    SectionLabel("Who brings what")
    Group {
        party.bring.forEachIndexed { i, b ->
            if (i > 0) GroupDivider()
            val who = party.guestName(b.guestId)
            Row(Modifier.fillMaxWidth().clickable { onAssign(b.id) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(who ?: "nobody yet", color = if (who != null) Pal.basil else Pal.muted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Queue(party: Party, onToggle: (QueueItem) -> Unit, onNext: () -> Unit, onTimer: () -> Unit) {
    val queue = party.queue()
    val next = queue.firstOrNull { !it.done }
    val done = queue.count { it.done }
    Group {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (next != null) {
                Text("NEXT INTO THE OVEN", color = Pal.muted, style = MaterialTheme.typography.labelSmall)
                Text(next.pizzaName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pal.flame)
                Text("for ${next.guestName}", fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                BigButton("Out of the oven ✓", onClick = onNext)
                Text(
                    "Ticks by itself when the timer's bake ends. Friends' phones now say: put me away.",
                    color = Pal.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(onClick = onTimer, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Go to the timer", color = Pal.fg) }
            } else {
                Text("Everything is baked", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pal.basil)
                Text("Sit down and eat.", color = Pal.muted, fontSize = 14.sp)
            }
        }
    }
    SectionLabel("Queue · $done of ${queue.size}")
    Group {
        queue.forEachIndexed { i, q ->
            if (i > 0) GroupDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(q) }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (q.done) Pal.basil else Pal.raised),
                    contentAlignment = Alignment.Center,
                ) { if (q.done) Icon(Icons.Default.Check, "Baked", tint = Pal.ink, modifier = Modifier.size(16.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(q.pizzaName, fontSize = 15.sp, fontWeight = if (q === next) FontWeight.Bold else FontWeight.Normal, color = if (q.done) Pal.muted else Pal.fg)
                    Text(q.guestName, color = Pal.muted, fontSize = 13.sp)
                }
                if (q === next) Text("next", color = Pal.flame, fontSize = 12.sp, fontFamily = Mono)
            }
        }
    }
}

/** On the Timer tab while a night is baking: whose pizza is next. Tapping opens the queue. */
@Composable
fun NextUpBanner(party: Party, onOpen: () -> Unit) {
    val queue = party.queue()
    val next = queue.firstOrNull { !it.done }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(Pal.card).clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (next != null) "NEXT" else party.title.uppercase(), color = Pal.muted, style = MaterialTheme.typography.labelSmall)
            Text(
                if (next != null) "${next.pizzaName} · ${next.guestName}" else "Everything is baked",
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (next != null) Pal.flame else Pal.basil,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("${queue.count { it.done }}/${queue.size}", fontFamily = Mono, color = Pal.muted, fontSize = 14.sp)
    }
}

// ---- dialogs ----

@Composable
private fun OrderDialog(party: Party, guest: PartyGuest, onChange: (String, Int) -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose, containerColor = Pal.card,
        title = { Text(if (guest.host) "Your pizza" else "${guest.name}'s pizza") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                party.menu.forEach { m ->
                    val n = guest.orders.firstOrNull { it.pizzaId == m.id }?.count ?: 0
                    StepperRow(m.name, null, n.toString(), onMinus = { onChange(m.id, -1) }, onPlus = { onChange(m.id, 1) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = Pal.flame) } },
        dismissButton = { if (!guest.host) TextButton(onClick = onRemove) { Text("Take off the list", color = Pal.tomato) } },
    )
}

@Composable
private fun NameDialog(onClose: () -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose, containerColor = Pal.card,
        title = { Text("A guest without a phone") },
        text = { Field(name, { name = it.take(PartyRules.NAME) }, "Their name") },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name) }) { Text("Add", color = Pal.flame) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun AssignDialog(party: Party, item: BringItem, onPick: (String?) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose, containerColor = Pal.card,
        title = { Text("Who brings ${item.name}?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                party.guests.forEach { g ->
                    TextButton(onClick = { onPick(g.id) }) { Text(g.name, color = if (g.id == item.guestId) Pal.basil else Pal.fg) }
                }
                TextButton(onClick = { onPick(null) }) { Text("Nobody yet", color = Pal.muted) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
