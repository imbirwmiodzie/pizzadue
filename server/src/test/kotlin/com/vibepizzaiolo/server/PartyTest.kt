package com.vibepizzaiolo.server

import com.vibepizzaiolo.party.BakedRequest
import com.vibepizzaiolo.party.BringItem
import com.vibepizzaiolo.party.BringRequest
import com.vibepizzaiolo.party.ClaimRequest
import com.vibepizzaiolo.party.GuestOrder
import com.vibepizzaiolo.party.JoinRequest
import com.vibepizzaiolo.party.Joined
import com.vibepizzaiolo.party.MenuPizza
import com.vibepizzaiolo.party.OrderRequest
import com.vibepizzaiolo.party.Party
import com.vibepizzaiolo.party.PartyCreated
import com.vibepizzaiolo.party.PartyDraft
import com.vibepizzaiolo.party.PartyStage
import com.vibepizzaiolo.party.PartyView
import com.vibepizzaiolo.party.StageRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class PartyTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val dbs = AtomicInteger()

    private fun server(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val db = Db.connect("jdbc:h2:mem:p${System.nanoTime()}_${dbs.incrementAndGet()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", null, null).apply { migrate() }
        var clock = 1_000_000L
        application { module(db, PhotoStore(Files.createTempDirectory("photos").toFile()), null) { clock += 1000; clock } }
        test()
    }

    private suspend fun <B> ApplicationTestBuilder.send(method: String, path: String, key: String? = null, s: KSerializer<B>? = null, body: B? = null): HttpResponse {
        val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            key?.let { header("Authorization", "Bearer $it") }
            if (s != null && body != null) { contentType(ContentType.Application.Json); setBody(json.encodeToString(s, body)) }
        }
        return when (method) { "GET" -> client.get(path, build); "PUT" -> client.put(path, build); "DELETE" -> client.delete(path, build); else -> client.post(path, build) }
    }

    private suspend fun ApplicationTestBuilder.send(method: String, path: String, key: String? = null) = send<Unit>(method, path, key)

    private suspend fun HttpResponse.party(): Party {
        assertEquals(HttpStatusCode.OK, status, bodyAsText())
        return json.decodeFromString(Party.serializer(), bodyAsText())
    }

    private val draft = PartyDraft(
        title = "  Saturday pizza  ", hostName = "Marek", whenText = "Saturday 19:00", note = "Garden if it is dry",
        spare = 1,
        menu = listOf(MenuPizza(name = "Margherita", note = "the classic"), MenuPizza(name = "Diavola"), MenuPizza(name = "margherita")),
        bring = listOf(BringItem(name = "Wine"), BringItem(name = "Mozzarella")),
    )

    private suspend fun ApplicationTestBuilder.create(d: PartyDraft = draft): PartyCreated {
        val r = send("POST", "/parties", null, PartyDraft.serializer(), d)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(PartyCreated.serializer(), r.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.join(code: String, name: String): Joined {
        val r = send("POST", "/parties/$code/join", null, JoinRequest.serializer(), JoinRequest(name))
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(Joined.serializer(), r.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.order(code: String, key: String?, guestId: String, vararg orders: Pair<String, Int>) =
        send("PUT", "/parties/$code/guests/$guestId/order", key, OrderRequest.serializer(), OrderRequest(orders.map { GuestOrder(it.first, it.second) }))

    private suspend fun ApplicationTestBuilder.view(code: String, key: String? = null): PartyView {
        val r = send("GET", "/parties/$code", key)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(PartyView.serializer(), r.bodyAsText())
    }

    @Test fun aNightIsCreatedTidiedAndSeenThroughItsLink() = server {
        val made = create()
        val p = made.party
        assertEquals(PartyService.CODE_LENGTH, p.code.length)
        assertEquals("Saturday pizza", p.title)
        assertEquals(listOf("Margherita", "Diavola"), p.menu.map { it.name }, "the doubled pizza is dropped")
        assertEquals(listOf("Wine", "Mozzarella"), p.bring.map { it.name })
        assertEquals(PartyStage.ORDERS, p.stage)
        assertEquals("Marek", p.hostName)
        assertEquals(made.hostGuestId, p.guests.single().id)
        assertEquals(1, p.balls, "nobody ordered yet: only the spare ball")

        // the link alone shows the night but not who is asking; the host key makes you the pizzaiolo
        val anyone = view(p.code)
        assertNull(anyone.me); assertFalse(anyone.host)
        val host = view(p.code, made.hostKey)
        assertTrue(host.host); assertEquals(made.hostGuestId, host.me)
        assertFalse(send("GET", "/parties/${p.code}").bodyAsText().contains(made.hostKey), "keys never come back")

        assertEquals(HttpStatusCode.NotFound, send("GET", "/parties/nosuchnight").status)
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/parties", null, PartyDraft.serializer(), draft.copy(menu = emptyList())).status)
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/parties", null, PartyDraft.serializer(), draft.copy(hostName = " ")).status)
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/parties", null, PartyDraft.serializer(), draft.copy(title = "")).status)
    }

    @Test fun guestsJoinOrderAndTheBallsAreCounted() = server {
        val made = create()
        val code = made.party.code
        val (margherita, diavola) = made.party.menu.map { it.id }

        val ania = join(code, "  Ania ")
        assertEquals("Ania", ania.party.guests.last().name)
        assertEquals(ania.guestId, view(code, ania.key).me)
        assertEquals(HttpStatusCode.Conflict, send("POST", "/parties/$code/join", null, JoinRequest.serializer(), JoinRequest("ania")).status, "one Ania per night")
        assertEquals(HttpStatusCode.BadRequest, send("POST", "/parties/$code/join", null, JoinRequest.serializer(), JoinRequest("  ")).status)
        val ola = join(code, "Ola")

        var p = order(code, ania.key, ania.guestId, diavola to 1, margherita to 2).party()
        assertEquals(listOf(GuestOrder(margherita, 2), GuestOrder(diavola, 1)), p.guests.first { it.id == ania.guestId }.orders, "in menu order")
        p = order(code, ola.key, ola.guestId, diavola to 1).party()
        assertEquals(4, p.ordered)
        assertEquals(5, p.balls, "one ball per pizza and the spare one")
        assertEquals(2, p.count(diavola))

        // an order replaces the one before; zero takes a pizza off
        p = order(code, ania.key, ania.guestId, margherita to 1, diavola to 0).party()
        assertEquals(listOf(GuestOrder(margherita, 1)), p.guests.first { it.id == ania.guestId }.orders)

        // not somebody else's order, not without a key, not off the menu, not a mountain
        assertEquals(HttpStatusCode.Forbidden, order(code, ola.key, ania.guestId, diavola to 1).status)
        assertEquals(HttpStatusCode.Forbidden, order(code, null, ania.guestId, diavola to 1).status)
        assertEquals(HttpStatusCode.Forbidden, order(code, "made-up", ania.guestId, diavola to 1).status)
        assertEquals(HttpStatusCode.BadRequest, order(code, ania.key, ania.guestId, "not-a-pizza" to 1).status)
        assertEquals(HttpStatusCode.BadRequest, order(code, ania.key, ania.guestId, diavola to 7).status)

        // the pizzaiolo eats too, and writes in a guest without a phone
        p = order(code, made.hostKey, made.hostGuestId, diavola to 1).party()
        assertEquals(1, p.guests.first { it.host }.pizzas)
        assertEquals(HttpStatusCode.Forbidden, send("POST", "/parties/$code/guests", ania.key, JoinRequest.serializer(), JoinRequest("Babcia")).status)
        p = send("POST", "/parties/$code/guests", made.hostKey, JoinRequest.serializer(), JoinRequest("Babcia")).party()
        val babcia = p.guests.first { it.name == "Babcia" }
        p = order(code, made.hostKey, babcia.id, margherita to 1).party()
        assertEquals(4, p.ordered)
    }

    @Test fun aBrowserThatForgotTapsItsName() = server {
        val made = create()
        val code = made.party.code
        val ania = join(code, "Ania")
        val again = send("POST", "/parties/$code/claim", null, ClaimRequest.serializer(), ClaimRequest(ania.guestId))
        assertEquals(HttpStatusCode.OK, again.status, again.bodyAsText())
        val fresh = json.decodeFromString(Joined.serializer(), again.bodyAsText())
        assertEquals(ania.guestId, fresh.guestId)
        assertNotEquals(ania.key, fresh.key)
        assertEquals(ania.guestId, view(code, fresh.key).me)
        assertNull(view(code, ania.key).me, "the earlier browser is asked again who it is")

        // nobody becomes the pizzaiolo by tapping a name
        assertEquals(HttpStatusCode.Forbidden, send("POST", "/parties/$code/claim", null, ClaimRequest.serializer(), ClaimRequest(made.hostGuestId)).status)
        assertEquals(HttpStatusCode.NotFound, send("POST", "/parties/$code/claim", null, ClaimRequest.serializer(), ClaimRequest("nobody")).status)
    }

    @Test fun whoBringsWhat() = server {
        val made = create()
        val code = made.party.code
        val (wine, mozzarella) = made.party.bring.map { it.id }
        val ania = join(code, "Ania")
        val ola = join(code, "Ola")
        fun bring(item: String) = "/parties/$code/bring/$item"

        var p = send("PUT", bring(wine), ania.key, BringRequest.serializer(), BringRequest(ania.guestId)).party()
        assertEquals(ania.guestId, p.bring.first { it.id == wine }.guestId)
        // taken is taken; and nobody volunteers somebody else
        assertEquals(HttpStatusCode.Conflict, send("PUT", bring(wine), ola.key, BringRequest.serializer(), BringRequest(ola.guestId)).status)
        assertEquals(HttpStatusCode.Conflict, send("PUT", bring(wine), ola.key, BringRequest.serializer(), BringRequest(null)).status)
        assertEquals(HttpStatusCode.Forbidden, send("PUT", bring(mozzarella), ola.key, BringRequest.serializer(), BringRequest(ania.guestId)).status)
        assertEquals(HttpStatusCode.Forbidden, send("PUT", bring(mozzarella), null, BringRequest.serializer(), BringRequest(ola.guestId)).status)
        // Ania changes her mind, Ola takes it
        send("PUT", bring(wine), ania.key, BringRequest.serializer(), BringRequest(null)).party()
        p = send("PUT", bring(wine), ola.key, BringRequest.serializer(), BringRequest(ola.guestId)).party()
        assertEquals(ola.guestId, p.bring.first { it.id == wine }.guestId)
        // the pizzaiolo hands out the rest
        p = send("PUT", bring(mozzarella), made.hostKey, BringRequest.serializer(), BringRequest(ania.guestId)).party()
        assertEquals(ania.guestId, p.bring.first { it.id == mozzarella }.guestId)

        // Ania cannot come: her mozzarella is free again
        assertEquals(HttpStatusCode.Forbidden, send("DELETE", "/parties/$code/guests/${ania.guestId}", ola.key).status)
        p = send("DELETE", "/parties/$code/guests/${ania.guestId}", made.hostKey).party()
        assertTrue(p.guests.none { it.id == ania.guestId })
        assertNull(p.bring.first { it.id == mozzarella }.guestId)
        assertEquals(HttpStatusCode.BadRequest, send("DELETE", "/parties/$code/guests/${made.hostGuestId}", made.hostKey).status)
    }

    @Test fun editingTheMenuKeepsOrdersOfThePizzasThatStay() = server {
        val made = create()
        val code = made.party.code
        val (margherita, diavola) = made.party.menu
        val ania = join(code, "Ania")
        order(code, ania.key, ania.guestId, margherita.id to 1, diavola.id to 1).party()
        send("PUT", "/parties/$code/bring/${made.party.bring[0].id}", ania.key, BringRequest.serializer(), BringRequest(ania.guestId)).party()

        val edit = PartyDraft(
            title = "Sunday pizza", whenText = "Sunday", spare = 2,
            menu = listOf(MenuPizza(name = "Bianca"), diavola.copy(name = "Diavola piccante")),
            bring = listOf(made.party.bring[0], BringItem(name = "Beer")),
        )
        assertEquals(HttpStatusCode.Forbidden, send("PUT", "/parties/$code", ania.key, PartyDraft.serializer(), edit).status)
        assertEquals(HttpStatusCode.Forbidden, send("PUT", "/parties/$code", null, PartyDraft.serializer(), edit).status)
        val p = send("PUT", "/parties/$code", made.hostKey, PartyDraft.serializer(), edit).party()
        assertEquals("Sunday pizza", p.title)
        assertEquals(listOf("Bianca", "Diavola piccante"), p.menu.map { it.name })
        assertEquals(diavola.id, p.menu[1].id)
        assertEquals(listOf(GuestOrder(diavola.id, 1)), p.guests.first { it.id == ania.guestId }.orders, "the Margherita went with its pizza")
        assertEquals(listOf("Wine", "Beer"), p.bring.map { it.name })
        assertEquals(ania.guestId, p.bring[0].guestId, "Ania still brings the wine")
        assertEquals(3, p.balls)
        assertEquals("Marek", p.hostName)
    }

    @Test fun theBakeClosesOrdersAndWalksTheQueue() = server {
        val made = create()
        val code = made.party.code
        val (margherita, diavola) = made.party.menu.map { it.id }
        val ania = join(code, "Ania")
        val ola = join(code, "Ola")
        order(code, ania.key, ania.guestId, margherita to 2).party()
        order(code, ola.key, ola.guestId, diavola to 1).party()

        assertEquals(HttpStatusCode.Forbidden, send("POST", "/parties/$code/stage", ania.key, StageRequest.serializer(), StageRequest(PartyStage.BAKING)).status)
        var p = send("POST", "/parties/$code/stage", made.hostKey, StageRequest.serializer(), StageRequest(PartyStage.BAKING)).party()
        assertEquals(PartyStage.BAKING, p.stage)

        // the test pizza first, then one each, then seconds
        assertEquals(listOf("everyone/Test pizza", "Ania/Margherita", "Ola/Diavola", "Ania/Margherita"), p.queue().map { "${it.guestName}/${it.pizzaName}" })
        assertEquals("Test pizza", p.next()!!.pizzaName)

        // phones are away: no new orders, no late guests, no changes to the list
        assertEquals(HttpStatusCode.Forbidden, order(code, ania.key, ania.guestId, diavola to 1).status)
        assertEquals(HttpStatusCode.Forbidden, send("POST", "/parties/$code/join", null, JoinRequest.serializer(), JoinRequest("Late")).status)
        assertEquals(HttpStatusCode.Forbidden, send("PUT", "/parties/$code/bring/${p.bring[0].id}", ania.key, BringRequest.serializer(), BringRequest(ania.guestId)).status)
        // ... but the pizzaiolo can still fix an order
        p = order(code, made.hostKey, ola.guestId, diavola to 1, margherita to 1).party()
        assertEquals(4, p.ordered)

        fun baked(guest: String?, pizza: String?, n: Int) = BakedRequest(guest, pizza, n)
        assertEquals(HttpStatusCode.Forbidden, send("POST", "/parties/$code/baked", ania.key, BakedRequest.serializer(), baked(null, null, 1)).status)
        p = send("POST", "/parties/$code/baked", made.hostKey, BakedRequest.serializer(), baked(null, null, 1)).party()
        assertEquals("Ania", p.next()!!.guestName)
        p = send("POST", "/parties/$code/baked", made.hostKey, BakedRequest.serializer(), baked(ania.guestId, margherita, 1)).party()
        assertEquals("Ola", p.next()!!.guestName)
        assertEquals(listOf(true, true, false, false, false), p.queue().map { it.done })
        // never more baked than ordered; and a tick can be taken back
        p = send("POST", "/parties/$code/baked", made.hostKey, BakedRequest.serializer(), baked(ania.guestId, margherita, 9)).party()
        assertEquals(2, p.guests.first { it.id == ania.guestId }.orders.single().baked)
        p = send("POST", "/parties/$code/baked", made.hostKey, BakedRequest.serializer(), baked(ania.guestId, margherita, 1)).party()
        assertEquals(1, p.guests.first { it.id == ania.guestId }.orders.single().baked)
        assertEquals(HttpStatusCode.NotFound, send("POST", "/parties/$code/baked", made.hostKey, BakedRequest.serializer(), baked(ania.guestId, diavola, 1)).status)
        // shrinking an order keeps the baked count within it
        p = order(code, made.hostKey, ania.guestId, margherita to 1).party()
        assertEquals(GuestOrder(margherita, 1, 1), p.guests.first { it.id == ania.guestId }.orders.single())

        p = send("POST", "/parties/$code/stage", made.hostKey, StageRequest.serializer(), StageRequest(PartyStage.DONE)).party()
        assertEquals(PartyStage.DONE, p.stage)

        assertEquals(HttpStatusCode.Forbidden, send("DELETE", "/parties/$code", ania.key).status)
        assertEquals(HttpStatusCode.NoContent, send("DELETE", "/parties/$code", made.hostKey).status)
        assertEquals(HttpStatusCode.NotFound, send("GET", "/parties/$code").status)
    }

    @Test fun theGuestPageIsServedLockedDown() = server {
        val page = client.get("/p/anything")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("/party/app.js"))
        assertEquals("default-src 'self'; frame-ancestors 'none'", page.headers["Content-Security-Policy"])
        assertTrue(page.headers["Content-Type"]!!.startsWith("text/html"))
        for (file in listOf("app.js", "pizza.js", "app.css", "icon.svg")) assertEquals(HttpStatusCode.OK, client.get("/party/$file").status, file)
        assertEquals(HttpStatusCode.NotFound, client.get("/party/../logback.xml").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/party/index.html").status)
    }
}
