package com.vibepizzaiolo

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.vibepizzaiolo.community.CommunityViewModel
import com.vibepizzaiolo.community.Filter
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.Section
import com.vibepizzaiolo.community.Stage
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.PizzaTheme
import com.vibepizzaiolo.ui.community.CommunityScreen
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
 * The whole Community tab with no server: create an account, answer the style quiz, swipe,
 * search the gallery, open a pizza, comment, ask to eat together, chat. Each step is also
 * saved as a picture under app/build/screens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CommunityScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val shots = File("build/screens").apply { mkdirs() }

    /**
     * Lets the app run until [done]: frames are advanced by hand (the screens have animations that
     * never end) and the main thread's queue is drained so work coming back from other threads lands.
     */
    private fun await(what: String, timeoutMs: Long = 10_000, done: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            rule.mainClock.advanceTimeByFrame()
            if (runCatching(done).getOrDefault(false)) return
            if (System.currentTimeMillis() > end) throw AssertionError("never happened: $what\n${last()}")
            Thread.sleep(5)
        }
    }

    private var last: () -> String = { "" }
    private fun shows(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun settle(ms: Long) { val end = System.currentTimeMillis() + ms; await("settle") { System.currentTimeMillis() >= end } }

    private fun shot(name: String) {
        settle(350) // let the slide or fade finish
        runCatching {
            val window = rule.activity.window
            val view = window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            var result = -1
            android.view.PixelCopy.request(window, bmp, { result = it }, android.os.Handler(android.os.Looper.getMainLooper()))
            await("screenshot $name", 5000) { result != -1 }
            File(shots, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.onFailure { println("SHOT $name failed: $it") }
    }

    @Test fun fromSignUpToEatingTogether() {
        val vm = CommunityViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { PizzaTheme { Surface(Modifier.fillMaxSize(), color = Pal.bg) { CommunityScreen(vm, avatar = null) } } }
        fun ui() = vm.ui.value
        last = { ui().toString().take(600) }
        rule.mainClock.autoAdvance = false

        // ---- account ----
        await("state") { ui().stage == Stage.AUTH }
        assertTrue(ui().offline)
        await("Pizzaiolo name") { shows("Pizzaiolo name") }
        shot("1-welcome")
        rule.onNodeWithText("Pizzaiolo name").performTextInput("Ania")
        rule.onNodeWithText("Email").performTextInput("ania@example.com")
        rule.onNodeWithText("Password (8 characters or more)").performTextInput("margherita1")
        rule.onAllNodesWithText("Create account")[1].performScrollTo().performClick()
        await("quiz") { ui().stage == Stage.QUIZ }

        // ---- the style quiz: four questions ----
        await("How spicy are you?") { shows("How spicy are you?") }
        shot("2-quiz-spice")
        rule.onNodeWithText("Next").performClick()
        await("Red or white?") { shows("Red or white?") }
        settle(500)
        rule.onNodeWithText("Bianca").performClick()
        shot("3-quiz-base")
        rule.onNodeWithText("Next").performClick()
        await("Which pizza is yours?") { shows("Which pizza is yours?") }
        shot("4-quiz-style")
        rule.onNodeWithText("Next").performClick()
        await("How hot do you bake?") { shows("How hot do you bake?") }
        shot("5-quiz-heat")
        rule.onNodeWithText("That's me").performClick()
        await("state") { ui().stage == Stage.HOME && ui().deck.isNotEmpty() }
        assertEquals(com.vibepizzaiolo.community.Base.BIANCA, ui().me!!.card!!.base)

        // ---- swipe ----
        await("by Gennaro · sample") { shows("by Gennaro · sample") }
        settle(400); shot("6-deck")
        val before = ui().deck.size
        val first = ui().deck.first()
        rule.onNodeWithText("by Gennaro · sample").performTouchInput { swipeRight() }
        await("state") { ui().deck.size == before - 1 }
        rule.onNodeWithContentDescription("Pass").performClick()
        await("state") { ui().deck.size == before - 2 }
        shot("7-deck-after")

        // ---- gallery and search by type ----
        rule.onNodeWithText("Gallery").performClick()
        await("state") { ui().section == Section.GALLERY && ui().results.size > 10 && !ui().searching }
        settle(700); shot("8-gallery")
        rule.runOnUiThread { vm.setFilter(Filter(type = PizzaType.DIAVOLA)) } // the chip is off-screen in its scrolling row
        await("state") { !ui().searching && ui().results.isNotEmpty() && ui().results.all { it.type == PizzaType.DIAVOLA } }
        settle(700); shot("9-gallery-diavola")
        rule.onNodeWithText("All types").performClick()
        await("state") { !ui().searching && ui().results.size > 10 }

        // ---- one pizza: comment, then "Mangiamo insieme?" ----
        rule.runOnUiThread { vm.open(first) }
        await("state") { ui().detail?.loading == false }
        await("Comments") { shows("Comments") }
        assertEquals(true, ui().detail!!.pizza.mySwipe)
        settle(500); shot("10-detail")
        rule.onNodeWithText("Say something about this pizza").performTextInput("Che cornicione!")
        rule.onNodeWithContentDescription("Send comment").performClick()
        await("state") { ui().detail!!.comments.any { it.mine && it.text == "Che cornicione!" } }
        rule.onNodeWithText("🍕 Mangiamo insieme?").performScrollTo().performClick()
        await("state") { ui().chat != null && ui().chat!!.messages.isNotEmpty() }
        await("When and where?") { shows("When and where?") }
        rule.onNodeWithText("When and where?").performTextInput("Saturday at mine?")
        rule.onNodeWithContentDescription("Send").performClick()
        await("state") { ui().chat!!.messages.size == 3 }
        settle(300); shot("11-chat")

        // ---- back out: chat, then the pizza, then the table and the profile ----
        rule.runOnUiThread { vm.back() }
        rule.runOnUiThread { vm.back() }
        await("state") { ui().chat == null && ui().detail == null }
        rule.onNodeWithText("Tavola").performClick()
        await("state") { ui().invites.size == 1 }
        settle(300); shot("12-tavola")
        rule.onNodeWithText("Me").performClick()
        await("MY STYLE") { shows("MY STYLE") }
        shot("13-me")
    }
}
