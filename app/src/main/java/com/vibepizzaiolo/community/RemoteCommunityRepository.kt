package com.vibepizzaiolo.community

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Where the login token is kept between app starts. */
interface TokenStore {
    var token: String?
}

/** The community on the server at [baseUrl] (no trailing slash). */
class RemoteCommunityRepository(baseUrl: String, private val tokens: TokenStore) : CommunityRepository {
    override val offline = false

    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    /** Is the server there? Short timeouts: this decides whether to fall back to the phone. */
    suspend fun healthy(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("$base/health").openConnection() as HttpURLConnection
            c.connectTimeout = 4000; c.readTimeout = 4000
            try { c.responseCode == 200 } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    private class Reply(val code: Int, val body: String)

    private suspend fun call(method: String, path: String, body: ByteArray? = null, contentType: String = "application/json"): Reply =
        withContext(Dispatchers.IO) {
            val c = try {
                URL(base + path).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw ServerUnreachable()
            }
            try {
                c.requestMethod = method
                c.connectTimeout = 8000; c.readTimeout = 30000
                c.setRequestProperty("Accept", "application/json")
                tokens.token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", contentType)
                    c.setFixedLengthStreamingMode(body.size)
                    c.outputStream.use { it.write(body) }
                }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
                Reply(code, text)
            } catch (e: IOException) {
                throw ServerUnreachable()
            } finally {
                c.disconnect()
            }
        }

    /** Turns anything but 2xx into a message for the user. */
    private fun Reply.ok(): String {
        if (code in 200..299) return body
        if (code == 401) tokens.token = null
        val said = runCatching { json.decodeFromString(ApiError.serializer(), body).error }.getOrNull()
        throw CommunityException(
            said ?: when (code) {
                401 -> "Log in again."
                429 -> "Too fast. Wait a moment and try again."
                in 500..599 -> "The community server has a problem ($code). Try again in a moment."
                else -> "The server said no ($code)."
            }
        )
    }

    private fun <T> enc(s: kotlinx.serialization.KSerializer<T>, v: T) = json.encodeToString(s, v).encodeToByteArray()
    private fun url(v: String) = URLEncoder.encode(v, "UTF-8")

    /** Photo paths come back relative to the server. */
    private fun Pizza.abs() = copy(photo = absolute(photo))
    private fun absolute(photo: String) = if (photo.startsWith("/")) base + photo else photo
    private fun pizzas(body: String) = json.decodeFromString(ListSerializer(Pizza.serializer()), body).map { it.abs() }

    // ---- account ----
    override suspend fun me(): Pizzaiolo? {
        if (tokens.token == null) return null
        val r = call("GET", "/me")
        if (r.code == 401) { tokens.token = null; return null }
        return json.decodeFromString(Pizzaiolo.serializer(), r.ok())
    }

    private fun session(body: String): Pizzaiolo {
        val s = json.decodeFromString(Session.serializer(), body)
        tokens.token = s.token
        return s.me
    }

    override suspend fun signUp(email: String, password: String, name: String): Pizzaiolo {
        val req = SignUpRequest(Rules.email(email), Rules.password(password), Rules.name(name))
        return session(call("POST", "/auth/signup", enc(SignUpRequest.serializer(), req)).ok())
    }

    override suspend fun logIn(email: String, password: String): Pizzaiolo =
        session(call("POST", "/auth/login", enc(LogInRequest.serializer(), LogInRequest(email.trim().lowercase(), password))).ok())

    override suspend fun logOut() {
        runCatching { call("POST", "/auth/logout") }
        tokens.token = null
    }

    override suspend fun deleteAccount() {
        call("DELETE", "/me").ok()
        tokens.token = null
    }

    override suspend fun saveCard(card: StyleCard): Pizzaiolo =
        json.decodeFromString(Pizzaiolo.serializer(), call("PUT", "/me/card", enc(StyleCard.serializer(), card.checked())).ok())

    // ---- pizzas ----
    override suspend fun sniff(photo: PhotoData): Sniff {
        // the server's model looks at the photo; without one (or without the server) the colours decide
        val fromServer = runCatching {
            val r = call("POST", "/photos/sniff", photo.jpeg, "image/jpeg")
            if (r.code == 200) json.decodeFromString(Sniff.serializer(), r.body) else null
        }.getOrNull()
        return fromServer ?: withContext(Dispatchers.Default) { ColorSniffer.sniff(photo.pixels, photo.width, photo.height) }
    }

