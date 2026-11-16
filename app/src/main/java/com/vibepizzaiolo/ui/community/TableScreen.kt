package com.vibepizzaiolo.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.ChatUi
import com.vibepizzaiolo.community.Invite
import com.vibepizzaiolo.community.InviteStatus
import com.vibepizzaiolo.community.Rules
import com.vibepizzaiolo.ui.Pal

/** La tavola: everyone you asked to eat together, and everyone who asked you. */
@Composable
fun TableScreen(invites: List<Invite>, onOpen: (Invite) -> Unit, onAnswer: (Invite, Boolean) -> Unit) {
    if (invites.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🍕🍷", fontSize = 44.sp)
            Text("Nobody at the table yet.", fontWeight = FontWeight.SemiBold)
            Text("Open a pizza you like and tap “Mangiamo insieme?”.", color = Pal.muted, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(invites, key = { it.id }) { inv ->
            Row(
                Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.card)
                    .clickable(enabled = inv.status == InviteStatus.ACCEPTED) { onOpen(inv) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PizzaPhoto(inv.pizzaPhoto, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)), small = true)
                Column(Modifier.weight(1f)) {
                    Text(inv.other.name + if (inv.other.demo) " · sample" else "", fontWeight = FontWeight.SemiBold)
                    Text(
                        when (inv.status) {
                            InviteStatus.PENDING -> if (inv.outgoing) "You asked over their ${inv.pizzaType.label}. Waiting…" else "Wants to eat your ${inv.pizzaType.label} together"
                            InviteStatus.DECLINED -> if (inv.outgoing) "Not this time." else "You said no."
                            InviteStatus.ACCEPTED -> inv.lastMessage ?: "Said yes. Say hello!"
                        },
                        color = Pal.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (inv.status == InviteStatus.PENDING && !inv.outgoing) {
                        Row {
                            TextButton(onClick = { onAnswer(inv, true) }) { Text("Sì, mangiamo!", color = Pal.basil) }
                            TextButton(onClick = { onAnswer(inv, false) }) { Text("No, thanks", color = Pal.muted) }
                        }
                    }
                }
                Text(ago(inv.at), color = Pal.muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ChatScreen(chat: ChatUi, onSend: (String) -> Unit, onClose: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(chat.messages.size) { if (chat.messages.isNotEmpty()) list.animateScrollToItem(chat.messages.lastIndex) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Pal.fg) }
            PizzaPhoto(chat.invite.pizzaPhoto, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)), small = true)
            Column {
                Text(chat.invite.other.name, fontWeight = FontWeight.SemiBold)
                Text("over ${chat.invite.pizzaType.label}", color = Pal.muted, fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(chat.messages, key = { it.id }) { m ->
                Box(Modifier.animateItem().fillMaxWidth(), contentAlignment = if (m.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.text, color = if (m.mine) Pal.ink else Pal.fg,
                        modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp)).background(if (m.mine) Pal.flame else Pal.card).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(draft, { if (it.length <= Rules.MAX_COMMENT) draft = it }, "When and where?", Modifier.weight(1f))
            IconButton(onClick = { onSend(draft); draft = "" }, enabled = draft.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (draft.isNotBlank()) Pal.flame else Pal.muted)
            }
        }
    }
}
