package com.vibepizzaiolo.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalPizza
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibepizzaiolo.community.CommunityViewModel
import com.vibepizzaiolo.ui.community.CommunityScreen
import com.vibepizzaiolo.party.PartyStage
import com.vibepizzaiolo.party.PartyViewModel
import com.vibepizzaiolo.ui.party.NextUpBanner
import com.vibepizzaiolo.ui.party.PartyScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.vibepizzaiolo.MainViewModel
import com.vibepizzaiolo.VideoUi
import com.vibepizzaiolo.share.RecordPizzaVideo
import com.vibepizzaiolo.share.TakeSelfie
import com.vibepizzaiolo.share.ShareUtils
import com.vibepizzaiolo.timer.Alert
import com.vibepizzaiolo.timer.Phase
import com.vibepizzaiolo.voice.AppTab

@Composable
fun App(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val voice by vm.voiceStatus.collectAsStateWithLifecycle()
    val share by vm.share.collectAsStateWithLifecycle()
    val bakeLog by vm.bakeLog.collectAsStateWithLifecycle()
    val video by vm.video.collectAsStateWithLifecycle()
    val avatar by vm.avatar.collectAsStateWithLifecycle()
    val avatarBusy by vm.avatarBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val community: CommunityViewModel = viewModel()
    val party: PartyViewModel = viewModel()
    val partyUi by party.ui.collectAsStateWithLifecycle()
    // a pizza night in progress is fetched at once, so the timer can say whose pizza is next
    LaunchedEffect(Unit) { party.start() }
    val baking = partyUi.party?.stage == PartyStage.BAKING
    LaunchedEffect(tab, baking) {
        // the banner above the timer follows what is ticked on the Serata tab or by a bake
        if (tab == AppTab.TIMER && baking) while (true) { kotlinx.coroutines.delay(10_000); party.refresh() }
    }

    // ---- photo for sharing: phone's camera app or the gallery ----
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var askSource by rememberSaveable { mutableStateOf(false) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = photoUri
        if (ok && uri != null) vm.openShare(uri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.openShare(uri)
    }
    fun openCamera() {
        val uri = ShareUtils.newPhotoUri(context)
        photoUri = uri
        try {
            takePicture.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No camera app found, pick a photo instead", Toast.LENGTH_LONG).show()
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // ---- the pizza reel: record with the camera app, or pick a video ----
    var videoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val recordVideo = rememberLauncherForActivityResult(RecordPizzaVideo()) { ok ->
        val uri = videoUri
        if (ok && uri != null) vm.makeVideo(uri)
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.makeVideo(uri)
    }
    fun startRecording() {
        val uri = ShareUtils.newVideoUri(context)
        videoUri = uri
        try {
            recordVideo.launch(uri)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No camera app found, pick a video instead", Toast.LENGTH_LONG).show()
            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    // ---- your selfie, made into a cartoon ----
    var selfieUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takeSelfie = rememberLauncherForActivityResult(TakeSelfie()) { ok ->
        val uri = selfieUri
        if (ok && uri != null) vm.makeAvatar(uri)
    }
    val pickSelfie = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.makeAvatar(uri)
    }
    fun selfie() {
        val uri = ShareUtils.newPhotoUri(context)
        selfieUri = uri
        try {
            takeSelfie.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pickSelfie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    if (askSource) {
        AlertDialog(
            onDismissRequest = { askSource = false },
            title = { Text("Share your pizza") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { askSource = false; vm.openVideoGuide() }) {
                        Text("🎬  Video reel (film 15 s, the app edits it)", color = Pal.flame)
                    }
                    TextButton(onClick = { askSource = false; openCamera() }) { Text("📷  Take a photo", color = Pal.fg) }
                    TextButton(onClick = {
                        askSource = false
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("🖼  Photo from the gallery", color = Pal.fg) }
                    TextButton(onClick = {
                        askSource = false
                        vm.selectTab(AppTab.COMMUNITY)
                        community.askPhoto(true)
                    }) { Text("🍕  Post it to the pizzaioli", color = Pal.fg) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { askSource = false }) { Text("Cancel") } },
            containerColor = Pal.card,
        )
    }

    // ---- reel screens replace the tabs while open ----
    val videoUi = video
    if (videoUi != null) {
        Scaffold(containerColor = Pal.bg) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                if (videoUi is VideoUi.Guide) {
                    RecordGuideScreen(
                        onRecord = ::startRecording,
                        onGallery = { pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        onClose = vm::closeVideo,
                    )
                } else {
                    VideoScreen(
                        ui = videoUi,
                        onShare = { target -> (videoUi as? VideoUi.Ready)?.file?.let { ShareUtils.shareVideo(context, it, vm.caption(), target) } },
                        onSave = {
                            val file = (videoUi as? VideoUi.Ready)?.file
                            val ok = file != null && ShareUtils.saveVideoToGallery(context, file)
                            Toast.makeText(
                                context,
                                if (ok) "Saved to Movies/VibePizzaiolo" else "Saving needs Android 10 or newer; use Share instead",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onClose = vm::closeVideo,
                    )
                }
            }
        }
        return
    }

    // ---- share screen replaces the tabs while open ----
    val shareUi = share
    if (shareUi != null) {
        Scaffold(containerColor = Pal.bg) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                ShareScreen(
                    ui = shareUi,
                    onDesign = vm::setDesign,
                    onFormat = vm::setFormat,
                    onShare = { target -> shareUi.card?.let { ShareUtils.share(context, it, vm.caption(), target) } },
                    onSave = {
                        val card = shareUi.card
                        val ok = card != null && ShareUtils.saveToGallery(context, card)
                        Toast.makeText(
                            context,
                            if (ok) "Saved to Pictures/VibePizzaiolo" else "Saving needs Android 10 or newer; use Share instead",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onClose = vm::closeShare,
                )
            }
        }
        return
    }

    // a pizza came out in the last 30 minutes and nothing is running: offer to share it
    val justBaked = bakeLog.lastAt > 0 &&
        System.currentTimeMillis() - bakeLog.lastAt < 30 * 60_000L &&
        !timer.isActive && timer.alert == Alert.NONE

    Scaffold(
        containerColor = Pal.bg,
        bottomBar = {
            NavigationBar(containerColor = Pal.bg) {
                val colors = NavigationBarItemDefaults.colors(indicatorColor = Pal.card, selectedIconColor = Pal.fg, selectedTextColor = Pal.fg, unselectedIconColor = Pal.muted, unselectedTextColor = Pal.muted)
                NavigationBarItem(tab == AppTab.TIMER, { vm.selectTab(AppTab.TIMER) }, { Icon(Icons.Default.Timer, null) }, label = { Text("Timer") }, colors = colors)
                NavigationBarItem(tab == AppTab.DOUGH, { vm.selectTab(AppTab.DOUGH) }, { Icon(Icons.Outlined.BakeryDining, null) }, label = { Text("Dough") }, colors = colors)
                NavigationBarItem(tab == AppTab.PARTY, { vm.selectTab(AppTab.PARTY) }, { Icon(Icons.Outlined.Groups, null) }, label = { Text("Serata") }, colors = colors)
                NavigationBarItem(tab == AppTab.COMMUNITY, { vm.selectTab(AppTab.COMMUNITY) }, { Icon(Icons.Outlined.LocalPizza, null) }, label = { Text("Pizzaioli") }, colors = colors)
                NavigationBarItem(tab == AppTab.SETTINGS, { vm.selectTab(AppTab.SETTINGS) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, colors = colors)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                AppTab.TIMER -> Column(Modifier.fillMaxSize()) {
                    // during a pizza night: whose pizza goes in next, above the timer
                    val night = partyUi.party?.takeIf { it.stage == PartyStage.BAKING }
                    if (night != null) NextUpBanner(night) { vm.selectTab(AppTab.PARTY) }
                    Box(Modifier.weight(1f)) {
                        TimerScreen(
                            timer, settings, voice,
                            TimerActions(
                                selectMode = vm::selectMode,
                                setDuration = vm::setDuration,
                                main = { if (timer.isActive) vm.stop() else vm.startCurrent() },
                                pauseResume = { if (timer.phase == Phase.PAUSED) vm.resume() else vm.pause() },
                                stop = vm::stop,
                                share = { askSource = true },
                            ),
                            justBaked = justBaked,
                            avatar = avatar,
                        )
                    }
                }
                AppTab.DOUGH -> DoughScreen(settings, voice, vm::updateSettings) { vm.selectTab(AppTab.SETTINGS) }
                AppTab.PARTY -> PartyScreen(party, settings) { vm.selectTab(AppTab.TIMER) }
                AppTab.COMMUNITY -> CommunityScreen(community, avatar)
                AppTab.SETTINGS -> SettingsScreen(
                    settings, voice, vm::updateSettings, vm::preview, vm::previewVolume,
                    avatar = AvatarActions(avatar, avatarBusy, ::selfie, {
                        pickSelfie.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, vm::removeAvatar),
                )
            }
        }
    }
}
