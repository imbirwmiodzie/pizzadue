package com.vibepizzaiolo.server

import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.ChatMessage
import com.vibepizzaiolo.community.Comment
import com.vibepizzaiolo.community.Invite
import com.vibepizzaiolo.community.InviteStatus
import com.vibepizzaiolo.community.Oven
import com.vibepizzaiolo.community.PhotoUploaded
import com.vibepizzaiolo.community.Pizza
import com.vibepizzaiolo.community.PizzaDraft
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.Pizzaiolo
import com.vibepizzaiolo.community.Session
import com.vibepizzaiolo.community.Sniff
import com.vibepizzaiolo.community.StyleCard
import com.vibepizzaiolo.community.Suggestion
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ServerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val dbs = AtomicInteger()

    /** A photo as a phone would send it. */
    private fun jpeg(w: Int = 1600, h: Int = 1200): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = Color(206, 52, 34); fillOval(100, 100, w - 200, h - 200); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
    }

    private class World(val photoDir: File)

    private fun server(classifier: Classifier? = null, test: suspend ApplicationTestBuilder.(World) -> Unit) = testApplication {
        val db = Db.connect("jdbc:h2:mem:t${System.nanoTime()}_${dbs.incrementAndGet()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", null, null).apply { migrate() }
        val dir = Files.createTempDirectory("photos").toFile()
        var clock = 1_000_000L
        application { module(db, PhotoStore(dir), classifier) { clock += 1000; clock } }
        test(World(dir))
    }

    private suspend fun HttpClient.send(method: String, path: String, token: String? = null, body: String? = null): HttpResponse {
        val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            token?.let { header("Authorization", "Bearer $it") }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
        }
        return when (method) { "GET" -> get(path, build); "PUT" -> put(path, build); "DELETE" -> delete(path, build); else -> post(path, build) }
    }

    private suspend fun ApplicationTestBuilder.signUp(name: String): Session {
        val r = client.send("POST", "/auth/signup", body = """{"email":"$name@example.com","password":"margherita1","name":"$name"}""")
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(Session.serializer(), r.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.postPizza(token: String, type: PizzaType = PizzaType.MARGHERITA, style: PizzaStyle = PizzaStyle.NAPOLETANA, base: Base = Base.ROSSA, spice: Int = 1, temp: Int = 450, caption: String = ""): Pizza {
        val up = client.post("/photos") { header("Authorization", "Bearer $token"); contentType(ContentType.Image.JPEG); setBody(jpeg()) }
        assertEquals(HttpStatusCode.OK, up.status, up.bodyAsText())
        val photoId = json.decodeFromString(PhotoUploaded.serializer(), up.bodyAsText()).photoId
        val draft = PizzaDraft(type, style, base, spice, temp, Oven.WOOD, bakeSec = 75, hydration = 70, caption = caption, photoId = photoId)
        val r = client.send("POST", "/pizzas", token, json.encodeToString(PizzaDraft.serializer(), draft))
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(Pizza.serializer(), r.bodyAsText())
    }

    private suspend fun ApplicationTestBuilder.pizzas(path: String, token: String): List<Pizza> {
        val r = client.send("GET", path, token)
        assertEquals(HttpStatusCode.OK, r.status, r.bodyAsText())
        return json.decodeFromString(ListSerializer(Pizza.serializer()), r.bodyAsText())
    }

    @Test fun accountsSignUpLogInAndAreRefusedProperly() = server {
        assertEquals("ok", client.get("/health").bodyAsText())
        assertEquals(HttpStatusCode.Unauthorized, client.send("GET", "/feed").status)
        assertEquals(HttpStatusCode.Unauthorized, client.send("GET", "/feed", "made-up-token").status)

        val ania = signUp("ania")
        assertNull(ania.me.card)
        assertEquals(HttpStatusCode.Conflict, client.send("POST", "/auth/signup", body = """{"email":"ANIA@example.com","password":"margherita1","name":"Other"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/auth/signup", body = """{"email":"b@example.com","password":"short","name":"Bea"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/auth/signup", body = """{"email":"nope","password":"margherita1","name":"Bea"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/auth/signup", body = "not json").status)

        assertEquals(HttpStatusCode.Unauthorized, client.send("POST", "/auth/login", body = """{"email":"ania@example.com","password":"wrong-one"}""").status)
        val again = json.decodeFromString(Session.serializer(), client.send("POST", "/auth/login", body = """{"email":" Ania@Example.com","password":"margherita1"}""").bodyAsText())
        assertEquals(ania.me.id, again.me.id)

        val card = StyleCard(3, Base.BOTH, PizzaStyle.TEGLIA, 9999, Oven.HOME)
        val saved = json.decodeFromString(Pizzaiolo.serializer(), client.send("PUT", "/me/card", again.token, json.encodeToString(StyleCard.serializer(), card)).bodyAsText())
        assertEquals(card.copy(tempC = 500), saved.card)
        assertEquals(saved, json.decodeFromString(Pizzaiolo.serializer(), client.send("GET", "/me", ania.token).bodyAsText()))

        client.send("POST", "/auth/logout", again.token)
        assertEquals(HttpStatusCode.Unauthorized, client.send("GET", "/me", again.token).status)
        assertEquals(HttpStatusCode.OK, client.send("GET", "/me", ania.token).status)
    }

    @Test fun photosAreReencodedSmallerAndServed() = server { world ->
        val ania = signUp("ania")
        val p = postPizza(ania.token)
        assertTrue(p.photo.startsWith("/photos/") && p.photo.endsWith("/card.jpg"))
        val card = client.get(p.photo)
        assertEquals(HttpStatusCode.OK, card.status)
        assertTrue(card.headers["Cache-Control"]!!.contains("immutable"))
        val img = ImageIO.read(ByteArrayInputStream(card.readRawBytes()))
        assertEquals(1080 to 810, img.width to img.height)
        val thumb = ImageIO.read(ByteArrayInputStream(client.get(p.photo.replace("card", "thumb")).readRawBytes()))
        assertEquals(360, thumb.width)
        assertEquals(HttpStatusCode.NotFound, client.get(p.photo.replace("card.jpg", "secret.txt")).status)
        assertEquals(HttpStatusCode.NotFound, client.get("/photos/..%2F..%2Fetc/card.jpg").status)

        val notAPhoto = client.post("/photos") { header("Authorization", "Bearer ${ania.token}"); setBody("hello".toByteArray()) }
        assertEquals(HttpStatusCode.BadRequest, notAPhoto.status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/photos") { setBody(jpeg()) }.status)

        // someone else's upload cannot be used for my pizza
        val bea = signUp("bea")
        val photoId = p.photo.split("/")[2]
        val stolen = PizzaDraft(PizzaType.MARINARA, PizzaStyle.ROMANA, Base.ROSSA, 0, 300, Oven.GAS, photoId = photoId)
        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/pizzas", bea.token, json.encodeToString(PizzaDraft.serializer(), stolen)).status)
        assertEquals(1, world.photoDir.list()!!.size)
    }

    @Test fun feedSwipesAndSearch() = server {
        val ania = signUp("ania"); val bea = signUp("bea"); val cyryl = signUp("cyryl")
        val marg = postPizza(ania.token, caption = "San Marzano")
        val diav = postPizza(ania.token, PizzaType.DIAVOLA, spice = 3, temp = 470)
        val white = postPizza(bea.token, PizzaType.QUATTRO_FORMAGGI, PizzaStyle.ROMANA, Base.BIANCA, 0, 320)

        assertEquals(listOf(white.id), pizzas("/feed", ania.token).map { it.id })
        assertEquals(listOf(diav.id, marg.id), pizzas("/feed", bea.token).map { it.id })
        assertEquals(listOf(diav.id, marg.id), pizzas("/pizzas/mine", ania.token).map { it.id })

        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/pizzas/${marg.id}/swipe", ania.token, """{"like":true}""").status)
        assertEquals(HttpStatusCode.NoContent, client.send("POST", "/pizzas/${marg.id}/swipe", bea.token, """{"like":true}""").status)
        client.send("POST", "/pizzas/${marg.id}/swipe", cyryl.token, """{"like":true}""")
        client.send("POST", "/pizzas/${diav.id}/swipe", cyryl.token, """{"like":true}""")
        client.send("POST", "/pizzas/${diav.id}/swipe", cyryl.token, """{"like":false}""") // changed their mind: one swipe each
        assertEquals(listOf(diav.id), pizzas("/feed", bea.token).map { it.id })
        val seen = json.decodeFromString(Pizza.serializer(), client.send("GET", "/pizzas/${marg.id}", bea.token).bodyAsText())
        assertEquals(Triple(2, 0, true), Triple(seen.likes, seen.passes, seen.mySwipe))
        assertFalse(seen.mine)
        assertEquals(0 to 1, pizzas("/pizzas?type=DIAVOLA", bea.token).single().let { it.likes to it.passes })

        assertEquals(listOf(white.id), pizzas("/pizzas?base=BIANCA", ania.token).map { it.id })
        assertEquals(listOf(white.id), pizzas("/pizzas?style=ROMANA", ania.token).map { it.id })
        assertEquals(listOf(diav.id), pizzas("/pizzas?minSpice=3", bea.token).map { it.id })
        assertEquals(listOf(diav.id, marg.id), pizzas("/pizzas?minTemp=440", bea.token).map { it.id })
        assertEquals(listOf(white.id), pizzas("/pizzas?maxTemp=400", bea.token).map { it.id })
        assertEquals(listOf(marg.id), pizzas("/pizzas?q=san%20marz", bea.token).map { it.id })
        assertEquals(listOf(white.id), pizzas("/pizzas?q=formaggi", ania.token).map { it.id })
        assertEquals(listOf(white.id), pizzas("/pizzas?q=bea", ania.token).map { it.id })
        assertEquals(listOf(marg.id, white.id, diav.id), pizzas("/pizzas?sort=BEST", bea.token).map { it.id })
        assertEquals(HttpStatusCode.BadRequest, client.send("GET", "/pizzas?type=PINEAPPLE", bea.token).status)
        assertTrue(pizzas("/pizzas?q=%25", bea.token).isEmpty())
    }

    @Test fun commentsInvitationsAndChat() = server {
        val ania = signUp("ania"); val bea = signUp("bea"); val cyryl = signUp("cyryl")
        val pizza = postPizza(ania.token)

        client.send("POST", "/pizzas/${pizza.id}/comments", bea.token, """{"text":"  Che bella!  "}""")
        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/pizzas/${pizza.id}/comments", bea.token, """{"text":"   "}""").status)
        val comments = json.decodeFromString(ListSerializer(Comment.serializer()), client.send("GET", "/pizzas/${pizza.id}/comments", ania.token).bodyAsText())
        assertEquals(listOf(Triple("bea", "Che bella!", false)), comments.map { Triple(it.author, it.text, it.mine) })
        assertEquals(1, pizzas("/pizzas/mine", ania.token).single().comments)

        assertEquals(HttpStatusCode.BadRequest, client.send("POST", "/pizzas/${pizza.id}/invite", ania.token).status)
        val sent = json.decodeFromString(Invite.serializer(), client.send("POST", "/pizzas/${pizza.id}/invite", bea.token).bodyAsText())
        assertEquals(Triple(InviteStatus.PENDING, true, "ania"), Triple(sent.status, sent.outgoing, sent.other.name))
        assertEquals(sent.id, json.decodeFromString(Invite.serializer(), client.send("POST", "/pizzas/${pizza.id}/invite", bea.token).bodyAsText()).id)
        assertTrue(pizzas("/pizzas", bea.token).single().invited)

        // not before yes, not by the asker, not by a stranger
        assertEquals(HttpStatusCode.Forbidden, client.send("POST", "/invites/${sent.id}/messages", bea.token, """{"text":"hi"}""").status)
        assertEquals(HttpStatusCode.Forbidden, client.send("POST", "/invites/${sent.id}/answer", bea.token, """{"accept":true}""").status)
        assertEquals(HttpStatusCode.NotFound, client.send("POST", "/invites/${sent.id}/answer", cyryl.token, """{"accept":true}""").status)
        assertEquals(HttpStatusCode.NotFound, client.send("GET", "/invites/${sent.id}/messages", cyryl.token).status)

        val incoming = json.decodeFromString(ListSerializer(Invite.serializer()), client.send("GET", "/invites", ania.token).bodyAsText()).single()
        assertEquals(Triple(false, "bea", PizzaType.MARGHERITA), Triple(incoming.outgoing, incoming.other.name, incoming.pizzaType))
        val yes = json.decodeFromString(Invite.serializer(), client.send("POST", "/invites/${sent.id}/answer", ania.token, """{"accept":true}""").bodyAsText())
        assertEquals(InviteStatus.ACCEPTED, yes.status)

        client.send("POST", "/invites/${sent.id}/messages", bea.token, """{"text":"Saturday?"}""")
        client.send("POST", "/invites/${sent.id}/messages", ania.token, """{"text":"At mine, 19:00."}""")
        val chat = json.decodeFromString(ListSerializer(ChatMessage.serializer()), client.send("GET", "/invites/${sent.id}/messages", bea.token).bodyAsText())
        assertEquals(listOf("Saturday?" to true, "At mine, 19:00." to false), chat.map { it.text to it.mine })
        assertEquals("At mine, 19:00.", json.decodeFromString(ListSerializer(Invite.serializer()), client.send("GET", "/invites", bea.token).bodyAsText()).single().lastMessage)
    }

    @Test fun blockingReportingAndDeletingTheAccount() = server { world ->
        val ania = signUp("ania"); val bea = signUp("bea")
        val pizza = postPizza(ania.token)
        val beas = postPizza(bea.token, PizzaType.MARINARA)
        val invite = json.decodeFromString(Invite.serializer(), client.send("POST", "/pizzas/${pizza.id}/invite", bea.token).bodyAsText())
        client.send("POST", "/invites/${invite.id}/answer", ania.token, """{"accept":true}""")

        // blocked: neither sees the other's pizzas, the invitation or new messages
        assertEquals(HttpStatusCode.NoContent, client.send("POST", "/pizzaioli/${bea.me.id}/block", ania.token).status)
        assertTrue(pizzas("/feed", ania.token).isEmpty())
        assertTrue(pizzas("/pizzas", bea.token).none { it.id == pizza.id })
        assertEquals("[]", client.send("GET", "/invites", ania.token).bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, client.send("POST", "/invites/${invite.id}/messages", bea.token, """{"text":"hello?"}""").status)

        // reported: gone for the reporter at once, for everyone after three people
        val reporters = listOf(signUp("r1"), signUp("r2"), signUp("r3"))
        client.send("POST", "/pizzas/${beas.id}/report", reporters[0].token, """{"text":"not a pizza"}""")
        assertTrue(pizzas("/pizzas", reporters[0].token).none { it.id == beas.id })
        assertTrue(pizzas("/pizzas", reporters[1].token).any { it.id == beas.id })
        client.send("POST", "/pizzas/${beas.id}/report", reporters[1].token, """{"text":"x"}""")
        client.send("POST", "/pizzas/${beas.id}/report", reporters[2].token, """{"text":"x"}""")
        assertTrue(pizzas("/pizzas/mine", bea.token).isEmpty())

        // deleting the account takes the pizzas and the photo files with it
        assertEquals(2, world.photoDir.list()!!.size)
        assertEquals(HttpStatusCode.NoContent, client.send("DELETE", "/me", ania.token).status)
        assertEquals(HttpStatusCode.Unauthorized, client.send("GET", "/me", ania.token).status)
        assertEquals(1, world.photoDir.list()!!.size)
        assertEquals(HttpStatusCode.NotFound, client.get(pizza.photo).status)
        assertTrue(pizzas("/pizzas", reporters[1].token).none { it.id == pizza.id })
        assertEquals(HttpStatusCode.Unauthorized, client.send("POST", "/auth/login", body = """{"email":"ania@example.com","password":"margherita1"}""").status)
    }

    @Test fun sniffingAsksTheClassifierOrSaysThereIsNone() {
        server { _ ->
            val ania = signUp("ania")
            val r = client.post("/photos/sniff") { header("Authorization", "Bearer ${ania.token}"); setBody(jpeg(400, 300)) }
            assertEquals(HttpStatusCode.NotImplemented, r.status)
        }
        val fixed = Sniff(listOf(Suggestion(PizzaType.DIAVOLA, 80)), Base.ROSSA, source = "vision")
        server(classifier = { fixed }) { _ ->
            val ania = signUp("ania")
            val r = client.post("/photos/sniff") { header("Authorization", "Bearer ${ania.token}"); setBody(jpeg(400, 300)) }
            assertEquals(fixed, json.decodeFromString(Sniff.serializer(), r.bodyAsText()))
            assertEquals(HttpStatusCode.Unauthorized, client.post("/photos/sniff") { setBody(jpeg(400, 300)) }.status)
        }
    }

    @Test fun loginIsRateLimited() = server {
        val codes = (1..12).map { client.send("POST", "/auth/login", body = """{"email":"x@example.com","password":"guess-$it"}""").status }
        assertEquals(HttpStatusCode.Unauthorized, codes.first())
        assertEquals(HttpStatusCode.TooManyRequests, codes.last())
    }

    @Test fun theModelsAnswerBecomesASuggestion() {
        // building the request also checks that the answer's shape is one the API accepts
        assertNotNull(ClaudeClassifier("claude-opus-5-5") { error("not called") }.request(jpeg(200, 200)))

        val answer = SniffAnswer().apply {
            base = "rossa"
            guesses = listOf(
                SniffAnswer.Guess().apply { type = "MARGHERITA"; confidence = 30 },
                SniffAnswer.Guess().apply { type = "diavola"; confidence = 60 },
                SniffAnswer.Guess().apply { type = "HAWAII"; confidence = 5 },
            )
        }
        val sniff = ClaudeClassifier.toSniff(answer)
        assertEquals(listOf(PizzaType.DIAVOLA to 60, PizzaType.MARGHERITA to 30), sniff.suggestions.map { it.type to it.confidence })
        assertEquals(Base.ROSSA, sniff.base)
        assertEquals("vision", sniff.source)
        assertTrue(ClaudeClassifier.toSniff(SniffAnswer().apply { isPizza = false; guesses = answer.guesses }).let { !it.isPizza && it.suggestions.isEmpty() })
    }
}
