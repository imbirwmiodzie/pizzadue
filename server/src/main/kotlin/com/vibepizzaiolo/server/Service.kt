package com.vibepizzaiolo.server

import com.password4j.Password
import com.vibepizzaiolo.community.Base
import com.vibepizzaiolo.community.ChatMessage
import com.vibepizzaiolo.community.Comment
import com.vibepizzaiolo.community.Filter
import com.vibepizzaiolo.community.Invite
import com.vibepizzaiolo.community.InviteStatus
import com.vibepizzaiolo.community.Oven
import com.vibepizzaiolo.community.Pizza
import com.vibepizzaiolo.community.PizzaDraft
import com.vibepizzaiolo.community.PizzaStyle
import com.vibepizzaiolo.community.PizzaType
import com.vibepizzaiolo.community.Pizzaiolo
import com.vibepizzaiolo.community.Session
import com.vibepizzaiolo.community.Sort
import com.vibepizzaiolo.community.StyleCard
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.ResultSet
import java.util.Base64
import java.util.UUID

/** Everything the community does, as SQL. The routes only read the request and call in here. */
class Service(private val db: Db, private val photos: PhotoStore, private val now: () -> Long) {
    private val random = SecureRandom()

    private fun id() = UUID.randomUUID().toString()
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---- accounts ----

    private fun ResultSet.card(prefix: String = ""): StyleCard? {
        val spice = intOrNull("${prefix}spice") ?: return null
        return StyleCard(
            spice, Base.valueOf(getString("${prefix}base")), PizzaStyle.valueOf(getString("${prefix}style")),
            getInt("${prefix}temp_c"), Oven.valueOf(getString("${prefix}oven")),
        )
    }

    private fun pizzaiolo(userId: String): Pizzaiolo =
        db.one("SELECT id, name, spice, base, style, temp_c, oven FROM users WHERE id = ?", userId) { Pizzaiolo(it.getString("id"), it.getString("name"), it.card()) }
            ?: refuse(HttpStatusCode.Unauthorized, "Log in again.")

    private fun openSession(userId: String): Session {
        val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        db.update("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, ?)", sha(token), userId, now() + SESSION_MS)
        return Session(token, pizzaiolo(userId))
    }

    fun signUp(email: String, password: String, name: String): Session {
        val e = email.trim().lowercase()
        val n = name.trim()
        if (!EMAIL.matches(e) || e.length > 254) refuse(HttpStatusCode.BadRequest, "That does not look like an email address.")
        if (password.length !in 8..200) refuse(HttpStatusCode.BadRequest, "The password needs at least 8 characters.")
        if (n.length !in 2..30) refuse(HttpStatusCode.BadRequest, "Your pizzaiolo name needs 2 to 30 characters.")
        if (db.one("SELECT 1 FROM users WHERE email = ?", e) { 1 } != null) refuse(HttpStatusCode.Conflict, "There is already an account with this email. Log in instead.")
        val userId = id()
        val hash = Password.hash(password).addRandomSalt().withArgon2().result
        db.update("INSERT INTO users (id, email, name, password_hash, created_at) VALUES (?, ?, ?, ?, ?)", userId, e, n, hash, now())
        return openSession(userId)
    }

    fun logIn(email: String, password: String): Session {
        val row = db.one("SELECT id, password_hash FROM users WHERE email = ?", email.trim().lowercase()) { it.getString(1) to it.getString(2) }
        // the same answer whether the email or the password is wrong
        if (row == null || !Password.check(password, row.second).withArgon2()) refuse(HttpStatusCode.Unauthorized, "Wrong email or password.")
        return openSession(row.first)
    }

    /** The user a login token belongs to, or null. */
    fun userFor(token: String): String? =
        db.one("SELECT user_id FROM sessions WHERE token_hash = ? AND expires_at > ?", sha(token), now()) { it.getString(1) }

