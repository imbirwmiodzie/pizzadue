package com.vibepizzaiolo

import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.CommunityException
import com.vibepizzaiolo.community.Filter
import com.vibepizzaiolo.community.InviteStatus
import com.vibepizzaiolo.community.Oven
import com.vibepizzaiolo.community.PhotoData
import com.vibepizzaiolo.community.PizzaDraft
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.RemoteCommunityRepository
import com.vibepizzaiolo.community.ServerUnreachable
import com.vibepizzaiolo.community.StyleCard
import com.vibepizzaiolo.community.TokenStore
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The app's server client against a real running server: set COMMUNITY_TEST_URL
 * (CI starts one; locally `./gradlew -p server run` and http://localhost:8080). Skipped otherwise.
 */
class RemoteCommunityTest {
    private val url = System.getenv("COMMUNITY_TEST_URL").orEmpty()

    private class Memory : TokenStore { override var token: String? = null }

    private fun client() = RemoteCommunityRepository(url, Memory())

    /** A red pizza: the JPEG from the test resources, and matching pixels for the colour guess. */
    private fun photo(): PhotoData {
        val jpeg = javaClass.getResourceAsStream("/test-pizza.jpg")!!.use { it.readBytes() }
        val px = IntArray(80 * 60) { i ->
            val dx = i % 80 - 40; val dy = i / 80 - 30
            if (dx * dx + dy * dy < 21 * 21) 0xFFCE3422.toInt() else 0xFF2E241E.toInt()
        }
        return PhotoData(jpeg, px, 80, 60)
    }

    @Test fun twoPhonesMeetOnTheServer() = runBlocking {
        assumeTrue("COMMUNITY_TEST_URL is not set", url.isNotEmpty())
        val run = System.nanoTime().toString(36)
        val ania = client(); val bea = client()
        assertTrue(ania.healthy())
        assertNull(ania.me())

        val a = ania.signUp("ania-$run@example.com", "margherita1", "Ania")
        val b = bea.signUp("bea-$run@example.com", "margherita1", "Bea")
        assertNull(a.card)
        assertEquals(PizzaStyle.NAPOLETANA, ania.saveCard(StyleCard(2, Base.ROSSA, PizzaStyle.NAPOLETANA, 450, Oven.WOOD)).card!!.style)
        bea.saveCard(StyleCard(0, Base.BIANCA, PizzaStyle.ROMANA, 320, Oven.ELECTRIC))
        assertEquals("Ania", ania.me()!!.name)
        try { client().signUp("ania-$run@example.com", "margherita1", "Again"); fail() } catch (e: CommunityException) { assertTrue(e.message!!.contains("already")) }

        // no model on the test server: the colours on the phone decide, and say so
        val sniff = ania.sniff(photo())
        assertEquals("colours", sniff.source)
        assertEquals(Base.ROSSA, sniff.base)

        val pizza = ania.post(photo(), PizzaDraft(PizzaType.MARINARA, PizzaStyle.NAPOLETANA, Base.ROSSA, 2, 450, Oven.WOOD, 80, 68, "run $run"))
        assertTrue(pizza.mine && pizza.photo.startsWith(url))
        assertEquals(200, (URL(pizza.photo).openConnection() as java.net.HttpURLConnection).responseCode)
        assertEquals(200, (URL(pizza.photo.replace("card.jpg", "thumb.jpg")).openConnection() as java.net.HttpURLConnection).responseCode)
        assertTrue(ania.mine().any { it.id == pizza.id })

        val seen = bea.feed().first { it.id == pizza.id }
        assertFalse(seen.mine)
        assertEquals("Ania", seen.author.name)
        bea.swipe(pizza.id, true)
        assertTrue(bea.feed().none { it.id == pizza.id })
        assertEquals(1, bea.pizza(pizza.id).likes)
        assertEquals(listOf(pizza.id), bea.search(Filter(type = PizzaType.MARINARA, query = "run $run")).map { it.id })
        assertTrue(bea.search(Filter(base = Base.BIANCA, query = "run $run")).isEmpty())

        bea.comment(pizza.id, "Che bella!")
        assertEquals(listOf("Bea" to false), ania.comments(pizza.id).map { it.author to it.mine })

        val sent = bea.invite(pizza.id)
        assertEquals(InviteStatus.PENDING, sent.status)
        val incoming = ania.invites().first { it.id == sent.id }
        assertFalse(incoming.outgoing)
        assertEquals(InviteStatus.ACCEPTED, ania.answer(incoming.id, true).status)
        bea.send(sent.id, "Saturday?")
        ania.send(sent.id, "Sì!")
        assertEquals(listOf("Saturday?" to false, "Sì!" to true), ania.messages(sent.id).map { it.text to it.mine })

        ania.logOut()
        assertNull(ania.me())
        assertEquals(a.id, ania.logIn("ania-$run@example.com", "margherita1").id)
        ania.deleteAccount()
        try { bea.pizza(pizza.id); fail() } catch (e: CommunityException) { assertTrue(e.message!!.contains("gone")) }
        bea.deleteAccount()
        assertEquals(b.id, b.id)
    }

    @Test fun aSilentServerIsToldApartFromARefusal() = runBlocking {
        val nobody = RemoteCommunityRepository("http://127.0.0.1:9", Memory().apply { token = "x" })
        assertFalse(nobody.healthy())
        try { nobody.feed(); fail() } catch (e: ServerUnreachable) { }
        // sniffing still answers, from the colours
        assertEquals("colours", nobody.sniff(photo()).source)
    }
}
