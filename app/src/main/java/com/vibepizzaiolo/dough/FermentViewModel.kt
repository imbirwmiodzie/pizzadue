package com.vibepizzaiolo.dough

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The fermentation wizard on the Dough tab: which step is open and the plan, kept between visits. */
class FermentViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("ferment", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _plan = MutableStateFlow(load())
    val plan: StateFlow<FermentPlan> = _plan.asStateFlow()

    private val _step = MutableStateFlow(0)
    val step: StateFlow<Int> = _step.asStateFlow()

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    fun openWizard() { _step.value = 0; _open.value = true }
    fun close() { _open.value = false }
    fun next() { _step.value = (_step.value + 1).coerceAtMost(LAST_STEP) }
    fun back() { if (_step.value == 0) close() else _step.value -= 1 }
    fun goTo(step: Int) { _step.value = step.coerceIn(0, LAST_STEP) }

    fun update(transform: (FermentPlan) -> FermentPlan) {
        _plan.value = sanitize(transform(_plan.value))
        runCatching { prefs.edit().putString(KEY, json.encodeToString(_plan.value)).apply() }
    }

    fun applyPreset(p: FermentPresets.Preset) {
        update { p.plan }
        next()
    }

    private fun load(): FermentPlan =
        prefs.getString(KEY, null)?.let { runCatching { sanitize(json.decodeFromString<FermentPlan>(it)) }.getOrNull() } ?: FermentPlan()

    companion object {
        const val LAST_STEP = 4
        private const val KEY = "plan"
        const val MAX_STAGES = 8
        const val MAX_HOURS = 168.0

        fun sanitize(p: FermentPlan): FermentPlan {
            val stages = p.stages.take(MAX_STAGES).map {
                Stage(hours = (Math.round(it.hours * 4) / 4.0).coerceIn(0.25, 120.0), tempC = Math.round(it.tempC).toDouble().coerceIn(0.0, 60.0))
            }.ifEmpty { listOf(Stage(8.0, 22.0)) }
            return p.copy(
                flourG = p.flourG.coerceIn(100, 10_000),
                balls = p.balls.coerceIn(1, 60),
                w = p.w.coerceIn(150, 400),
                hydration = p.hydration.coerceIn(55.0, 80.0),
                salt = p.salt.coerceIn(0.0, 3.5),
                sugar = p.sugar.coerceIn(0.0, 10.0),
                oil = p.oil.coerceIn(0.0, 10.0),
                vinegarTsp = p.vinegarTsp.coerceIn(0.0, 4.0),
                yeastPct = p.yeastPct.coerceIn(YEAST_MIN * p.yeastType.perInstant, YEAST_MAX * p.yeastType.perInstant),
                doughTempC = p.doughTempC.coerceIn(4.0, 35.0),
                stages = stages,
            )
        }

        /** The amounts the yeast slider offers, % of flour as instant yeast. */
        const val YEAST_MIN = 0.01
        const val YEAST_MAX = 2.5
    }
}
