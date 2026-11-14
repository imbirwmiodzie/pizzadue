package com.pizzadue.voice

enum class Lang { EN, PL }

enum class AppTab { TIMER, DOUGH, SETTINGS }

sealed interface Command {
    data object StartBake : Command
    data object StartPreheat : Command
    data object Stop : Command
    data object TimeLeft : Command
    data object Pause : Command
    data object Resume : Command
    data class SetBake(val seconds: Int) : Command
    data class Bright(val on: Boolean) : Command
    data class Hydration(val percent: Int) : Command
    data class Dough(val balls: Int) : Command
    data class Tab(val tab: AppTab) : Command
    data class Ticks(val on: Boolean) : Command
}
