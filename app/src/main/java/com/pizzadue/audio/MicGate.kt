package com.pizzadue.audio

import android.os.SystemClock

/**
 * Keeps the microphone from hearing the app's own voice: while a clip or a spoken reply
 * plays (and shortly after), recognition results are thrown away.
 */
object MicGate {
    @Volatile private var speaking = 0
    @Volatile private var quietUntil = 0L

    @Synchronized fun begin() { speaking++ }
    @Synchronized fun end() {
        speaking = (speaking - 1).coerceAtLeast(0)
        quietUntil = SystemClock.elapsedRealtime() + 500
    }

    val isMuted: Boolean get() = speaking > 0 || SystemClock.elapsedRealtime() < quietUntil
}
