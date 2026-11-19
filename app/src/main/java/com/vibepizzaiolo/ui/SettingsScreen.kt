package com.vibepizzaiolo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepizzaiolo.audio.Clip
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.settings.SealText
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextAlign
import com.vibepizzaiolo.voice.VoiceStatus
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    s: Settings,
    voice: VoiceStatus,
    update: ((Settings) -> Settings) -> Unit,
    preview: (Clip) -> Unit,
    previewVolume: () -> Unit,
    avatar: AvatarActions? = null,
) {
    fun step(v: Float, d: Float, range: ClosedFloatingPointRange<Float>) = ((v + d) * 100).roundToInt().div(100f).coerceIn(range)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Settings", null)

        if (avatar != null) {
            SectionLabel("You, the pizzaiolo")
            Group { AvatarRow(avatar) }
        }

        SectionLabel("Your reels")
        Group { ReelSettings(s, update) }

        SectionLabel("Screen")
        Group {
            SwitchRow("☀ Always on, full brightness", "while the app is open", s.bright, { v -> update { it.copy(bright = v) } })
        }

        SectionLabel("Dough, % of flour")
        Group {
            StepperRow("Hydration", "water", pct(s.hydration),
                { update { it.copy(hydration = step(it.hydration, -1f, Settings.HYDRATION)) } },
                { update { it.copy(hydration = step(it.hydration, 1f, Settings.HYDRATION)) } })
            GroupDivider()
            StepperRow("Salt", null, "%.1f%%".format(s.salt),
                { update { it.copy(salt = step(it.salt, -0.1f, Settings.SALT)) } },
                { update { it.copy(salt = step(it.salt, 0.1f, Settings.SALT)) } })
            GroupDivider()
            StepperRow("Yeast", null, "%.2f%%".format(s.yeast),
                { update { it.copy(yeast = step(it.yeast, -0.05f, Settings.YEAST)) } },
                { update { it.copy(yeast = step(it.yeast, 0.05f, Settings.YEAST)) } })
        }

        SectionLabel("Timer")
        Group {
            StepperRow("Bake time", "15 s steps", formatClock(s.bakeSec * 1000L),
                { update { it.copy(bakeSec = (it.bakeSec - 15).coerceIn(Settings.BAKE_SEC)) } },
                { update { it.copy(bakeSec = (it.bakeSec + 15).coerceIn(Settings.BAKE_SEC)) } })
            GroupDivider()
            StepperRow("Preheat time", "5 min steps", formatClock(s.preheatSec * 1000L),
                { update { it.copy(preheatSec = (it.preheatSec - 300).coerceIn(Settings.PREHEAT_SEC)) } },
                { update { it.copy(preheatSec = (it.preheatSec + 300).coerceIn(Settings.PREHEAT_SEC)) } })
            GroupDivider()
            SwitchRow("Ticks", "bake: 1-2-3 on quarters, long on the minute; preheat: at each third", s.ticks, { v -> update { it.copy(ticks = v) } })
        }

        SectionLabel("Loudness")
        Group {
            SwitchRow(
                "Play as alarm",
                if (s.asAlarm) "on: alarm volume, plays even on silent, always from the phone's speaker"
                else "off: media volume, plays through Bluetooth or headphones like music",
                s.asAlarm, { v -> update { it.copy(asAlarm = v) } },
            )
            GroupDivider()
            SwitchRow("Night mode", "quiet: at most 15% and vibration on", s.night, { v -> update { it.copy(night = v) } })
            GroupDivider()
            VolumeRow(s.volume, onChange = { v -> update { it.copy(volume = v) } }, onDone = previewVolume)
            GroupDivider()
            SwitchRow("Vibrate with every tick", null, s.vibrate, { v -> update { it.copy(vibrate = v) } })
            GroupDivider()
            SwitchRow("Flashlight blink with every tick", null, s.flash, { v -> update { it.copy(flash = v) } })
        }

        SectionLabel("Voice clips")
        Group {
            SwitchRow("La pizza è pronta!", "end of bake, repeats every 10 s", s.pronta, { v -> update { it.copy(pronta = v) } }) { PlayButton { preview(Clip.PRONTA) } }
            GroupDivider()
            SwitchRow("Forno a legna caldo", "end of preheat, repeats every 30 s", s.forno, { v -> update { it.copy(forno = v) } }) { PlayButton { preview(Clip.FORNO) } }
            GroupDivider()
            SwitchRow("Gira, gira!", "turn the pizza, at half the bake time", s.gira, { v -> update { it.copy(gira = v) } }) { PlayButton { preview(Clip.GIRA) } }
        }


        SectionLabel("Voice commands")
        Group {
            SwitchRow("English", "start: vai! · waj! · pizza in · start   stop: pizza out · stop", s.voiceEn, { v -> update { it.copy(voiceEn = v) } })
            GroupDivider()
            SwitchRow("Polski", "start: start   stop: wyjmij · stop · koniec", s.voicePl, { v -> update { it.copy(voicePl = v) } })
        }
        val status = when (voice) {
            is VoiceStatus.Listening -> "Listening, offline."
            VoiceStatus.Preparing -> "Preparing the speech models (first start only)…"
            VoiceStatus.NoPermission -> "Microphone permission is off. Allow it in Android settings → Apps → VibePizzaiolo."
            is VoiceStatus.Error -> voice.message
            VoiceStatus.Off -> "Voice is off."
        }
        Text(status, color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
    }
}

