package com.vibepizzaiolo.ui.community

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.CommunityUi
import com.vibepizzaiolo.community.Pizza
import com.vibepizzaiolo.community.badge
import com.vibepizzaiolo.ui.Group
import com.vibepizzaiolo.ui.GroupDivider
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.SectionLabel

class MeActions(
    val editCard: () -> Unit, val open: (Pizza) -> Unit, val logOut: () -> Unit, val delete: () -> Unit,
    val server: (String) -> Unit, val retry: () -> Unit, val offline: () -> Unit,
)

/** Your style card, your pizzas, and the account. */
@Composable
fun MeScreen(ui: CommunityUi, avatar: Bitmap?, a: MeActions) {
    val me = ui.me ?: return
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var askServer by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(Pal.card), contentAlignment = Alignment.Center) {
                if (avatar != null) Image(avatar.asImageBitmap(), "You", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("👨‍🍳", fontSize = 34.sp)
            }
            Column {
                Text(me.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (avatar == null) Text("Take a selfie in Settings to see yourself as the pizzaiolo here.", color = Pal.muted, fontSize = 12.sp)
            }
        }
        me.card?.let { card ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.card).clickable(onClick = a.editCard).padding(14.dp)) {
                Text("MY STYLE", color = Pal.muted, fontSize = 11.sp)
                Text(card.badge(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Tap to change", color = Pal.flame, fontSize = 12.sp)
            }
        }

        SectionLabel("My pizzas")
        if (ui.myPizzas.isEmpty()) Text("Nothing posted yet. The round button below takes the photo.", color = Pal.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in ui.myPizzas) {
                Column(Modifier.clickable { a.open(p) }) {
                    PizzaPhoto(p.photo, Modifier.size(130.dp).clip(RoundedCornerShape(14.dp)), small = true)
                    Text(p.type.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(p.likePercent?.let { "♥ $it% · 💬 ${p.comments}" } ?: "no swipes yet", color = Pal.muted, fontSize = 11.sp)
                }
            }
        }

        SectionLabel("Community")
        Group {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (ui.offline) "On this phone only" else "Connected", fontWeight = FontWeight.SemiBold, color = if (ui.offline) Pal.flame else Pal.basil)
                Text(
                    when {
                        !ui.offline -> ui.serverUrl.removePrefix("https://")
                        ui.serverSet -> "The server did not answer. You see sample pizzaioli, and what you post here stays on this phone."
                        else -> "No server is set. You see sample pizzaioli, and what you post here stays on this phone."
                    },
                    color = Pal.muted, fontSize = 13.sp,
                )
            }
            GroupDivider()
            Row {
                TextButton(onClick = { askServer = true }) { Text("Server address") }
                if (ui.offline && ui.serverSet) TextButton(onClick = a.retry) { Text("Try again") }
                if (!ui.offline) TextButton(onClick = a.offline) { Text("Use phone only", color = Pal.muted) }
            }
        }

        SectionLabel("Account")
        Group {
            TextButton(onClick = a.logOut, modifier = Modifier.padding(horizontal = 6.dp)) { Text("Log out") }
            GroupDivider()
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 6.dp)) { Text("Delete my account", color = Pal.tomato) }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = Pal.card,
            title = { Text("Delete your account?") },
            text = { Text("Your pizzas, photos, comments, swipes and chats are removed for good.", color = Pal.muted) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; a.delete() }) { Text("Delete", color = Pal.tomato) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
        )
    }
    if (askServer) ServerDialog(ui.serverUrl, onSave = { askServer = false; a.server(it) }, onClose = { askServer = false })
}