    override suspend fun post(photo: PhotoData, draft: PizzaDraft): Pizza {
        val up = json.decodeFromString(PhotoUploaded.serializer(), call("POST", "/photos", photo.jpeg, "image/jpeg").ok())
        val body = enc(PizzaDraft.serializer(), draft.copy(photoId = up.photoId))
        return json.decodeFromString(Pizza.serializer(), call("POST", "/pizzas", body).ok()).abs()
    }

    override suspend fun feed(): List<Pizza> = pizzas(call("GET", "/feed").ok())

    override suspend fun search(filter: Filter): List<Pizza> {
        val q = buildList {
            filter.type?.let { add("type=${it.name}") }
            filter.style?.let { add("style=${it.name}") }
            filter.base?.let { add("base=${it.name}") }
            if (filter.minSpice > 0) add("minSpice=${filter.minSpice}")
            if (filter.minTemp > StyleCard.TEMP_MIN) add("minTemp=${filter.minTemp}")
            if (filter.maxTemp < StyleCard.TEMP_MAX) add("maxTemp=${filter.maxTemp}")
            if (filter.query.isNotBlank()) add("q=${url(filter.query.trim())}")
            add("sort=${filter.sort.name}")
        }.joinToString("&")
        return pizzas(call("GET", "/pizzas?$q").ok())
    }

    override suspend fun mine(): List<Pizza> = pizzas(call("GET", "/pizzas/mine").ok())

    override suspend fun pizza(id: String): Pizza = json.decodeFromString(Pizza.serializer(), call("GET", "/pizzas/$id").ok()).abs()

    override suspend fun swipe(pizzaId: String, like: Boolean) {
        call("POST", "/pizzas/$pizzaId/swipe", enc(SwipeRequest.serializer(), SwipeRequest(like))).ok()
    }

    // ---- comments ----
    override suspend fun comments(pizzaId: String): List<Comment> =
        json.decodeFromString(ListSerializer(Comment.serializer()), call("GET", "/pizzas/$pizzaId/comments").ok())

    override suspend fun comment(pizzaId: String, text: String): Comment =
        json.decodeFromString(Comment.serializer(), call("POST", "/pizzas/$pizzaId/comments", enc(TextRequest.serializer(), TextRequest(Rules.text(text)))).ok())

    // ---- let's eat together ----
    private fun parseInvite(body: String) = json.decodeFromString(Invite.serializer(), body).let { it.copy(pizzaPhoto = absolute(it.pizzaPhoto)) }

    override suspend fun invite(pizzaId: String): Invite = parseInvite(call("POST", "/pizzas/$pizzaId/invite").ok())

    override suspend fun invites(): List<Invite> =
        json.decodeFromString(ListSerializer(Invite.serializer()), call("GET", "/invites").ok()).map { it.copy(pizzaPhoto = absolute(it.pizzaPhoto)) }

    override suspend fun answer(inviteId: String, accept: Boolean): Invite =
        parseInvite(call("POST", "/invites/$inviteId/answer", enc(AnswerRequest.serializer(), AnswerRequest(accept))).ok())

    override suspend fun messages(inviteId: String): List<ChatMessage> =
        json.decodeFromString(ListSerializer(ChatMessage.serializer()), call("GET", "/invites/$inviteId/messages").ok())

    override suspend fun send(inviteId: String, text: String): ChatMessage =
        json.decodeFromString(ChatMessage.serializer(), call("POST", "/invites/$inviteId/messages", enc(TextRequest.serializer(), TextRequest(Rules.text(text)))).ok())

    // ---- safety ----
    override suspend fun report(pizzaId: String, reason: String) {
        call("POST", "/pizzas/$pizzaId/report", enc(TextRequest.serializer(), TextRequest(reason.ifBlank { "reported" }))).ok()
    }

    override suspend fun block(pizzaioloId: String) {
        call("POST", "/pizzaioli/$pizzaioloId/block").ok()
    }
}