    fun logOut(token: String) { db.update("DELETE FROM sessions WHERE token_hash = ?", sha(token)) }

    fun me(userId: String) = pizzaiolo(userId)

    fun saveCard(userId: String, card: StyleCard): Pizzaiolo {
        val c = card.checked()
        db.update("UPDATE users SET spice = ?, base = ?, style = ?, temp_c = ?, oven = ? WHERE id = ?", c.spice, c.base.name, c.style.name, c.tempC, c.oven.name, userId)
        return pizzaiolo(userId)
    }

    /** Removes the user and, through the foreign keys, everything they posted, said and swiped. */
    fun deleteAccount(userId: String) {
        val theirs = db.query("SELECT id FROM photos WHERE user_id = ?", userId) { it.getString(1) }
        db.update("DELETE FROM users WHERE id = ?", userId)
        theirs.forEach(photos::delete)
    }

    // ---- photos ----

    fun addPhoto(userId: String, bytes: ByteArray): String {
        if (bytes.size > PhotoStore.MAX_BYTES) refuse(HttpStatusCode.PayloadTooLarge, "That photo is too large.")
        val photoId = id()
        if (!photos.save(photoId, bytes)) refuse(HttpStatusCode.BadRequest, "That file is not a photo.")
        db.update("INSERT INTO photos (id, user_id, created_at) VALUES (?, ?, ?)", photoId, userId, now())
        return photoId
    }

    // ---- pizzas ----

    /** Pizzas as [me] sees them: counts, my own swipe, and without anyone blocked (either way) or reported by me. */
    private fun pizzas(me: String, where: String, vararg args: Any?, order: String = "created_at DESC", limit: Int = 100): List<Pizza> = db.query(
        """SELECT * FROM (
             SELECT p.id, p.user_id, p.photo_id, p.type, p.style, p.base, p.spice, p.temp_c, p.oven, p.bake_sec, p.hydration, p.caption, p.created_at,
               u.name AS author_name, u.spice AS u_spice, u.base AS u_base, u.style AS u_style, u.temp_c AS u_temp_c, u.oven AS u_oven,
               (SELECT COUNT(*) FROM swipes s WHERE s.pizza_id = p.id AND s.liked = TRUE) AS likes,
               (SELECT COUNT(*) FROM swipes s WHERE s.pizza_id = p.id AND s.liked = FALSE) AS passes,
               (SELECT COUNT(*) FROM comments c WHERE c.pizza_id = p.id) AS n_comments,
               (SELECT s.liked FROM swipes s WHERE s.pizza_id = p.id AND s.user_id = ?) AS my_swipe,
               (SELECT COUNT(*) FROM invites i WHERE i.pizza_id = p.id AND i.from_user = ?) AS n_invited
             FROM pizzas p JOIN users u ON u.id = p.user_id
             WHERE p.hidden = FALSE
               AND p.user_id NOT IN (SELECT blocked_id FROM blocks WHERE user_id = ?)
               AND p.user_id NOT IN (SELECT user_id FROM blocks WHERE blocked_id = ?)
               AND p.id NOT IN (SELECT pizza_id FROM reports WHERE user_id = ?)
               AND ($where)
           ) x ORDER BY $order LIMIT $limit""",
        me, me, me, me, me, *args,
    ) { r ->
        Pizza(
            id = r.getString("id"),
            author = Pizzaiolo(r.getString("user_id"), r.getString("author_name"), r.card("u_")),
            photo = "/photos/${r.getString("photo_id")}/card.jpg",
            type = PizzaType.valueOf(r.getString("type")), style = PizzaStyle.valueOf(r.getString("style")), base = Base.valueOf(r.getString("base")),
            spice = r.getInt("spice"), tempC = r.getInt("temp_c"), oven = Oven.valueOf(r.getString("oven")),
            bakeSec = r.intOrNull("bake_sec"), hydration = r.intOrNull("hydration"), caption = r.getString("caption"),
            createdAt = r.getLong("created_at"), likes = r.getInt("likes"), passes = r.getInt("passes"), comments = r.getInt("n_comments"),
            mine = r.getString("user_id") == me, mySwipe = r.boolOrNull("my_swipe"), invited = r.getInt("n_invited") > 0,
        )
    }

