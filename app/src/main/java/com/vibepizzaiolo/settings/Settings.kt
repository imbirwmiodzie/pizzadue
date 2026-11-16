package com.vibepizzaiolo.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class Settings(
    val hydration: Float = 70f,
    val salt: Float = 4f,
    val yeast: Float = 0.5f,
    val bakeSec: Int = 90,
    val preheatSec: Int = 30 * 60,
    val ticks: Boolean = true,
    val pronta: Boolean = true,
    val forno: Boolean = true,
    val gira: Boolean = true,
    val bright: Boolean = true,
    /** Volume while a timer runs, % of the phone's alarm maximum. */
    val volume: Int = 40,
    /** Night mode: volume capped low and vibration on, for sleeping neighbours. */
    val night: Boolean = false,
    /** Play as alarm (true) or as media (false). */
    val asAlarm: Boolean = true,
    val vibrate: Boolean = false,
    val flash: Boolean = false,
    val voiceEn: Boolean = true,
    val voicePl: Boolean = true,
    val balls: Int = 4,
    val ballWeight: Int = 250,
) {
    companion object {
        val HYDRATION = 50f..90f
        val SALT = 0f..5f
        val YEAST = 0f..3f
        val BAKE_SEC = 15..30 * 60
        val PREHEAT_SEC = 5 * 60..90 * 60
    }
}

/** The pizzas baked so far: numbers the share cards ("Pizza nº 23") and gives the last bake time. */
data class BakeLog(val count: Int = 0, val lastBakeMs: Long = 0L, val lastAt: Long = 0L)

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.store

    private object K {
        val hydration = floatPreferencesKey("hydration")
        val salt = floatPreferencesKey("salt")
        val yeast = floatPreferencesKey("yeast")
        val bakeSec = intPreferencesKey("bake_sec")
        val preheatSec = intPreferencesKey("preheat_sec")
        val ticks = booleanPreferencesKey("ticks")
        val pronta = booleanPreferencesKey("pronta")
        val forno = booleanPreferencesKey("forno")
        val gira = booleanPreferencesKey("gira")
        val bright = booleanPreferencesKey("bright")
        // new key: earlier installs saved 100%, the new default is 40%
        val volume = intPreferencesKey("volume_v2")
        val night = booleanPreferencesKey("night")
        val asAlarm = booleanPreferencesKey("as_alarm")
        val vibrate = booleanPreferencesKey("vibrate_v2")
        val flash = booleanPreferencesKey("flash")
        val voiceEn = booleanPreferencesKey("voice_en")
        val voicePl = booleanPreferencesKey("voice_pl")
        val balls = intPreferencesKey("balls")
        val ballWeight = intPreferencesKey("ball_weight")
        val bakeCount = intPreferencesKey("bake_count")
        val lastBakeMs = longPreferencesKey("last_bake_ms")
        val lastBakeAt = longPreferencesKey("last_bake_at")
    }

    val bakeLog: Flow<BakeLog> = store.data.map { p ->
        BakeLog(p[K.bakeCount] ?: 0, p[K.lastBakeMs] ?: 0L, p[K.lastBakeAt] ?: 0L)
    }

    /** One more pizza out of the oven, after [bakeMs] in it. */
    suspend fun recordBake(bakeMs: Long) {
        store.edit { p ->
            p[K.bakeCount] = (p[K.bakeCount] ?: 0) + 1
            p[K.lastBakeMs] = bakeMs
            p[K.lastBakeAt] = System.currentTimeMillis()
        }
    }

    val flow: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            hydration = p[K.hydration] ?: d.hydration,
            salt = p[K.salt] ?: d.salt,
            yeast = p[K.yeast] ?: d.yeast,
            bakeSec = p[K.bakeSec] ?: d.bakeSec,
            preheatSec = p[K.preheatSec] ?: d.preheatSec,
            ticks = p[K.ticks] ?: d.ticks,
            pronta = p[K.pronta] ?: d.pronta,
            forno = p[K.forno] ?: d.forno,
            gira = p[K.gira] ?: d.gira,
            bright = p[K.bright] ?: d.bright,
            volume = p[K.volume] ?: d.volume,
            night = p[K.night] ?: d.night,
            asAlarm = p[K.asAlarm] ?: d.asAlarm,
            vibrate = p[K.vibrate] ?: d.vibrate,
            flash = p[K.flash] ?: d.flash,
            voiceEn = p[K.voiceEn] ?: d.voiceEn,
            voicePl = p[K.voicePl] ?: d.voicePl,
            balls = p[K.balls] ?: d.balls,
            ballWeight = p[K.ballWeight] ?: d.ballWeight,
        )
    }

    /**
     * The latest settings, kept loaded for the life of the process. Read this where the value is
     * needed at once (the timer service); a fresh `stateIn` would hand out the defaults first.
     */
    val state: StateFlow<Settings> =
        flow.stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, Settings())

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val d = Settings()
            val cur = Settings(
                p[K.hydration] ?: d.hydration, p[K.salt] ?: d.salt, p[K.yeast] ?: d.yeast,
                p[K.bakeSec] ?: d.bakeSec, p[K.preheatSec] ?: d.preheatSec,
                p[K.ticks] ?: d.ticks, p[K.pronta] ?: d.pronta, p[K.forno] ?: d.forno, p[K.gira] ?: d.gira,
                p[K.bright] ?: d.bright, p[K.volume] ?: d.volume, p[K.night] ?: d.night, p[K.asAlarm] ?: d.asAlarm, p[K.vibrate] ?: d.vibrate, p[K.flash] ?: d.flash,
                p[K.voiceEn] ?: d.voiceEn, p[K.voicePl] ?: d.voicePl,
                p[K.balls] ?: d.balls, p[K.ballWeight] ?: d.ballWeight,
            )
            val s = transform(cur)
            p[K.hydration] = s.hydration.coerceIn(Settings.HYDRATION)
            p[K.salt] = s.salt.coerceIn(Settings.SALT)
            p[K.yeast] = s.yeast.coerceIn(Settings.YEAST)
            p[K.bakeSec] = s.bakeSec.coerceIn(Settings.BAKE_SEC)
            p[K.preheatSec] = s.preheatSec.coerceIn(Settings.PREHEAT_SEC)
            p[K.ticks] = s.ticks
            p[K.pronta] = s.pronta
            p[K.forno] = s.forno
            p[K.gira] = s.gira
            p[K.bright] = s.bright
            p[K.volume] = s.volume.coerceIn(5, 100)
            p[K.night] = s.night
            p[K.asAlarm] = s.asAlarm
            p[K.vibrate] = s.vibrate
            p[K.flash] = s.flash
            p[K.voiceEn] = s.voiceEn
            p[K.voicePl] = s.voicePl
            p[K.balls] = s.balls.coerceIn(1, 99)
            p[K.ballWeight] = s.ballWeight.coerceIn(50, 2000)
        }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) { instance ?: SettingsRepository(context).also { instance = it } }
    }
}
