package com.vibepizzaiolo.community

/** A problem worth telling the user about, in words they can act on. */
open class CommunityException(message: String) : Exception(message)

/** The server could not be reached at all (as opposed to it saying no). */
class ServerUnreachable(message: String = "The community server is not answering.") : CommunityException(message)

/** A photo ready to post: the JPEG, plus a small copy of its pixels for the colour guess. */
class PhotoData(val jpeg: ByteArray, val pixels: IntArray, val width: Int, val height: Int)

/**
 * Everything the community screens need. Two implementations: [RemoteCommunityRepository]
 * talks to the server, [LocalCommunityRepository] keeps it all on the phone with sample
 * pizzaioli, so the app works before the server exists and whenever it is down.
 */
interface CommunityRepository {
    /** true: everything stays on this phone. */
    val offline: Boolean

    suspend fun me(): Pizzaiolo?
    suspend fun signUp(email: String, password: String, name: String): Pizzaiolo
    suspend fun logIn(email: String, password: String): Pizzaiolo
    suspend fun logOut()
    suspend fun deleteAccount()
    suspend fun saveCard(card: StyleCard): Pizzaiolo

    suspend fun sniff(photo: PhotoData): Sniff
    suspend fun post(photo: PhotoData, draft: PizzaDraft): Pizza

    /** Pizzas by others that I have not swiped yet. */
    suspend fun feed(): List<Pizza>
    suspend fun search(filter: Filter): List<Pizza>
    suspend fun mine(): List<Pizza>
    suspend fun pizza(id: String): Pizza
    suspend fun swipe(pizzaId: String, like: Boolean)

    suspend fun comments(pizzaId: String): List<Comment>
    suspend fun comment(pizzaId: String, text: String): Comment

    suspend fun invite(pizzaId: String): Invite
    suspend fun invites(): List<Invite>
    suspend fun answer(inviteId: String, accept: Boolean): Invite
    suspend fun messages(inviteId: String): List<ChatMessage>
    suspend fun send(inviteId: String, text: String): ChatMessage

    suspend fun report(pizzaId: String, reason: String)
    suspend fun block(pizzaioloId: String)
}

object Rules {
    const val MAX_COMMENT = 500
    const val MAX_CAPTION = 200
    const val MIN_PASSWORD = 8

    fun email(raw: String): String {
        val e = raw.trim().lowercase()
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$").matches(e)) throw CommunityException("That does not look like an email address.")
        return e
    }

    fun password(raw: String): String {
        if (raw.length < MIN_PASSWORD) throw CommunityException("The password needs at least $MIN_PASSWORD characters.")
        return raw
    }

    fun name(raw: String): String {
        val n = raw.trim()
        if (n.length !in 2..30) throw CommunityException("Your pizzaiolo name needs 2 to 30 characters.")
        return n
    }

    fun text(raw: String, max: Int = MAX_COMMENT): String {
        val t = raw.trim()
        if (t.isEmpty()) throw CommunityException("Write something first.")
        if (t.length > max) throw CommunityException("That is too long (at most $max characters).")
        return t
    }
}
