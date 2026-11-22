package com.vibepizzaiolo

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.vibepizzaiolo.dough.FermentViewModel
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.PizzaTheme
import com.vibepizzaiolo.ui.ferment.DoughSettingsFromPlan
import com.vibepizzaiolo.ui.ferment.FermentWizard
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The fermentation wizard on the Dough tab, step by step: a preset, the dough, the schedule,
 * the yeast worked out backwards and the result with its chart. Each step is also saved as a
 * picture under app/build/screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FermentWizardScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val shots = File("build/screens").apply { mkdirs() }

    private fun shows(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String) {
        rule.waitForIdle()
        runCatching {
            val window = rule.activity.window
            val view = window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            var result = -1
            android.view.PixelCopy.request(window, bmp, { result = it }, android.os.Handler(android.os.Looper.getMainLooper()))
            val end = System.currentTimeMillis() + 5000
            while (result == -1 && System.currentTimeMillis() < end) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("SHOT $name failed: $it") }
    }

    /** Scrolls the wizard's page by [dy] pixels. */
    private fun scroll(dy: Float) {
        val page = SemanticsMatcher("the scrolling page") { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
        rule.onAllNodes(page)[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        rule.waitForIdle()
    }

    @Test fun fromAPresetToTheChart() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("ferment", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = FermentViewModel(app)
        vm.openWizard()
        var saved: DoughSettingsFromPlan? = null
        rule.setContent {
            PizzaTheme { Surface(Modifier.fillMaxSize(), color = Pal.bg) { FermentWizard(vm) { saved = it } } }
        }

        assertTrue(shows("Od czego zaczynamy?"))
        shot("ferment-1-style")

        rule.onNodeWithText("48 h w lodówce").performClick()
        rule.waitForIdle()
        assertEquals(1, vm.step.value)
        assertEquals(320, vm.plan.value.w)
        shot("ferment-2-dough")

        rule.onNodeWithText("Dalej ›").performClick()
        rule.waitForIdle()
        assertTrue(shows("Harmonogram"))
        assertEquals(3, vm.plan.value.stages.size)
        // one more hour in the fridge
        rule.onNodeWithContentDescription("Więcej: etap 2, czas").performClick()
        rule.waitForIdle()
        assertEquals(46.0, vm.plan.value.stages[1].hours, 1e-9)
        shot("ferment-3-schedule")

        rule.onNodeWithText("Dalej ›").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Policz za mnie").performClick()
        rule.waitForIdle()
        assertTrue(vm.plan.value.autoYeast)
        assertTrue(shows("POTRZEBA"))
        // bake 2 hours later: the fridge stage gets longer
        rule.onNodeWithContentDescription("Więcej: Chcę piec za").performClick()
        rule.onNodeWithContentDescription("Więcej: Chcę piec za").performClick()
        rule.waitForIdle()
        assertEquals(51.0, vm.plan.value.totalHours, 1e-9)
        assertEquals(48.0, vm.plan.value.stages[1].hours, 1e-9)
        assertTrue(shows("Przy tych ustawieniach: gotowe na koniec"))
        shot("ferment-4-yeast")

        rule.onNodeWithText("Symuluj ›").performClick()
        rule.waitForIdle()
        assertTrue(shows("Gotowe do pieczenia"))
        rule.onNodeWithContentDescription("Wykres fermentacji").assertExists()
        shot("ferment-5-result")
        scroll(1400f)
        shot("ferment-6-ingredients")
        rule.onNodeWithText("pH a rozciągliwość").performClick()
        rule.waitForIdle()
        assertTrue(shows("przyjmują protony"))
        scroll(900f)
        shot("ferment-7-chemistry")

        rule.onNodeWithText("Zapisz w Dough").performClick()
        rule.waitForIdle()
        val s = saved!!
        assertEquals(4, s.balls)
        assertEquals(65f, s.hydration, 1e-6f)
        assertTrue("about 250 g a ball, was ${s.ballWeight}", s.ballWeight in 240..260)
    }

    @Test fun anOverFermentedDoughSaysSoAndOffersLessYeast() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("ferment", Context.MODE_PRIVATE).edit().clear().commit()
        val vm = FermentViewModel(app)
        vm.update { it.copy(w = 220, yeastPct = 1.0, autoYeast = false, stages = listOf(com.vibepizzaiolo.dough.Stage(24.0, 24.0))) }
        vm.openWizard()
        vm.goTo(4)
        rule.setContent {
            PizzaTheme { Surface(Modifier.fillMaxSize(), color = Pal.bg) { FermentWizard(vm) {} } }
        }
        rule.waitForIdle()
        assertTrue(shows("Przefermentowane"))
        shot("ferment-8-over")
        // the advice sets the yeast that would make it ready at the end
        rule.onNodeWithText("Żeby było gotowe na koniec", substring = true).performClick()
        rule.waitForIdle()
        assertTrue("yeast was ${vm.plan.value.yeastPct}", vm.plan.value.yeastPct < 0.1)
    }
}
