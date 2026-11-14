package com.pizzadue.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    val maxVolume: Boolean = true,
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
        val maxVolume = booleanPreferencesKey("max_volume")
        val vibrate = booleanPreferencesKey("vibrate_v2")
        val flash = booleanPreferencesKey("flash")
        val voiceEn = booleanPreferencesKey("voice_en")
        val voicePl = booleanPreferencesKey("voice_pl")
        val balls = intPreferencesKey("balls")
        val ballWeight = intPreferencesKey("ball_weight")
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
            maxVolume = p[K.maxVolume] ?: d.maxVolume,
            vibrate = p[K.vibrate] ?: d.vibrate,
            flash = p[K.flash] ?: d.flash,
            voiceEn = p[K.voiceEn] ?: d.voiceEn,
            voicePl = p[K.voicePl] ?: d.voicePl,
            balls = p[K.balls] ?: d.balls,
            ballWeight = p[K.ballWeight] ?: d.ballWeight,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val d = Settings()
            val cur = Settings(
                p[K.hydration] ?: d.hydration, p[K.salt] ?: d.salt, p[K.yeast] ?: d.yeast,
                p[K.bakeSec] ?: d.bakeSec, p[K.preheatSec] ?: d.preheatSec,
                p[K.ticks] ?: d.ticks, p[K.pronta] ?: d.pronta, p[K.forno] ?: d.forno, p[K.gira] ?: d.gira,
                p[K.bright] ?: d.bright, p[K.maxVolume] ?: d.maxVolume, p[K.vibrate] ?: d.vibrate, p[K.flash] ?: d.flash,
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
            p[K.maxVolume] = s.maxVolume
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
