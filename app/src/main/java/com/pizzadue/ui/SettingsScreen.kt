package com.pizzadue.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pizzadue.audio.Clip
import com.pizzadue.settings.Settings
import com.pizzadue.voice.VoiceStatus
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(s: Settings, voice: VoiceStatus, update: ((Settings) -> Settings) -> Unit, preview: (Clip) -> Unit) {
    fun step(v: Float, d: Float, range: ClosedFloatingPointRange<Float>) = ((v + d) * 100).roundToInt().div(100f).coerceIn(range)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScreenHeader("Settings", null)

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
            SwitchRow("Maximum alarm volume", "while a timer runs, then back to your level", s.maxVolume, { v -> update { it.copy(maxVolume = v) } })
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

        SectionLabel("Screen")
        Group {
            SwitchRow("Always on, full brightness", "same as ☀ on the Timer tab", s.bright, { v -> update { it.copy(bright = v) } })
        }

        SectionLabel("Voice commands")
        Group {
            SwitchRow("English", "vai!, pizza out, time left…", s.voiceEn, { v -> update { it.copy(voiceEn = v) } })
            GroupDivider()
            SwitchRow("Polski", "start, wyjmij, ile zostało…", s.voicePl, { v -> update { it.copy(voicePl = v) } })
        }
        val status = when (voice) {
            is VoiceStatus.Listening -> "Listening, offline."
            VoiceStatus.Preparing -> "Preparing the speech models (first start only)…"
            VoiceStatus.NoPermission -> "Microphone permission is off. Allow it in Android settings → Apps → PizzaDue."
            is VoiceStatus.Error -> voice.message
            VoiceStatus.Off -> "Voice is off."
        }
        Text(status, color = Pal.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
        Text("▶ play", fontFamily = Mono, fontSize = 12.sp, color = Pal.flame)
    }
}
