package com.pizzadue.voice

enum class Lang { EN, PL }

enum class AppTab { TIMER, DOUGH, SETTINGS }

/** Voice only starts and stops the timer; everything else is done on screen. */
enum class Command { START, STOP }
