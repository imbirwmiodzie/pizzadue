package com.vibepizzaiolo

import android.app.Application
import android.content.Context
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.vibepizzaiolo.party.GuestOrder
import com.vibepizzaiolo.party.PartyClient
import com.vibepizzaiolo.party.PartyStage
import com.vibepizzaiolo.party.PartyViewModel
import com.vibepizzaiolo.settings.Settings
import com.vibepizzaiolo.ui.Pal
import com.vibepizzaiolo.ui.PizzaTheme
import com.vibepizzaiolo.ui.party.NextUpBanner
import com.vibepizzaiolo.ui.party.PartyScreen
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Serata tab from the pizzaiolo's side, against a real running server (COMMUNITY_TEST_URL,
 * as for RemotePartyTest; skipped without it): a new night, friends ordering from their
 * browsers, the bake and its queue. Each step is also saved as a picture under app/build/screens.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartyScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val url = System.getenv("COMMUNITY_TEST_URL").orEmpty()
    private val shots = File("build/screens").apply { mkdirs() }

    /** Lets the app run until [done]; frames are advanced by hand, like in CommunityScreenTest. */
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

    /**
     * Scrolls the page until [text] is on screen. performScrollTo cannot be used here: with frames
     * advanced by hand the page is not laid out again after its scroll, so it would scroll forever.
     */
    private fun reach(text: String): SemanticsNodeInteraction {
        val page = SemanticsMatcher("the scrolling page") { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
        val viewport = rule.onAllNodes(page)[0].fetchSemanticsNode().boundsInRoot
        val node = rule.onNodeWithText(text).fetchSemanticsNode()
        val bottom = node.positionInRoot.y + node.size.height
        val dy = when {
            bottom > viewport.bottom -> bottom - viewport.bottom + 40f
            node.positionInRoot.y < viewport.top -> node.positionInRoot.y - viewport.top - 40f
            else -> 0f
        }
        if (dy != 0f) {
            rule.onAllNodes(page)[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
            settle(400)
        }
        return rule.onNodeWithText(text)
    }

    private fun shot(name: String) {
        settle(350)
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

    @Test fun fromTheInvitationToTheLastPizza() {
        assumeTrue("COMMUNITY_TEST_URL is not set", url.isNotEmpty())
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("community", Context.MODE_PRIVATE).edit().putString("server_url", url).commit()
        val vm = PartyViewModel(app)
        var timerOpened = false
        rule.setContent {
            PizzaTheme { Surface(Modifier.fillMaxSize(), color = Pal.bg) { PartyScreen(vm, Settings()) { timerOpened = true } } }
        }
        fun ui() = vm.ui.value
        last = { "toast: ${org.robolectric.shadows.ShadowToast.getTextOfLatestToast()}\n" + ui().toString().take(800) }
        rule.mainClock.autoAdvance = false

        // ---- a new night ----
        await("welcome") { shows("New pizza night") }
        shot("party-1-welcome")
        rule.onNodeWithText("New pizza night").performClick()
        await("form") { ui().form != null && shows("Your name") }
        rule.onNodeWithText("Your name").performTextInput("Marek")
        rule.onNodeWithText("When (e.g. Saturday 19:00)").performTextInput("Saturday 19:00")
        reach("+ Diavola").performClick()
        reach("+ Wine").performClick()
        reach("+ Mozzarella").performClick()
        await("menu of three") { ui().form!!.menu.size == 3 && ui().form!!.bring.size == 2 }
        shot("party-2-form")
        reach("Create and get the link").performClick()
        await("created") { ui().party != null && ui().form == null }
        val code = ui().party!!.code
        assertEquals("$url/p/$code", ui().link)
        assertEquals(listOf("Margherita", "Marinara", "Diavola"), ui().party!!.menu.map { it.name })

        // ---- friends order from their browsers; the board follows ----
        val friends = PartyClient(url)
        val (margherita, _, diavola) = ui().party!!.menu.map { it.id }
        runBlocking {
            val ania = friends.join(code, "Ania")
            friends.order(code, ania.key, ania.guestId, listOf(GuestOrder(margherita, 2)))
            friends.bring(code, ania.key, ui().party!!.bring.first().id, ania.guestId)
            val ola = friends.join(code, "Ola")
            friends.order(code, ola.key, ola.guestId, listOf(GuestOrder(diavola, 1)))
        }
        rule.runOnUiThread { vm.refresh() }
        await("orders arrive") { ui().party!!.ordered == 3 && shows("Margherita ×2") }

        // the pizzaiolo eats too
        reach("Marek · you").performClick()
        await("own order dialog") { shows("Your pizza") }
        rule.onNodeWithContentDescription("More Diavola").performClick()
        await("own order saved") { ui().party!!.ordered == 4 }
        rule.onNodeWithText("Done").performClick()
        await("dialog closed") { ui().orderFor == null && !shows("Your pizza") }
        assertTrue(shows("5 balls of 250 g"))
        shot("party-3-orders")
        rule.onNodeWithText("WHO BRINGS WHAT").assertExists()
        reach("Close orders · start the bake")
        shot("party-4-orders-bottom")

        // ---- the bake: the test pizza first, then one each ----
        rule.onNodeWithText("Close orders · start the bake").performClick()
        await("baking") { ui().party!!.stage == PartyStage.BAKING && shows("NEXT INTO THE OVEN") }
        assertEquals("Test pizza", ui().party!!.next()!!.pizzaName)
        shot("party-5-baking")
        rule.onNodeWithText("Out of the oven ✓").performClick()
        await("test pizza out") { ui().party!!.next()!!.guestName == "Marek" }
        rule.runOnUiThread { vm.bakedNext() } // what the timer does when a bake ends
        await("Marek's out") { ui().party!!.next()!!.guestName == "Ania" }
        shot("party-6-queue")
        reach("Go to the timer").performClick()
        assertTrue(timerOpened)

        // ---- the end ----
        reach("Finish the night").performClick()
        await("done") { ui().party!!.stage == PartyStage.DONE && shows("Grazie!") }
        shot("party-7-done")

        // a fresh start of the app finds the night again
        val again = PartyViewModel(app)
        rule.runOnUiThread { again.start() }
        await("found again") { again.ui.value.party?.code == code }

        rule.runOnUiThread { vm.deleteNight() }
        await("deleted") { ui().party == null && shows("New pizza night") }
    }

    @Test fun theTimerSaysWhoseAPizzaIsNext() {
        val party = com.vibepizzaiolo.party.Party(
            code = "c", title = "Night", stage = PartyStage.BAKING, spare = 1, spareBaked = 1,
            menu = listOf(com.vibepizzaiolo.party.MenuPizza("d", "Diavola")),
            guests = listOf(com.vibepizzaiolo.party.PartyGuest("a", "Ania", orders = listOf(GuestOrder("d", 1)))),
        )
        var opened = false
        rule.setContent { PizzaTheme { Surface(color = Pal.bg) { NextUpBanner(party) { opened = true } } } }
        rule.onNodeWithText("Diavola · Ania").assertExists()
        rule.onNodeWithText("1/2").assertExists()
        rule.onNodeWithText("Diavola · Ania").performClick()
        assertTrue(opened)
    }
}
