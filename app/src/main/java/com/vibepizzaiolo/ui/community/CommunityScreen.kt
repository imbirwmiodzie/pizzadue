@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.vibepizzaiolo.ui.community

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibepizzaiolo.community.CommunityUi
import com.vibepizzaiolo.community.CommunityViewModel
import com.vibepizzaiolo.community.DetailUi
import com.vibepizzaiolo.community.Section
import com.vibepizzaiolo.community.Stage
import com.vibepizzaiolo.share.ShareUtils
import com.vibepizzaiolo.ui.Pal

/** The Community tab: account, then swipe / gallery / tavola / me, with one pizza or a chat on top. */
@Composable
fun CommunityScreen(vm: CommunityViewModel, avatar: Bitmap?) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.start() }
    BackHandler(enabled = ui.chat != null || ui.post != null || ui.detail != null || ui.editingCard) { vm.back() }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.messageShown()
        }
    }

    // ---- the photo to post: camera app or gallery ----
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) vm.startPost(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.startPost(uri)
    }
    fun gallery() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    if (ui.askPhoto && ui.stage == Stage.HOME) {
        AlertDialog(
            onDismissRequest = { vm.askPhoto(false) }, containerColor = Pal.card,
            title = { Text("Post a pizza") },
            text = {
                Column {
                    TextButton(onClick = {
                        vm.askPhoto(false)
                        val uri = ShareUtils.newPhotoUri(context)
                        photoUri = uri
                        try { takePicture.launch(uri) } catch (_: ActivityNotFoundException) { gallery() }
                    }) { Text("📷  Take a photo", color = Pal.fg) }
                    TextButton(onClick = { vm.askPhoto(false); gallery() }) { Text("🖼  Photo from the gallery", color = Pal.fg) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { vm.askPhoto(false) }) { Text("Cancel") } },
        )
    }

    val layer = when {
        ui.stage == Stage.LOADING -> "loading"
        ui.stage == Stage.AUTH -> "auth"
        ui.stage == Stage.QUIZ -> "quiz"
        ui.editingCard -> "edit"
        ui.post != null -> "post"
        ui.chat != null -> "chat"
        else -> "main"
    }
    // what the leaving layer keeps showing while it fades out
    val lastPost = remember { Holder(ui.post) }.also { h -> ui.post?.let { h.value = it } }
    val lastChat = remember { Holder(ui.chat) }.also { h -> ui.chat?.let { h.value = it } }

    AnimatedContent(layer, label = "community", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, modifier = Modifier.fillMaxSize()) { l ->
        when (l) {
            "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.flame) }
            "auth" -> AuthScreen(ui, vm::signUp, vm::logIn, vm::setServerUrl, vm::connect)
            "quiz" -> StyleQuiz(ui.me?.name.orEmpty(), ui.busy, vm::saveCard)
            "edit" -> ui.me?.card?.let { StyleEditor(it, ui.busy, vm::saveCard) { vm.editCard(false) } }
            "post" -> (ui.post ?: lastPost.value)?.let { PostScreen(it, vm::editDraft, vm::submitPost, vm::cancelPost) }
            "chat" -> (ui.chat ?: lastChat.value)?.let { ChatScreen(it, vm::sendChat, vm::closeChat) }
            else -> Main(ui, vm, avatar)
        }
    }
}

private class Holder<T>(var value: T)

/** The sections, and one pizza opened on top; its photo flies between the two. */
@Composable
private fun Main(ui: CommunityUi, vm: CommunityViewModel, avatar: Bitmap?) {
    val lastDetail = remember { Holder<DetailUi?>(null) }.also { h -> ui.detail?.let { h.value = it } }
    SharedTransitionLayout {
        AnimatedContent(ui.detail?.pizza?.id, label = "detail", transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) }) { openId ->
            val shared = Shared(this@SharedTransitionLayout, this@AnimatedContent)
            val detail = (ui.detail ?: lastDetail.value)?.takeIf { it.pizza.id == openId }
            if (openId != null && detail != null) {
                DetailScreen(
                    detail, ui.me, ui.busy, shared,
                    DetailActions(vm::closeDetail, vm::rate, vm::comment, vm::invite, vm::report, vm::block),
                )
            } else {
                Home(ui, vm, avatar, shared)
            }
        }
    }
}

@Composable
private fun Home(ui: CommunityUi, vm: CommunityViewModel, avatar: Bitmap?, shared: Shared) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pizzaioli", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (ui.offline) {
                    Text(
                        "on this phone · samples", color = Pal.flame, fontSize = 11.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.flame.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in Section.entries) {
                    val waiting = s == Section.TABLE && ui.invites.any { !it.outgoing && it.status == com.vibepizzaiolo.community.InviteStatus.PENDING }
                    Pill(s.label + if (waiting) " •" else "", selected = ui.section == s) { vm.select(s) }
                }
            }
            AnimatedContent(ui.section, label = "section", transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, modifier = Modifier.fillMaxSize()) { section ->
                when (section) {
                    Section.SWIPE -> SwipeDeck(ui.deck, ui.deckLoading, shared.takeIf { ui.section == Section.SWIPE }, vm::swipe, vm::open, vm::refreshDeck)
                    Section.GALLERY -> GalleryScreen(ui.filter, ui.results, ui.searching, shared.takeIf { ui.section == Section.GALLERY }, vm::setFilter, vm::open)
                    Section.TABLE -> TableScreen(ui.invites, vm::openChat, vm::answer)
                    Section.ME -> MeScreen(
                        ui, avatar,
                        MeActions({ vm.editCard(true) }, vm::open, { vm.logOut() }, { vm.deleteAccount() }, vm::setServerUrl, vm::connect, vm::workOffline),
                    )
                }
            }
        }
        if (ui.section != Section.SWIPE) {
            FloatingActionButton(
                onClick = { vm.askPhoto(true) }, containerColor = Pal.flame, contentColor = Pal.ink, shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp),
            ) { Icon(Icons.Default.PhotoCamera, "Post a pizza") }
        }
    }
}
