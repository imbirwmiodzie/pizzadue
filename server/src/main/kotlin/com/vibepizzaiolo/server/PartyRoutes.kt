package com.vibepizzaiolo.server

import com.vibepizzaiolo.party.BakedRequest
import com.vibepizzaiolo.party.BringRequest
import com.vibepizzaiolo.party.ClaimRequest
import com.vibepizzaiolo.party.JoinRequest
import com.vibepizzaiolo.party.OrderRequest
import com.vibepizzaiolo.party.PartyDraft
import com.vibepizzaiolo.party.StageRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing

/** The guests' page: a few small files from the jar. The page itself is the same for every night. */
private object GuestPage {
    private fun load(name: String) = GuestPage::class.java.getResourceAsStream("/party/$name")!!.use { it.readBytes() }
    val html = load("index.html")
    val files = mapOf(
        "app.js" to (ContentType.Text.JavaScript to load("app.js")),
        "pizza.js" to (ContentType.Text.JavaScript to load("pizza.js")),
        "app.css" to (ContentType.Text.CSS to load("app.css")),
        "icon.svg" to (ContentType.Image.SVG to load("icon.svg")),
    ) + PIZZA_PHOTOS.associate { "pizza-$it.webp" to (ContentType("image", "webp") to load("pizza-$it.webp")) }
}

/** The photographed pizzas (scripts/pizza_photos), the same list as PHOTOS in pizza.js. */
val PIZZA_PHOTOS = listOf(
    "margherita", "marinara", "diavola", "capricciosa", "quattro-formaggi", "hawaii", "nutella",
    "tonno", "tonno-cipolla", "salsiccia", "salsiccia-funghi", "guanciale-cipolla", "rucola-grana",
)

private suspend fun ApplicationCall.page(type: ContentType, bytes: ByteArray, cache: String = "no-cache") {
    // nothing but this server's own files runs or loads on the page
    response.header("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'")
    response.header("X-Content-Type-Options", "nosniff")
    response.header("Referrer-Policy", "no-referrer")
    response.header(HttpHeaders.CacheControl, cache)
    respondBytes(bytes, if (type.contentType == "image" && type.contentSubtype == "webp") type else type.withParameter("charset", "utf-8"))
}

fun Application.partyRoutes(service: PartyService) {
    routing {
        // the link friends get: https://server/p/<code>
        get("/p/{code}") { call.page(ContentType.Text.Html, GuestPage.html) }
        get("/party/{file}") {
            val file = call.param("file")
            val (type, bytes) = GuestPage.files[file] ?: refuse(HttpStatusCode.NotFound, "No such file.")
            // a photo is kept a day, instead of asked for again on every redraw of the menu
            call.page(type, bytes, if (file.endsWith(".webp")) "public, max-age=86400" else "no-cache")
        }

        // starting a night needs no account, so it is slowed down per address like sign-up
        rateLimit(AUTH_LIMIT) {
            post("/parties") {
                val draft = call.receive<PartyDraft>()
                call.respond(io { service.create(draft) })
            }
        }

        rateLimit(PARTY_LIMIT) {
            get("/parties/{code}") { val code = call.param("code"); val key = call.token(); call.respond(io { service.view(code, key) }) }
            put("/parties/{code}") {
                val code = call.param("code"); val key = call.token()
                val draft = call.receive<PartyDraft>()
                call.respond(io { service.update(code, key, draft) })
            }
            delete("/parties/{code}") {
                val code = call.param("code"); val key = call.token()
                io { service.delete(code, key) }
                call.respond(HttpStatusCode.NoContent)
            }
            post("/parties/{code}/stage") {
                val code = call.param("code"); val key = call.token()
                val stage = call.receive<StageRequest>().stage
                call.respond(io { service.setStage(code, key, stage) })
            }
            post("/parties/{code}/baked") {
                val code = call.param("code"); val key = call.token()
                val r = call.receive<BakedRequest>()
                call.respond(io { service.setBaked(code, key, r) })
            }

            post("/parties/{code}/join") {
                val code = call.param("code")
                val name = call.receive<JoinRequest>().name
                call.respond(io { service.join(code, name) })
            }
            post("/parties/{code}/claim") {
                val code = call.param("code")
                val guestId = call.receive<ClaimRequest>().guestId
                call.respond(io { service.claim(code, guestId) })
            }
            post("/parties/{code}/guests") {
                val code = call.param("code"); val key = call.token()
                val name = call.receive<JoinRequest>().name
                call.respond(io { service.addGuest(code, key, name) })
            }
            delete("/parties/{code}/guests/{guest}") {
                val code = call.param("code"); val key = call.token(); val guest = call.param("guest")
                call.respond(io { service.removeGuest(code, key, guest) })
            }
            put("/parties/{code}/guests/{guest}/order") {
                val code = call.param("code"); val key = call.token(); val guest = call.param("guest")
                val orders = call.receive<OrderRequest>().orders
                call.respond(io { service.order(code, key, guest, orders) })
            }
            put("/parties/{code}/bring/{item}") {
                val code = call.param("code"); val key = call.token(); val item = call.param("item")
                val guestId = call.receive<BringRequest>().guestId
                call.respond(io { service.bring(code, key, item, guestId) })
            }
        }
    }
}
