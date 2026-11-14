package com.pizzadue.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pizzadue.MainViewModel
import com.pizzadue.timer.Phase
import com.pizzadue.voice.AppTab

@Composable
fun App(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timer by vm.timer.collectAsStateWithLifecycle()
    val voice by vm.voiceStatus.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Pal.bg,
        bottomBar = {
            NavigationBar(containerColor = Pal.bg) {
                val colors = NavigationBarItemDefaults.colors(indicatorColor = Pal.card, selectedIconColor = Pal.fg, selectedTextColor = Pal.fg, unselectedIconColor = Pal.muted, unselectedTextColor = Pal.muted)
                NavigationBarItem(tab == AppTab.TIMER, { vm.selectTab(AppTab.TIMER) }, { Icon(Icons.Default.Timer, null) }, label = { Text("Timer") }, colors = colors)
                NavigationBarItem(tab == AppTab.DOUGH, { vm.selectTab(AppTab.DOUGH) }, { Icon(Icons.Outlined.BakeryDining, null) }, label = { Text("Dough") }, colors = colors)
                NavigationBarItem(tab == AppTab.SETTINGS, { vm.selectTab(AppTab.SETTINGS) }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, colors = colors)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                AppTab.TIMER -> TimerScreen(
                    timer, settings, voice,
                    TimerActions(
                        selectMode = vm::selectMode,
                        setDuration = vm::setDuration,
                        main = { if (timer.isActive || timer.phase == Phase.DONE) vm.stop() else vm.startCurrent() },
                        pauseResume = { if (timer.phase == Phase.PAUSED) vm.resume() else vm.pause() },
                        stop = vm::stop,
                    ),
                )
                AppTab.DOUGH -> DoughScreen(settings, voice, vm::updateSettings) { vm.selectTab(AppTab.SETTINGS) }
                AppTab.SETTINGS -> SettingsScreen(settings, voice, vm::updateSettings, vm::preview)
            }
        }
    }
}
