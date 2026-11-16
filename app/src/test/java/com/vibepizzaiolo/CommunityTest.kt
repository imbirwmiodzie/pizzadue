package com.vibepizzaiolo

import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.ColorSniffer
import com.vibepizzaiolo.community.CommunityException
import com.vibepizzaiolo.community.Filter
import com.vibepizzaiolo.community.InviteStatus
import com.vibepizzaiolo.community.LocalCommunityRepository
import com.vibepizzaiolo.community.Oven
import com.vibepizzaiolo.community.PhotoData
import com.vibepizzaiolo.community.PizzaDraft
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.Sort
import com.vibepizzaiolo.community.StyleCard
import com.vibepizzaiolo.community.inCommonWith
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The community as it runs with no server: everything on the phone, with the sample pizzaioli. */
class CommunityTest {
    private lateinit var dir: File
    private lateinit var repo: LocalCommunityRepository
    private val photo = PhotoData(byteArrayOf(1, 2, 3), IntArray(4), 2, 2)
    private val draft = PizzaDraft(PizzaType.DIAVOLA, PizzaStyle.NAPOLETANA, Base.ROSSA, spice = 3, tempC = 450, oven = Oven.WOOD, caption = " hot ")

    @Before fun setUp() {
        dir = Files.createTempDirectory("community").toFile()
        repo = LocalCommunityRepository(dir)
    }

    private fun refuses(block: suspend () -> Unit): String = runBlocking {
        try { block(); fail("should have been refused"); "" } catch (e: CommunityException) { e.message.orEmpty() }
    }

    private suspend fun signedUp() = repo.signUp("Ania@Example.com ", "margherita1", "Ania").also { repo.saveCard(StyleCard(2, Base.ROSSA, PizzaStyle.NAPOLETANA, 450, Oven.WOOD)) }

    @Test fun accountIsCreatedCheckedAndSurvivesARestart() = runBlocking {
        assertNull(repo.me())
        assertTrue(refuses { repo.signUp("not-an-email", "margherita1", "Ania") }.contains("email"))
        assertTrue(refuses { repo.signUp("a@b.pl", "short", "Ania") }.contains("8"))
        val me = signedUp()
        assertEquals("Ania", me.name)

        val again = LocalCommunityRepository(dir) // the app was closed and opened
        assertEquals(PizzaStyle.NAPOLETANA, again.me()!!.card!!.style)
        again.logOut()
        assertNull(again.me())
        assertTrue(refuses { again.logIn("ania@example.com", "wrong-password") }.contains("Wrong"))
        assertEquals("Ania", again.logIn("ANIA@example.com", "margherita1").name)
        assertFalse("the password itself is never stored", File(dir, "community.json").readText().contains("margherita1"))
    }

    @Test fun nothingWorksLoggedOut() {
        assertTrue(refuses { repo.feed() }.contains("Log in"))
        assertTrue(refuses { repo.post(photo, draft) }.contains("Log in"))
    }

    @Test fun swipingTakesAPizzaOffTheDeckAndCountsIt() = runBlocking {
        signedUp()
        val deck = repo.feed()
        assertTrue(deck.size >= 10)
        assertTrue(deck.none { it.mine })
        val first = deck.first()
        repo.swipe(first.id, like = true)
        assertTrue(repo.feed().none { it.id == first.id })
        val after = repo.pizza(first.id)
        assertEquals(first.likes + 1, after.likes)
        assertEquals(true, after.mySwipe)
    }

    @Test fun postedPizzaIsMineSearchableAndNotInMyOwnDeck() = runBlocking {
        signedUp()
        val p = repo.post(photo, draft)
        assertTrue(p.mine)
        assertEquals("hot", p.caption)
        assertTrue(File(p.photo.removePrefix("file:")).exists())
        assertEquals(listOf(p.id), repo.mine().map { it.id })
        assertTrue(repo.feed().none { it.id == p.id })
        assertTrue(refuses { repo.swipe(p.id, true) }.contains("own"))
        assertEquals(p.id, repo.search(Filter(type = PizzaType.DIAVOLA)).first().id) // newest first
    }

    @Test fun searchByTypeStyleSauceHeatAndWords() = runBlocking {
        signedUp()
        assertTrue(repo.search(Filter(type = PizzaType.MARGHERITA)).let { r -> r.isNotEmpty() && r.all { it.type == PizzaType.MARGHERITA } })
        assertTrue(repo.search(Filter(base = Base.BIANCA)).all { it.base == Base.BIANCA })
        assertTrue(repo.search(Filter(style = PizzaStyle.TEGLIA)).all { it.style == PizzaStyle.TEGLIA })
        assertTrue(repo.search(Filter(minSpice = 3)).all { it.spice == 3 })
        assertTrue(repo.search(Filter(minTemp = 440)).all { it.tempC >= 440 })
        assertTrue(repo.search(Filter(query = "gennaro")).all { it.author.name == "Gennaro" })
        assertTrue(repo.search(Filter(query = "quattro")).all { it.type == PizzaType.QUATTRO_FORMAGGI })
        val best = repo.search(Filter(sort = Sort.BEST)).map { it.likePercent ?: 50 }
        assertEquals(best.sortedDescending(), best)
    }

