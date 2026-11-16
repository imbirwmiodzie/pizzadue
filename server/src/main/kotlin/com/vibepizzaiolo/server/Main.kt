package com.vibepizzaiolo.server

import com.vibepizzaiolo.community.ApiError
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** An answer that is not 200: the status and one sentence for the person holding the phone. */
class ApiException(val status: HttpStatusCode, message: String) : Exception(message)

fun refuse(status: HttpStatusCode, message: String): Nothing = throw ApiException(status, message)

val AUTH_LIMIT = RateLimitName("auth")
val WRITE_LIMIT = RateLimitName("write")
val PARTY_LIMIT = RateLimitName("party")

/** Settings come from the environment; see server/README.md. */
fun main() {
    val log = LoggerFactory.getLogger("server")
    val env = System.getenv()
    val data = File(env["DATA_DIR"] ?: "data").apply { mkdirs() }
    val url = env["DATABASE_URL"] ?: "jdbc:h2:file:${File(data, "pizzaioli").absolutePath};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE".also {
        log.warn("DATABASE_URL is not set: keeping the data in an H2 file under {}. Fine for trying it out; use PostgreSQL for real.", data.absolutePath)
    }
    val db = Db.connect(url, env["DATABASE_USER"], env["DATABASE_PASSWORD"]).apply { migrate() }
    val photos = PhotoStore(File(env["PHOTO_DIR"] ?: File(data, "photos").path))
    val classifier = if (env["ANTHROPIC_API_KEY"].isNullOrBlank()) {
        log.warn("ANTHROPIC_API_KEY is not set: the app guesses the pizza type from colours on the phone.")
        null
    } else {
        ClaudeClassifier(env["CLASSIFIER_MODEL"] ?: "claude-opus-5-5")
    }
    val port = env["PORT"]?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) { module(db, photos, classifier) }.start(wait = true)
}

fun Application.module(db: Db, photos: PhotoStore, classifier: Classifier?, now: () -> Long = System::currentTimeMillis) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.message ?: "No.")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("That request could not be read.")) }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("unhandled", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("The server tripped. Try again in a moment."))
        }
        status(HttpStatusCode.TooManyRequests) { call, status -> call.respond(status, ApiError("Too fast. Wait a moment and try again.")) }
    }
    install(RateLimit) {
        // sign-up and login: per address, slow enough to make password guessing pointless
        register(AUTH_LIMIT) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
            requestKey { call -> call.request.header("X-Forwarded-For")?.substringBefore(',')?.trim() ?: call.request.origin.remoteHost }
        }
        // posting, commenting, swiping: per login
        register(WRITE_LIMIT) {
            rateLimiter(limit = 90, refillPeriod = 1.minutes)
            requestKey { call -> call.request.header("Authorization") ?: call.request.origin.remoteHost }
        }
        // a pizza night: per guest or host key; a browser without one yet counts by address
        register(PARTY_LIMIT) {
            rateLimiter(limit = 120, refillPeriod = 1.minutes)
            requestKey { call ->
                call.request.header("Authorization")
                    ?: call.request.header("X-Forwarded-For")?.substringBefore(',')?.trim()
                    ?: call.request.origin.remoteHost
            }
        }
    }
    routes(Service(db, photos, now), photos, classifier)
    partyRoutes(PartyService(db, now))
    reelRoutes()
}
