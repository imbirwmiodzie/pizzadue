package com.vibepizzaiolo

import com.vibepizzaiolo.party.BakedRequest
import com.vibepizzaiolo.party.BringItem
import com.vibepizzaiolo.party.GuestOrder
import com.vibepizzaiolo.party.MenuPizza
import com.vibepizzaiolo.party.PartyClient
import com.vibepizzaiolo.party.PartyDraft
import com.vibepizzaiolo.party.PartyException
import com.vibepizzaiolo.party.PartyStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The app's pizza-night client against a real running server: set COMMUNITY_TEST_URL
 * (CI starts one; locally `./gradlew -p server run` and http://localhost:8080). Skipped otherwise.
 */
class RemotePartyTest {
    private val url = System.getenv("COMMUNITY_TEST_URL").orEmpty()

    @Test fun aWholeNightFromThePizzaiolosPhone() = runBlocking {
        assumeTrue("COMMUNITY_TEST_URL is not set", url.isNotEmpty())
        val client = PartyClient(url)
        val made = client.create(
            PartyDraft(
                title = "Client night", hostName = "Marek", whenText = "tonight",
                menu = listOf(MenuPizza(name = "Margherita"), MenuPizza(name = "Diavola")), bring = listOf(BringItem(name = "Wine")),
            )
        )
        val code = made.party.code
        val key = made.hostKey
        assertEquals("$url/p/$code", client.link(code))
        assertTrue(client.view(code, key).host)
        val (margherita, diavola) = made.party.menu.map { it.id }

        // a friend's browser joins; the pizzaiolo orders for himself and for a guest he writes in
        val ania = client.join(code, "Ania")
        var p = client.order(code, key, made.hostGuestId, listOf(GuestOrder(diavola, 1)))
        p = client.addGuest(code, key, "Babcia")
        val babcia = p.guests.first { it.name == "Babcia" }
        p = client.order(code, key, babcia.id, listOf(GuestOrder(margherita, 2)))
        assertEquals(3, p.ordered)
        assertEquals(4, p.balls)
        p = client.bring(code, key, p.bring.single().id, ania.guestId)
        assertEquals("Ania", p.guestName(p.bring.single().guestId))
        p = client.bring(code, key, p.bring.single().id, null)
        assertEquals(null, p.bring.single().guestId)

        p = client.update(code, key, PartyDraft(title = "Client night, later", spare = 0, menu = p.menu, bring = p.bring))
        assertEquals("Client night, later", p.title)
        assertEquals(3, p.balls)

        p = client.setStage(code, key, PartyStage.BAKING)
        assertEquals("Marek", p.next()!!.guestName)
        p = client.setBaked(code, key, BakedRequest(made.hostGuestId, diavola, 1))
        assertEquals("Babcia", p.next()!!.guestName)
        p = client.removeGuest(code, key, ania.guestId)
        assertEquals(2, p.guests.size)

        // what the server refuses arrives as a sentence, with its status
        try {
            client.setStage(code, "not-the-key", PartyStage.DONE)
            fail("a made-up key closed the night")
        } catch (e: PartyException) {
            assertEquals(403, e.status)
            assertEquals("Only the pizzaiolo can do that.", e.message)
        }
        client.delete(code, key)
        try {
            client.view(code, key)
            fail("the night is still there")
        } catch (e: PartyException) {
            assertEquals(404, e.status)
        }
    }
}
