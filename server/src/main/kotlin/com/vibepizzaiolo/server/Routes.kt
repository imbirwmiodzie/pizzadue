package com.vibepizzaiolo.server

import com.vibepizzaiolo.community.AnswerRequest
import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.Filter
import com.vibepizzaiolo.community.LogInRequest
import com.vibepizzaiolo.community.PhotoUploaded
import com.vibepizzaiolo.community.PizzaDraft
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.SignUpRequest
import com.vibepizzaiolo.community.Sort
import com.vibepizzaiolo.community.StyleCard
import com.vibepizzaiolo.community.SwipeRequest
import com.vibepizzaiolo.community.TextRequest
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray

private fun ApplicationCall.token(): String? = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }

/** Database and disk work happens off the request threads. */
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private fun ApplicationCall.param(name: String): String = parameters[name] ?: refuse(HttpStatusCode.BadRequest, "Missing $name.")

private inline fun <reified E : Enum<E>> ApplicationCall.enumQuery(name: String): E? =
    request.queryParameters[name]?.let { v -> enumValues<E>().firstOrNull { it.name == v } ?: refuse(HttpStatusCode.BadRequest, "Unknown $name.") }

/** The photo in the request body, refused before reading when it announces itself as too large. */
private suspend fun ApplicationCall.photoBytes(): ByteArray {
    if ((request.contentLength() ?: 0) > PhotoStore.MAX_BYTES) refuse(HttpStatusCode.PayloadTooLarge, "That photo is too large.")
    val bytes = receive<io.ktor.utils.io.ByteReadChannel>().readRemaining(PhotoStore.MAX_BYTES + 1L).readByteArray()
    if (bytes.size > PhotoStore.MAX_BYTES) refuse(HttpStatusCode.PayloadTooLarge, "That photo is too large.")
    return bytes
}

fun Application.routes(service: Service, photos: PhotoStore, classifier: Classifier?) {
    suspend fun ApplicationCall.user(): String =
        token()?.let { t -> io { service.userFor(t) } } ?: refuse(HttpStatusCode.Unauthorized, "Log in again.")

    routing {
        get("/health") { call.respondText("ok") }

        // photos are public to whoever knows the address, which is a random id; they never change, so phones keep them
        get("/photos/{id}/{file}") {
            val file = photos.file(call.param("id"), call.param("file")) ?: refuse(HttpStatusCode.NotFound, "No such photo.")
            call.response.header(HttpHeaders.CacheControl, CacheControl.MaxAge(31_536_000, visibility = CacheControl.Visibility.Public).toString() + ", immutable")
            call.respondFile(file)
        }

        rateLimit(AUTH_LIMIT) {
            post("/auth/signup") {
                val r = call.receive<SignUpRequest>()
                call.respond(io { service.signUp(r.email, r.password, r.name) })
            }
            post("/auth/login") {
                val r = call.receive<LogInRequest>()
                call.respond(io { service.logIn(r.email, r.password) })
            }
        }

        rateLimit(WRITE_LIMIT) {
            post("/auth/logout") {
                call.token()?.let { t -> io { service.logOut(t) } }
                call.respond(HttpStatusCode.NoContent)
            }
            get("/me") { val me = call.user(); call.respond(io { service.me(me) }) }
            put("/me/card") {
                val me = call.user()
                val card = call.receive<StyleCard>()
                call.respond(io { service.saveCard(me, card) })
            }
            delete("/me") { val me = call.user(); io { service.deleteAccount(me) }; call.respond(HttpStatusCode.NoContent) }

            post("/photos") {
                val me = call.user()
                val bytes = call.photoBytes()
                call.respond(PhotoUploaded(io { service.addPhoto(me, bytes) }))
            }
            // which pizza is on this photo? 501 when the server has no model to ask: the app then guesses itself
            post("/photos/sniff") {
                call.user()
                val bytes = call.photoBytes()
                val sniff = classifier?.let { c -> io { c.sniff(bytes) } }
                if (sniff == null) call.respond(HttpStatusCode.NotImplemented, com.vibepizzaiolo.community.ApiError("No classifier here."))
                else call.respond(sniff)
            }

            post("/pizzas") {
                val me = call.user()
                val draft = call.receive<PizzaDraft>()
                call.respond(io { service.post(me, draft) })
            }
            get("/feed") { val me = call.user(); call.respond(io { service.feed(me) }) }
            get("/pizzas/mine") { val me = call.user(); call.respond(io { service.mine(me) }) }
            get("/pizzas") {
                val me = call.user()
                val q = call.request.queryParameters
                val filter = Filter(
                    type = call.enumQuery<PizzaType>("type"), style = call.enumQuery<PizzaStyle>("style"), base = call.enumQuery<Base>("base"),
                    minSpice = q["minSpice"]?.toIntOrNull() ?: 0,
                    minTemp = q["minTemp"]?.toIntOrNull() ?: StyleCard.TEMP_MIN, maxTemp = q["maxTemp"]?.toIntOrNull() ?: StyleCard.TEMP_MAX,
                    query = q["q"].orEmpty(), sort = call.enumQuery<Sort>("sort") ?: Sort.NEWEST,
                )
                call.respond(io { service.search(me, filter) })
            }
            get("/pizzas/{id}") { val me = call.user(); val id = call.param("id"); call.respond(io { service.pizza(me, id) }) }
            post("/pizzas/{id}/swipe") {
                val me = call.user(); val id = call.param("id")
                val like = call.receive<SwipeRequest>().like
                io { service.swipe(me, id, like) }
                call.respond(HttpStatusCode.NoContent)
            }
            get("/pizzas/{id}/comments") { val me = call.user(); val id = call.param("id"); call.respond(io { service.comments(me, id) }) }
            post("/pizzas/{id}/comments") {
                val me = call.user(); val id = call.param("id")
                val text = call.receive<TextRequest>().text
                call.respond(io { service.comment(me, id, text) })
            }
            post("/pizzas/{id}/invite") { val me = call.user(); val id = call.param("id"); call.respond(io { service.invite(me, id) }) }
            post("/pizzas/{id}/report") {
                val me = call.user(); val id = call.param("id")
                val reason = call.receive<TextRequest>().text
                io { service.report(me, id, reason) }
                call.respond(HttpStatusCode.NoContent)
            }

            get("/invites") { val me = call.user(); call.respond(io { service.invites(me) }) }
            post("/invites/{id}/answer") {
                val me = call.user(); val id = call.param("id")
                val accept = call.receive<AnswerRequest>().accept
                call.respond(io { service.answer(me, id, accept) })
            }
            get("/invites/{id}/messages") { val me = call.user(); val id = call.param("id"); call.respond(io { service.messages(me, id) }) }
            post("/invites/{id}/messages") {
                val me = call.user(); val id = call.param("id")
                val text = call.receive<TextRequest>().text
                call.respond(io { service.send(me, id, text) })
            }

            post("/pizzaioli/{id}/block") {
                val me = call.user(); val id = call.param("id")
                io { service.block(me, id) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
