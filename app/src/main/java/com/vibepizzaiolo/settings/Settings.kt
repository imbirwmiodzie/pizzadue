package com.vibepizzaiolo.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    // ---- the reel and the cards: you and your numbers ----
    /** Your name or @handle, printed on the reel's ending and cover. Empty: none. */
    val handle: String = "",
    /** Oven temperature in °C, shown on the reel. 0: not shown. */
    val ovenC: Int = 0,
    /** Dough fermentation in hours, shown on the reel. 0: not shown. */
    val fermentH: Int = 0,
    /** A series like "Pizza nº 47 / 100". 0: no series. */
    val seriesGoal: Int = 0,
    /** What the quality seal says, or no seal at all. */
    val seal: SealText = SealText.QUALITA,
    /** A small "made with VibePizzaiolo" at the very end of the reel. */
    val appTag: Boolean = true,
    /** The look of the reel; the last one chosen. */
    val reelStyle: Int = 0,
) {
    companion object {
        val HYDRATION = 50f..90f
        val SALT = 0f..5f
        val YEAST = 0f..3f
        val BAKE_SEC = 15..30 * 60
        val PREHEAT_SEC = 5 * 60..90 * 60
        val OVEN_C = 0..550
        val FERMENT_H = 0..120
        val SERIES = 0..1000
        const val HANDLE = 30
    }
}

/** The words in the middle of the quality seal; [NONE] leaves the seal off. */
enum class SealText(val big: String, val small: String, val label: String) {
    QUALITA("QUALITÀ", "SUPERIORE", "Qualità superiore"),
    CASA("FATTA", "IN CASA", "Fatta in casa"),
    AMORE("CON", "AMORE", "Con amore"),
    NONE("", "", "No seal"),
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
        val handle = stringPreferencesKey("handle")
        val ovenC = intPreferencesKey("oven_c")
        val fermentH = intPreferencesKey("ferment_h")
        val seriesGoal = intPreferencesKey("series_goal")
        val seal = intPreferencesKey("seal")
        val appTag = booleanPreferencesKey("app_tag")
        val reelStyle = intPreferencesKey("reel_style")
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

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
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
            handle = p[K.handle] ?: d.handle,
            ovenC = p[K.ovenC] ?: d.ovenC,
            fermentH = p[K.fermentH] ?: d.fermentH,
            seriesGoal = p[K.seriesGoal] ?: d.seriesGoal,
            seal = SealText.entries.getOrNull(p[K.seal] ?: -1) ?: d.seal,
            appTag = p[K.appTag] ?: d.appTag,
            reelStyle = p[K.reelStyle] ?: d.reelStyle,
        )
    }

    val flow: Flow<Settings> = store.data.map { read(it) }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val s = transform(read(p))
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
            p[K.handle] = s.handle.trim().take(Settings.HANDLE)
            p[K.ovenC] = s.ovenC.coerceIn(Settings.OVEN_C)
            p[K.fermentH] = s.fermentH.coerceIn(Settings.FERMENT_H)
            p[K.seriesGoal] = s.seriesGoal.coerceIn(Settings.SERIES)
            p[K.seal] = s.seal.ordinal
            p[K.appTag] = s.appTag
            p[K.reelStyle] = s.reelStyle.coerceAtLeast(0)
        }
    }

    companion object {
        @Volatile private var instance: SettingsRepository? = null
        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) { instance ?: SettingsRepository(context).also { instance = it } }
    }
}
