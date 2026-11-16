package com.vibepizzaiolo.ui.community

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.CommunityUi
import com.vibepizzaiolo.ui.Pal

/** Welcome: create an account or log in. A pizza turns slowly above the form. */
@Composable
fun AuthScreen(
    ui: CommunityUi,
    onSignUp: (email: String, password: String, name: String) -> Unit,
    onLogIn: (email: String, password: String) -> Unit,
    onServer: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var signUp by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var askServer by rememberSaveable { mutableStateOf(false) }
    val turn by rememberInfiniteTransition(label = "pizza").animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "turn",
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PizzaPhoto("paint:MARGHERITA:7", Modifier.size(150.dp).graphicsLayer { rotationZ = turn }.clip(CircleShape))
        Text("Pizzaioli", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Swipe other people's pizzas, show yours, and find someone to eat with.",
            color = Pal.muted, textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Create account", selected = signUp) { signUp = true }
            Pill("Log in", selected = !signUp) { signUp = false }
        }
        AnimatedVisibility(signUp) { Field(name, { name = it }, "Pizzaiolo name") }
        Field(email, { email = it }, "Email", keyboard = KeyboardType.Email)
        Field(password, { password = it }, if (signUp) "Password (8 characters or more)" else "Password", password = true)
        BigButton(if (signUp) "Create account" else "Log in", busy = ui.busy) {
            if (signUp) onSignUp(email, password, name) else onLogIn(email, password)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                !ui.offline -> "Connected to ${ui.serverUrl.removePrefix("https://")}."
                ui.serverSet -> "The server is not answering, so this account lives on this phone only, with sample pizzaioli."
                else -> "No community server is set yet: the account, your pizzas and your swipes stay on this phone, with sample pizzaioli to try it out."
            },
            color = Pal.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Row {
            if (ui.offline && ui.serverSet) TextButton(onClick = onRetry) { Text("Try the server again", color = Pal.flame) }
            TextButton(onClick = { askServer = true }) { Text("Server address", color = Pal.muted) }
        }
    }
    if (askServer) ServerDialog(ui.serverUrl, onSave = { askServer = false; onServer(it) }, onClose = { askServer = false })
}

@Composable
fun ServerDialog(current: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var url by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose, containerColor = Pal.card,
        title = { Text("Community server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The address of your server, starting with https://. Leave it empty to keep everything on this phone.", color = Pal.muted, fontSize = 13.sp)
                Field(url, { url = it }, "https://…", keyboard = KeyboardType.Uri)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
