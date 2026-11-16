@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.vibepizzaiolo.ui.community

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.community.Photos
import com.vibepizzaiolo.ui.Mono
import com.vibepizzaiolo.ui.Pal

/** The scopes a photo needs to fly between the gallery and the detail view. */
class Shared(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

@Composable
fun Modifier.sharedPhoto(shared: Shared?, pizzaId: String): Modifier {
    if (shared == null) return this
    return with(shared.transition) {
        this@sharedPhoto.sharedElement(rememberSharedContentState("photo-$pizzaId"), shared.visibility)
    }
}

/** A pizza's picture, fading in once it is loaded. */
@Composable
fun PizzaPhoto(ref: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val context = LocalContext.current
    var bmp by remember(ref, small) { mutableStateOf(Photos.cached(ref, small)) }
    LaunchedEffect(ref, small) { if (bmp == null) bmp = Photos.load(context, ref, small) }
    val alpha by animateFloatAsState(if (bmp != null) 1f else 0f, tween(250), label = "photo")
    Box(modifier.background(Pal.card)) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, color: Color = Pal.flame, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = if (selected) Pal.ink else Pal.fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color else Pal.card)
            .border(1.dp, if (selected) color else Pal.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** A small fact under a photo: "450 °C wood", "70% hydration". */
@Composable
fun Fact(text: String) {
    Text(
        text, color = Pal.muted, fontFamily = Mono, fontSize = 11.sp,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Pal.raised.copy(alpha = 0.5f)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, color: Color = Pal.flame, onClick: () -> Unit) {
    // on the grey of an unchosen button, dark text would vanish
    val textColor = if (color == Pal.raised) Pal.fg else Pal.ink
    Button(
        onClick = onClick, enabled = enabled && !busy, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor, disabledContainerColor = Pal.raised, disabledContentColor = Pal.muted),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) CircularProgressIndicator(Modifier.height(18.dp).padding(end = 0.dp), color = Pal.muted, strokeWidth = 2.dp)
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Pal.flame, unfocusedBorderColor = Pal.line, focusedLabelColor = Pal.flame,
            unfocusedLabelColor = Pal.muted, cursorColor = Pal.flame, focusedTextColor = Pal.fg, unfocusedTextColor = Pal.fg,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - at) / 60_000
    return when {
        min < 1 -> "now"
        min < 60 -> "$min min"
        min < 60 * 24 -> "${min / 60} h"
        else -> "${min / (60 * 24)} d"
    }
}