    fun post(me: String, d: PizzaDraft): Pizza {
        val photoId = d.photoId ?: refuse(HttpStatusCode.BadRequest, "The photo is missing.")
        if (db.one("SELECT 1 FROM photos WHERE id = ? AND user_id = ?", photoId, me) { 1 } == null) refuse(HttpStatusCode.BadRequest, "Upload the photo first.")
        if (d.base == Base.BOTH) refuse(HttpStatusCode.BadRequest, "One pizza is either red or white.")
        val pizzaId = id()
        db.update(
            "INSERT INTO pizzas (id, user_id, photo_id, type, style, base, spice, temp_c, oven, bake_sec, hydration, caption, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            pizzaId, me, photoId, d.type.name, d.style.name, d.base.name,
            d.spice.coerceIn(0, StyleCard.MAX_SPICE), d.tempC.coerceIn(StyleCard.TEMP_MIN, StyleCard.TEMP_MAX), d.oven.name,
            d.bakeSec?.coerceIn(1, 7200), d.hydration?.coerceIn(30, 120), d.caption.trim().take(200), now(),
        )
        return pizza(me, pizzaId)
    }

    fun pizza(me: String, pizzaId: String): Pizza =
        pizzas(me, "p.id = ?", pizzaId).firstOrNull() ?: refuse(HttpStatusCode.NotFound, "That pizza is gone.")

    /** Other people's pizzas I have not swiped yet, newest first. */
    fun feed(me: String): List<Pizza> =
        pizzas(me, "p.user_id <> ? AND NOT EXISTS (SELECT 1 FROM swipes s WHERE s.pizza_id = p.id AND s.user_id = ?)", me, me, limit = 30)

    fun mine(me: String): List<Pizza> = pizzas(me, "p.user_id = ?", me)

    fun search(me: String, f: Filter): List<Pizza> {
        val where = mutableListOf("p.spice >= ?", "p.temp_c BETWEEN ? AND ?")
        val args = mutableListOf<Any?>(f.minSpice, f.minTemp, f.maxTemp)
        f.type?.let { where += "p.type = ?"; args += it.name }
        f.style?.let { where += "p.style = ?"; args += it.name }
        f.base?.let { where += "p.base = ?"; args += it.name }
        val q = f.query.trim().lowercase().take(60)
        if (q.isNotEmpty()) {
            // the words people type are the labels, which the database stores as names: match those in code
            val types = PizzaType.entries.filter { it.label.lowercase().contains(q) }.map { it.name }
            val styles = PizzaStyle.entries.filter { it.label.lowercase().contains(q) }.map { it.name }
            val parts = mutableListOf("LOWER(p.caption) LIKE ? ESCAPE '!'", "LOWER(u.name) LIKE ? ESCAPE '!'")
            // % and _ typed by the user are ordinary characters, not wildcards
            val like = "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"
            args += like; args += like
            if (types.isNotEmpty()) { parts += "p.type IN (${types.joinToString { "?" }})"; args.addAll(types) }
            if (styles.isNotEmpty()) { parts += "p.style IN (${styles.joinToString { "?" }})"; args.addAll(styles) }
            where += "(" + parts.joinToString(" OR ") + ")"
        }
        val order = when (f.sort) {
            Sort.NEWEST -> "created_at DESC"
            // a pizza nobody has swiped yet counts as half liked: above the passed-over, below the loved
            Sort.BEST -> "CASE WHEN likes + passes = 0 THEN 50 ELSE likes * 100 / (likes + passes) END DESC, likes DESC, created_at DESC"
        }
        return pizzas(me, where.joinToString(" AND "), *args.toTypedArray(), order = order)
    }