/** Your cartoon portrait and what you can do with it. */
class AvatarActions(
    val picture: Bitmap?,
    val busy: Boolean,
    val takeSelfie: () -> Unit,
    val pickPhoto: () -> Unit,
    val remove: () -> Unit,
)

@Composable
private fun AvatarRow(a: AvatarActions) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Pal.raised), contentAlignment = Alignment.Center) {
            when {
                a.busy -> CircularProgressIndicator(color = Pal.flame, modifier = Modifier.size(32.dp))
                a.picture != null -> Image(a.picture.asImageBitmap(), "Your cartoon portrait", Modifier.size(84.dp))
                else -> Icon(Icons.Default.Face, null, tint = Pal.muted, modifier = Modifier.size(44.dp))
            }
        }
        Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (a.picture == null) "Take a selfie: the app draws you as a cartoon. You become the pizzaiolo at the end of your reels."
                else "This is you as the pizzaiolo: your icon and the chef at the end of your reels.",
                fontSize = 13.sp, color = Pal.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = a.takeSelfie, enabled = !a.busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Selfie", color = Pal.flame) }
                OutlinedButton(onClick = a.pickPhoto, enabled = !a.busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Gallery", color = Pal.fg) }
                if (a.picture != null) {
                    OutlinedButton(onClick = a.remove, enabled = !a.busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Remove", color = Pal.muted) }
                }
            }
        }
    }
}

@Composable
private fun VolumeRow(percent: Int, onChange: (Int) -> Unit, onDone: () -> Unit) {
    // local value so the slider moves smoothly; saved on every step
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowLabel("Beep volume", "while a timer runs; your volume comes back after", Modifier.weight(1f))
            Text("${value.roundToInt()}%", fontFamily = Mono, fontSize = 16.sp, color = Pal.flame)
        }
        Slider(
            value = value,
            onValueChange = { value = it; onChange(it.roundToInt()) },
            onValueChangeFinished = onDone,
            valueRange = 5f..100f,
            steps = 18,
            colors = SliderDefaults.colors(thumbColor = Pal.flame, activeTrackColor = Pal.flame, inactiveTrackColor = Pal.line),
        )
        Text("The phone's volume buttons also change it while the app is open.", color = Pal.muted, fontSize = 12.sp)
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
        Text("▶ play", fontFamily = Mono, fontSize = 12.sp, color = Pal.flame)
    }
}

/** What your reels say about you: handle, the oven and the dough, the series, the seal. */
@Composable
private fun ReelSettings(s: Settings, update: ((Settings) -> Settings) -> Unit) {
    var handle by remember { mutableStateOf(s.handle) }
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        OutlinedTextField(
            value = handle,
            onValueChange = { v -> handle = v.take(Settings.HANDLE); update { it.copy(handle = handle) } },
            label = { Text("Your @handle or name") },
            placeholder = { Text("@pizza_di_marco") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Printed big at the end of every reel and on its cover: the reel is yours.", color = Pal.muted, fontSize = 12.sp)
    }
    GroupDivider()
    StepperRow("Oven temperature", "on the reel; 0 hides it", if (s.ovenC == 0) "–" else "${s.ovenC} °C",
        { update { it.copy(ovenC = (it.ovenC - 10).coerceIn(Settings.OVEN_C)) } },
        { update { it.copy(ovenC = if (it.ovenC == 0) 400 else (it.ovenC + 10).coerceIn(Settings.OVEN_C)) } })
    GroupDivider()
    StepperRow("Dough fermentation", "hours; 0 hides it", if (s.fermentH == 0) "–" else "${s.fermentH} h",
        { update { it.copy(fermentH = (it.fermentH - 2).coerceIn(Settings.FERMENT_H)) } },
        { update { it.copy(fermentH = if (it.fermentH == 0) 24 else (it.fermentH + 2).coerceIn(Settings.FERMENT_H)) } })
    GroupDivider()
    StepperRow("A series", "“Pizza nº 47 / 100”; 0 is no series", if (s.seriesGoal == 0) "–" else "${s.seriesGoal}",
        { update { it.copy(seriesGoal = (it.seriesGoal - 10).coerceIn(Settings.SERIES)) } },
        { update { it.copy(seriesGoal = (it.seriesGoal + 10).coerceIn(Settings.SERIES)) } })
    GroupDivider()
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RowLabel("The seal says", "on reels and the Stamp card")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            SealText.entries.forEach { st ->
                val on = st == s.seal
                Surface(
                    onClick = { update { it.copy(seal = st) } },
                    shape = RoundedCornerShape(10.dp),
                    color = if (on) Pal.raised else Pal.bg,
                    border = if (on) BorderStroke(1.5.dp, Pal.flame) else null,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(st.label, fontSize = 12.sp, color = if (on) Pal.flame else Pal.fg, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    GroupDivider()
    SwitchRow("Small app tag at the end", "“made with VibePizzaiolo”, under your handle", s.appTag, { v -> update { it.copy(appTag = v) } })
}