    @Test fun commentsAreKeptAndChecked() = runBlocking {
        signedUp()
        val p = repo.feed().first()
        val before = repo.comments(p.id).size
        repo.comment(p.id, "  Che bella!  ")
        val all = repo.comments(p.id)
        assertEquals(before + 1, all.size)
        assertTrue(all.last().let { it.mine && it.text == "Che bella!" && it.author == "Ania" })
        assertEquals(before + 1, repo.pizza(p.id).comments)
        assertTrue(refuses { repo.comment(p.id, "   ") }.contains("Write"))
    }

    @Test fun eatingTogetherOpensAChatWithTheSamplePizzaiolo() = runBlocking {
        signedUp()
        val p = repo.feed().first()
        val inv = repo.invite(p.id)
        assertEquals(InviteStatus.ACCEPTED, inv.status)
        assertTrue(inv.outgoing && inv.other.demo)
        assertEquals("asking twice is the same invitation", inv.id, repo.invite(p.id).id)
        assertTrue(repo.pizza(p.id).invited)
        assertEquals(1, repo.messages(inv.id).size)
        repo.send(inv.id, "Saturday?")
        repo.send(inv.id, "At mine.")
        val chat = repo.messages(inv.id)
        assertEquals(listOf(false, true, false, true), chat.map { it.mine })
        assertTrue("the sample says once that it is a sample", chat[2].text.contains("sample"))
        assertEquals(1, repo.invites().size)
    }

    @Test fun blockingAndReportingHide() = runBlocking {
        signedUp()
        val p = repo.feed().first()
        repo.block(p.author.id)
        assertTrue(repo.search(Filter()).none { it.author.id == p.author.id })
        val q = repo.feed().first()
        repo.report(q.id, "not a pizza")
        assertTrue(repo.search(Filter()).none { it.id == q.id })
    }

    @Test fun deletingTheAccountRemovesEverything() = runBlocking {
        signedUp()
        val p = repo.post(photo, draft)
        repo.deleteAccount()
        assertNull(repo.me())
        assertFalse(File(p.photo.removePrefix("file:")).exists())
        assertNotNull(repo.signUp("new@example.com", "margherita2", "Nuovo"))
        assertTrue(repo.mine().isEmpty())
    }

    // ---- the colour guess ----

    /** A round pizza of [sauce] with blobs of other colours on it. */
    private fun picture(sauce: Int, vararg toppings: Pair<Int, Int>): IntArray {
        val n = 100
        val px = IntArray(n * n) { 0xFF2E241E.toInt() }
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x - 50; val dy = y - 50
            if (dx * dx + dy * dy < 45 * 45) px[y * n + x] = sauce
        }
        var k = 0
        for ((color, count) in toppings) repeat(count) {
            val cx = 22 + (k * 37) % 56; val cy = 22 + (k * 53) % 56; k++
            for (y in cy - 4..cy + 4) for (x in cx - 4..cx + 4) px[y * n + x] = color
        }
        return px
    }

    private val tomato = 0xFFCE3422.toInt(); private val cream = 0xFFF6ECD4.toInt(); private val mozzarella = 0xFFFAF6EC.toInt()
    private val basil = 0xFF348E46.toInt(); private val salami = 0xFF8C221E.toInt()

    @Test fun coloursTellRedFromWhiteAndProposeAType() {
        val margherita = ColorSniffer.sniff(picture(tomato, mozzarella to 14, basil to 4), 100, 100)
        assertEquals(Base.ROSSA, margherita.base)
        assertEquals(PizzaType.MARGHERITA, margherita.suggestions.first().type)

        val diavola = ColorSniffer.sniff(picture(tomato, salami to 16, mozzarella to 4), 100, 100)
        assertEquals(PizzaType.DIAVOLA, diavola.suggestions.first().type)

        val marinara = ColorSniffer.sniff(picture(tomato), 100, 100)
        assertEquals(PizzaType.MARINARA, marinara.suggestions.first().type)

        val white = ColorSniffer.sniff(picture(cream, mozzarella to 8), 100, 100)
        assertEquals(Base.BIANCA, white.base)
        assertTrue(white.suggestions.first().type.base == Base.BIANCA)

        for (s in listOf(margherita, diavola, marinara, white)) {
            assertEquals(3, s.suggestions.size)
            assertTrue(s.suggestions.zipWithNext().all { (a, b) -> a.confidence >= b.confidence })
        }
    }

    @Test fun whatTwoPizzaioliShare() {
        val a = StyleCard(2, Base.ROSSA, PizzaStyle.NAPOLETANA, 450, Oven.WOOD)
        val same = a.inCommonWith(a.copy(tempC = 470))
        assertTrue(same.any { it.contains("Napoletana") } && same.any { it.contains("°C") })
        assertTrue(a.inCommonWith(StyleCard(0, Base.BIANCA, PizzaStyle.DETROIT, 260, Oven.HOME)).isEmpty())
    }
}
