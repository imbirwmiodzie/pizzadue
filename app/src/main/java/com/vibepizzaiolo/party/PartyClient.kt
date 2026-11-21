package com.vibepizzaiolo.party

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One sentence for the pizzaiolo. [status] is the server's answer, 0 when it did not answer at all. */
class PartyException(message: String, val status: Int = 0) : Exception(message)

@Serializable
private data class Refusal(val error: String = "")

/** Pizza nights on the server at [baseUrl]. The pizzaiolo's calls carry the night's host key. */
class PartyClient(baseUrl: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }

    /** What friends open in their browser. */
    fun link(code: String) = "$base/p/$code"

    private suspend fun <B> call(method: String, path: String, key: String? = null, s: KSerializer<B>? = null, body: B? = null): String =
        withContext(Dispatchers.IO) {
            val unreachable = PartyException("The server is not answering. Check your connection.")
            val c = try {
                URL(base + path).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                throw unreachable
            }
            try {
                c.requestMethod = method
                c.connectTimeout = 8000; c.readTimeout = 20000
                c.setRequestProperty("Accept", "application/json")
                key?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                if (s != null && body != null) {
                    val bytes = json.encodeToString(s, body).encodeToByteArray()
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setFixedLengthStreamingMode(bytes.size)
                    c.outputStream.use { it.write(bytes) }
                }
                val code = c.responseCode
                val text = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
                if (code !in 200..299) {
                    val said = runCatching { json.decodeFromString(Refusal.serializer(), text).error }.getOrNull().orEmpty()
                    throw PartyException(said.ifEmpty { if (code >= 500) "The server has a problem ($code). Try again in a moment." else "The server said no ($code)." }, code)
                }
                text
            } catch (e: IOException) {
                throw unreachable
            } catch (e: SecurityException) {
                // no network permission, or a policy on the phone blocks this app
                throw PartyException("This phone does not let the app use the internet.")
            } finally {
                c.disconnect()
            }
        }

    private suspend fun call(method: String, path: String, key: String? = null) = call<Unit>(method, path, key)

    /** An answer that is not what this app expects (another server at that address, an old version) is one sentence too. */
    private fun <T> decode(s: KSerializer<T>, body: String): T = try {
        json.decodeFromString(s, body)
    } catch (e: IllegalArgumentException) {
        throw PartyException("The server's answer makes no sense to the app. Is that the right address?")
    }

    private fun party(body: String) = decode(Party.serializer(), body)

    suspend fun create(draft: PartyDraft): PartyCreated =
        decode(PartyCreated.serializer(), call("POST", "/parties", null, PartyDraft.serializer(), draft))

    suspend fun view(code: String, key: String?): PartyView = decode(PartyView.serializer(), call("GET", "/parties/$code", key))

    suspend fun update(code: String, key: String, draft: PartyDraft) = party(call("PUT", "/parties/$code", key, PartyDraft.serializer(), draft))

    suspend fun setStage(code: String, key: String, stage: PartyStage) =
        party(call("POST", "/parties/$code/stage", key, StageRequest.serializer(), StageRequest(stage)))

    suspend fun setBaked(code: String, key: String, request: BakedRequest) = party(call("POST", "/parties/$code/baked", key, BakedRequest.serializer(), request))

    suspend fun addGuest(code: String, key: String, name: String) =
        party(call("POST", "/parties/$code/guests", key, JoinRequest.serializer(), JoinRequest(name)))

    suspend fun removeGuest(code: String, key: String, guestId: String) = party(call("DELETE", "/parties/$code/guests/$guestId", key))

    suspend fun order(code: String, key: String, guestId: String, orders: List<GuestOrder>) =
        party(call("PUT", "/parties/$code/guests/$guestId/order", key, OrderRequest.serializer(), OrderRequest(orders)))

    suspend fun bring(code: String, key: String, itemId: String, guestId: String?) =
        party(call("PUT", "/parties/$code/bring/$itemId", key, BringRequest.serializer(), BringRequest(guestId)))

    suspend fun delete(code: String, key: String) { call("DELETE", "/parties/$code", key) }

    // ---- what a friend's browser does; the app only needs these in its tests ----
    suspend fun join(code: String, name: String): Joined =
        decode(Joined.serializer(), call("POST", "/parties/$code/join", null, JoinRequest.serializer(), JoinRequest(name)))
}
