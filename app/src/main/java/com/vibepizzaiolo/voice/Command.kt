package com.vibepizzaiolo.voice

/** Speech models in the app. Only the Italian one: the commands are Italian. */
enum class Lang { IT }

enum class AppTab { TIMER, DOUGH, PARTY, COMMUNITY, SETTINGS }

/** Voice only starts and stops the timer; everything else is done on screen. */
enum class Command { START, STOP }