    fun swipe(me: String, pizzaId: String, like: Boolean) {
        if (pizza(me, pizzaId).mine) refuse(HttpStatusCode.BadRequest, "You cannot rate your own pizza.")
        db.tx { c ->
            c.exec("DELETE FROM swipes WHERE pizza_id = ? AND user_id = ?", pizzaId, me)
            c.exec("INSERT INTO swipes (pizza_id, user_id, liked) VALUES (?, ?, ?)", pizzaId, me, like)
        }
    }

    // ---- comments ----

    private fun text(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) refuse(HttpStatusCode.BadRequest, "Write something first.")
        if (t.length > 500) refuse(HttpStatusCode.BadRequest, "That is too long (at most 500 characters).")
        return t
    }

    fun comments(me: String, pizzaId: String): List<Comment> {
        pizza(me, pizzaId)
        return db.query(
            """SELECT c.id, c.user_id, c.body, c.created_at, u.name FROM comments c JOIN users u ON u.id = c.user_id
               WHERE c.pizza_id = ? AND c.user_id NOT IN (SELECT blocked_id FROM blocks WHERE user_id = ?) ORDER BY c.created_at""",
            pizzaId, me,
        ) { Comment(it.getString("id"), it.getString("name"), it.getString("body"), it.getLong("created_at"), it.getString("user_id") == me) }
    }

    fun comment(me: String, pizzaId: String, raw: String): Comment {
        val t = text(raw)
        pizza(me, pizzaId)
        val c = Comment(id(), pizzaiolo(me).name, t, now(), mine = true)
        db.update("INSERT INTO comments (id, pizza_id, user_id, body, created_at) VALUES (?, ?, ?, ?, ?)", c.id, pizzaId, me, t, c.at)
        return c
    }

    // ---- let's eat together ----

    private fun invitesWhere(me: String, where: String, vararg args: Any?): List<Invite> = db.query(
        """SELECT i.id, i.pizza_id, i.from_user, i.to_user, i.status, i.created_at, p.photo_id, p.type,
             o.id AS o_id, o.name AS o_name, o.spice, o.base, o.style, o.temp_c, o.oven,
             (SELECT m.body FROM messages m WHERE m.invite_id = i.id ORDER BY m.created_at DESC LIMIT 1) AS last_message
           FROM invites i JOIN pizzas p ON p.id = i.pizza_id
             JOIN users o ON o.id = CASE WHEN i.from_user = ? THEN i.to_user ELSE i.from_user END
           WHERE (i.from_user = ? OR i.to_user = ?) AND ($where) ORDER BY i.created_at DESC""",
        me, me, me, *args,
    ) { r ->
        Invite(
            id = r.getString("id"), pizzaId = r.getString("pizza_id"), pizzaPhoto = "/photos/${r.getString("photo_id")}/card.jpg",
            pizzaType = PizzaType.valueOf(r.getString("type")), other = Pizzaiolo(r.getString("o_id"), r.getString("o_name"), r.card()),
            outgoing = r.getString("from_user") == me, status = InviteStatus.valueOf(r.getString("status")), at = r.getLong("created_at"),
            lastMessage = r.getString("last_message"),
        )
    }

    fun invites(me: String): List<Invite> = invitesWhere(
        me, "o.id NOT IN (SELECT blocked_id FROM blocks WHERE user_id = ?) AND o.id NOT IN (SELECT user_id FROM blocks WHERE blocked_id = ?)", me, me,
    )

    private fun inviteOf(me: String, inviteId: String): Invite =
        invitesWhere(me, "i.id = ?", inviteId).firstOrNull() ?: refuse(HttpStatusCode.NotFound, "That invitation is gone.")

    fun invite(me: String, pizzaId: String): Invite {
        val p = pizza(me, pizzaId)
        if (p.mine) refuse(HttpStatusCode.BadRequest, "That is your own pizza.")
        val existing = db.one("SELECT id FROM invites WHERE pizza_id = ? AND from_user = ?", pizzaId, me) { it.getString(1) }
        if (existing != null) return inviteOf(me, existing)
        val inviteId = id()
        db.update(
            "INSERT INTO invites (id, pizza_id, from_user, to_user, status, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            inviteId, pizzaId, me, p.author.id, InviteStatus.PENDING.name, now(),
        )
        return inviteOf(me, inviteId)
    }

    fun answer(me: String, inviteId: String, accept: Boolean): Invite {
        val inv = inviteOf(me, inviteId)
        if (inv.outgoing) refuse(HttpStatusCode.Forbidden, "Only the invited pizzaiolo can answer.")
        if (inv.status != InviteStatus.PENDING) return inv
        db.update("UPDATE invites SET status = ? WHERE id = ?", (if (accept) InviteStatus.ACCEPTED else InviteStatus.DECLINED).name, inviteId)
        return inviteOf(me, inviteId)
    }

    fun messages(me: String, inviteId: String): List<ChatMessage> {
        inviteOf(me, inviteId)
        return db.query("SELECT id, user_id, body, created_at FROM messages WHERE invite_id = ? ORDER BY created_at", inviteId) {
            ChatMessage(it.getString("id"), it.getString("user_id") == me, it.getString("body"), it.getLong("created_at"))
        }
    }

    fun send(me: String, inviteId: String, raw: String): ChatMessage {
        val t = text(raw)
        val inv = inviteOf(me, inviteId)
        if (inv.status != InviteStatus.ACCEPTED) refuse(HttpStatusCode.Forbidden, "You can chat once the invitation is accepted.")
        if (db.one("SELECT 1 FROM blocks WHERE (user_id = ? AND blocked_id = ?) OR (user_id = ? AND blocked_id = ?)", me, inv.other.id, inv.other.id, me) { 1 } != null) {
            refuse(HttpStatusCode.Forbidden, "This chat is closed.")
        }
        val m = ChatMessage(id(), mine = true, text = t, at = now())
        db.update("INSERT INTO messages (id, invite_id, user_id, body, created_at) VALUES (?, ?, ?, ?, ?)", m.id, inviteId, me, t, m.at)
        return m
    }

    // ---- safety ----

    /** Hidden for the reporter at once; hidden for everyone after [REPORTS_TO_HIDE] different people. */
    fun report(me: String, pizzaId: String, reason: String) {
        if (pizza(me, pizzaId).mine) refuse(HttpStatusCode.BadRequest, "That is your own pizza.")
        db.update("INSERT INTO reports (pizza_id, user_id, reason, created_at) VALUES (?, ?, ?, ?)", pizzaId, me, reason.trim().take(500).ifEmpty { "reported" }, now())
        val count = db.one("SELECT COUNT(*) FROM reports WHERE pizza_id = ?", pizzaId) { it.getInt(1) } ?: 0
        if (count >= REPORTS_TO_HIDE) db.update("UPDATE pizzas SET hidden = TRUE WHERE id = ?", pizzaId)
    }

    fun block(me: String, otherId: String) {
        if (otherId == me) refuse(HttpStatusCode.BadRequest, "You cannot block yourself.")
        if (db.one("SELECT 1 FROM users WHERE id = ?", otherId) { 1 } == null) refuse(HttpStatusCode.NotFound, "That pizzaiolo is gone.")
        db.tx { c ->
            c.exec("DELETE FROM blocks WHERE user_id = ? AND blocked_id = ?", me, otherId)
            c.exec("INSERT INTO blocks (user_id, blocked_id) VALUES (?, ?)", me, otherId)
        }
    }

    companion object {
        const val SESSION_MS = 90L * 24 * 3600 * 1000
        const val REPORTS_TO_HIDE = 3
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    }
}
