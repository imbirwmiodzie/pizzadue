package com.vibepizzaiolo.server

import com.vibepizzaiolo.party.BakedRequest
import com.vibepizzaiolo.party.BringItem
import com.vibepizzaiolo.party.GuestOrder
import com.vibepizzaiolo.party.Joined
import com.vibepizzaiolo.party.MenuPizza
import com.vibepizzaiolo.party.Party
import com.vibepizzaiolo.party.PartyCreated
import com.vibepizzaiolo.party.PartyDraft
import com.vibepizzaiolo.party.PartyGuest
import com.vibepizzaiolo.party.PartyRules
import com.vibepizzaiolo.party.PartyStage
import com.vibepizzaiolo.party.PartyView
import io.ktor.http.HttpStatusCode
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Pizza nights. Nobody logs in: the link's code invites, the pizzaiolo holds a host key,
 * and every guest's browser holds a key for its own name. Keys are stored hashed.
 */
class PartyService(private val db: Db, private val now: () -> Long) {
    private val random = SecureRandom()

    private fun id() = UUID.randomUUID().toString()
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun key() = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun code() = String(CharArray(CODE_LENGTH) { CODE_CHARS[random.nextInt(CODE_CHARS.length)] })

    /** Who is calling: the pizzaiolo, a guest, or just somebody with the link. */
    private class Who(val host: Boolean, val guestId: String?)

    private fun who(code: String, key: String?): Who {
        if (key == null) return Who(false, null)
        val hash = sha(key)
        if (db.one("SELECT 1 FROM parties WHERE code = ? AND host_key_hash = ?", code, hash) { 1 } != null) {
            return Who(true, db.one("SELECT id FROM party_guests WHERE party_code = ? AND is_host = TRUE", code) { it.getString(1) })
        }
        return Who(false, db.one("SELECT id FROM party_guests WHERE party_code = ? AND key_hash = ?", code, hash) { it.getString(1) })
    }

    private fun host(code: String, key: String?) {
        load(code)
        if (!who(code, key).host) refuse(HttpStatusCode.Forbidden, "Only the pizzaiolo can do that.")
    }

    private fun load(code: String): Party {
        val party = db.one("SELECT title, when_text, note, stage, spare, spare_baked, created_at FROM parties WHERE code = ?", code) {
            Party(
                code = code, title = it.getString("title"), whenText = it.getString("when_text"), note = it.getString("note"),
                stage = PartyStage.valueOf(it.getString("stage")), spare = it.getInt("spare"), spareBaked = it.getInt("spare_baked"),
                createdAt = it.getLong("created_at"),
            )
        } ?: refuse(HttpStatusCode.NotFound, "This pizza night does not exist (any more).")
        val menu = db.query("SELECT id, name, note FROM party_pizzas WHERE party_code = ? ORDER BY pos", code) {
            MenuPizza(it.getString("id"), it.getString("name"), it.getString("note"))
        }
        val place = menu.withIndex().associate { it.value.id to it.index }
        val orders = db.query(
            "SELECT o.guest_id, o.pizza_id, o.qty, o.baked FROM party_orders o JOIN party_guests g ON g.id = o.guest_id WHERE g.party_code = ?", code,
        ) { it.getString("guest_id") to GuestOrder(it.getString("pizza_id"), it.getInt("qty"), it.getInt("baked")) }.groupBy({ it.first }, { it.second })
        val guests = db.query("SELECT id, name, is_host FROM party_guests WHERE party_code = ? ORDER BY created_at, id", code) {
            val guestId = it.getString("id")
            PartyGuest(guestId, it.getString("name"), it.getBoolean("is_host"), orders[guestId].orEmpty().sortedBy { o -> place[o.pizzaId] ?: 0 })
        }
        val bring = db.query("SELECT id, name, guest_id FROM party_bring WHERE party_code = ? ORDER BY pos", code) {
            BringItem(it.getString("id"), it.getString("name"), it.getString("guest_id"))
        }
        return party.copy(menu = menu, guests = guests, bring = bring)
    }

    fun view(code: String, key: String?): PartyView {
        val party = load(code)
        val w = who(code, key)
        return PartyView(party, w.guestId, w.host)
    }

    // ---- the pizzaiolo ----

    private fun name(raw: String, what: String = "Your name"): String {
        val n = raw.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) refuse(HttpStatusCode.BadRequest, "$what is missing.")
        if (n.length > PartyRules.NAME) refuse(HttpStatusCode.BadRequest, "$what is too long (at most ${PartyRules.NAME} characters).")
        return n
    }

    /** A draft with everything trimmed, cut to size and without doubles. */
    private fun checked(d: PartyDraft): PartyDraft {
        val title = d.title.trim().take(PartyRules.TITLE)
        if (title.isEmpty()) refuse(HttpStatusCode.BadRequest, "Give the night a name.")
        val menu = d.menu.map { it.copy(name = it.name.trim().take(PartyRules.ITEM), note = it.note.trim().take(PartyRules.PIZZA_NOTE)) }
            .filter { it.name.isNotEmpty() }.distinctBy { it.name.lowercase() }
        if (menu.isEmpty()) refuse(HttpStatusCode.BadRequest, "Put at least one pizza on the menu.")
        if (menu.size > PartyRules.MAX_MENU) refuse(HttpStatusCode.BadRequest, "At most ${PartyRules.MAX_MENU} pizzas fit on the menu.")
        val bring = d.bring.map { it.copy(name = it.name.trim().take(PartyRules.ITEM)) }.filter { it.name.isNotEmpty() }.distinctBy { it.name.lowercase() }
        if (bring.size > PartyRules.MAX_BRING) refuse(HttpStatusCode.BadRequest, "At most ${PartyRules.MAX_BRING} things fit on the list.")
        return d.copy(
            title = title, whenText = d.whenText.trim().take(PartyRules.WHEN), note = d.note.trim().take(PartyRules.NOTE),
            spare = d.spare.coerceIn(0, PartyRules.MAX_SPARE), menu = menu, bring = bring,
        )
    }

    fun create(draft: PartyDraft): PartyCreated {
        val d = checked(draft)
        val hostName = name(d.hostName)
        // nights nobody looked after for two months are forgotten
        db.update("DELETE FROM parties WHERE created_at < ?", now() - KEEP_MS)
        val code = code()
        val hostKey = key()
        val hostGuest = id()
        db.tx { c ->
            c.exec(
                "INSERT INTO parties (code, host_key_hash, title, when_text, note, stage, spare, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                code, sha(hostKey), d.title, d.whenText, d.note, PartyStage.ORDERS.name, d.spare, now(),
            )
            c.exec("INSERT INTO party_guests (id, party_code, name, is_host, created_at) VALUES (?, ?, ?, TRUE, ?)", hostGuest, code, hostName, now())
            d.menu.forEachIndexed { i, m -> c.exec("INSERT INTO party_pizzas (id, party_code, name, note, pos) VALUES (?, ?, ?, ?, ?)", id(), code, m.name, m.note, i) }
            d.bring.forEachIndexed { i, b -> c.exec("INSERT INTO party_bring (id, party_code, name, pos) VALUES (?, ?, ?, ?)", id(), code, b.name, i) }
        }
        return PartyCreated(load(code), hostKey, hostGuest)
    }

    /** Menu pizzas and things to bring keep their id, so orders and "I bring it" survive an edit; what is left out goes. */
    fun update(code: String, key: String?, draft: PartyDraft): Party {
        host(code, key)
        val d = checked(draft)
        val old = load(code)
        val oldPizzas = old.menu.map { it.id }.toSet()
        val oldBring = old.bring.map { it.id }.toSet()
        db.tx { c ->
            c.exec("UPDATE parties SET title = ?, when_text = ?, note = ?, spare = ?, spare_baked = LEAST(spare_baked, ?) WHERE code = ?", d.title, d.whenText, d.note, d.spare, d.spare, code)
            val keptPizzas = d.menu.map { it.id }.filter { it in oldPizzas }.toSet()
            (oldPizzas - keptPizzas).forEach { c.exec("DELETE FROM party_pizzas WHERE id = ?", it) }
            d.menu.forEachIndexed { i, m ->
                if (m.id in keptPizzas) c.exec("UPDATE party_pizzas SET name = ?, note = ?, pos = ? WHERE id = ?", m.name, m.note, i, m.id)
                else c.exec("INSERT INTO party_pizzas (id, party_code, name, note, pos) VALUES (?, ?, ?, ?, ?)", id(), code, m.name, m.note, i)
            }
            val keptBring = d.bring.map { it.id }.filter { it in oldBring }.toSet()
            (oldBring - keptBring).forEach { c.exec("DELETE FROM party_bring WHERE id = ?", it) }
            d.bring.forEachIndexed { i, b ->
                if (b.id in keptBring) c.exec("UPDATE party_bring SET name = ?, pos = ? WHERE id = ?", b.name, i, b.id)
                else c.exec("INSERT INTO party_bring (id, party_code, name, pos) VALUES (?, ?, ?, ?)", id(), code, b.name, i)
            }
        }
        return load(code)
    }

    fun setStage(code: String, key: String?, stage: PartyStage): Party {
        host(code, key)
        db.update("UPDATE parties SET stage = ? WHERE code = ?", stage.name, code)
        return load(code)
    }

    fun delete(code: String, key: String?) {
        host(code, key)
        db.update("DELETE FROM parties WHERE code = ?", code)
    }

    /** A guest without a phone: the pizzaiolo writes them in and orders for them. */
    fun addGuest(code: String, key: String?, rawName: String): Party {
        host(code, key)
        insertGuest(load(code), name(rawName, "The name"), null)
        return load(code)
    }

    /** The pizzaiolo ticks a pizza as out of the oven (or takes the tick back). */
    fun setBaked(code: String, key: String?, r: BakedRequest): Party {
        host(code, key)
        val guestId = r.guestId
        if (guestId == null) {
            db.update("UPDATE parties SET spare_baked = ? WHERE code = ?", r.baked.coerceIn(0, 1), code)
        } else {
            val pizzaId = r.pizzaId ?: refuse(HttpStatusCode.BadRequest, "Which pizza?")
            val party = load(code)
            val order = party.guests.firstOrNull { it.id == guestId }?.orders?.firstOrNull { it.pizzaId == pizzaId }
                ?: refuse(HttpStatusCode.NotFound, "Nobody ordered that.")
            db.update("UPDATE party_orders SET baked = ? WHERE guest_id = ? AND pizza_id = ?", r.baked.coerceIn(0, order.count), guestId, pizzaId)
        }
        return load(code)
    }

    // ---- guests ----

    private fun insertGuest(party: Party, guestName: String, keyHash: String?): String {
        if (party.guests.size >= PartyRules.MAX_GUESTS) refuse(HttpStatusCode.Conflict, "This night is full.")
        if (party.guests.any { it.name.equals(guestName, ignoreCase = true) }) {
            refuse(HttpStatusCode.Conflict, "There is already a $guestName here. If that is you, tap your name; if not, add a letter.")
        }
        val guestId = id()
        db.update("INSERT INTO party_guests (id, party_code, name, key_hash, created_at) VALUES (?, ?, ?, ?, ?)", guestId, party.code, guestName, keyHash, now())
        return guestId
    }

    fun join(code: String, rawName: String): Joined {
        val party = load(code)
        if (party.stage != PartyStage.ORDERS) refuse(HttpStatusCode.Forbidden, CLOSED)
        val guestKey = key()
        val guestId = insertGuest(party, name(rawName), sha(guestKey))
        return Joined(guestId, guestKey, load(code))
    }

    /**
     * "I am Ania" from a browser that does not know it yet. Friends are trusted with the link,
     * so the name is enough; the guest's earlier key stops working. The pizzaiolo cannot be claimed.
     */
    fun claim(code: String, guestId: String): Joined {
        val party = load(code)
        val guest = party.guests.firstOrNull { it.id == guestId } ?: refuse(HttpStatusCode.NotFound, "That guest is not on the list.")
        if (guest.host) refuse(HttpStatusCode.Forbidden, "The pizzaiolo orders from the app.")
        val guestKey = key()
        db.update("UPDATE party_guests SET key_hash = ? WHERE id = ?", sha(guestKey), guestId)
        return Joined(guestId, guestKey, party)
    }

    /** A guest leaves, or the pizzaiolo takes somebody off the list. What they were bringing is free again. */
    fun removeGuest(code: String, key: String?, guestId: String): Party {
        val party = load(code)
        val w = who(code, key)
        val guest = party.guests.firstOrNull { it.id == guestId } ?: refuse(HttpStatusCode.NotFound, "That guest is not on the list.")
        if (guest.host) refuse(HttpStatusCode.BadRequest, "The pizzaiolo stays.")
        if (!w.host && w.guestId != guestId) refuse(HttpStatusCode.Forbidden, "Only the pizzaiolo can do that.")
        db.update("DELETE FROM party_guests WHERE id = ?", guestId)
        return load(code)
    }

    /** Replaces one guest's whole order. Guests order for themselves while orders are open; the pizzaiolo for anybody, any time. */
    fun order(code: String, key: String?, guestId: String, orders: List<GuestOrder>): Party {
        val party = load(code)
        val w = who(code, key)
        val guest = party.guests.firstOrNull { it.id == guestId } ?: refuse(HttpStatusCode.NotFound, "That guest is not on the list.")
        if (!w.host) {
            if (w.guestId != guestId) refuse(HttpStatusCode.Forbidden, "Tap your name first.")
            if (party.stage != PartyStage.ORDERS) refuse(HttpStatusCode.Forbidden, CLOSED)
        }
        val menu = party.menu.map { it.id }.toSet()
        // one line per pizza, last one wins
        val wanted = orders.filter { it.count > 0 }.associate { it.pizzaId to it.count }
        if (wanted.keys.any { it !in menu }) refuse(HttpStatusCode.BadRequest, "That pizza is not on the menu any more.")
        if (wanted.values.any { it < 0 } || wanted.values.sum() > PartyRules.MAX_PER_GUEST) {
            refuse(HttpStatusCode.BadRequest, "At most ${PartyRules.MAX_PER_GUEST} pizzas per person.")
        }
        val baked = guest.orders.associate { it.pizzaId to it.baked }
        db.tx { c ->
            c.exec("DELETE FROM party_orders WHERE guest_id = ?", guestId)
            wanted.forEach { (pizzaId, count) ->
                c.exec("INSERT INTO party_orders (guest_id, pizza_id, qty, baked) VALUES (?, ?, ?, ?)", guestId, pizzaId, count, minOf(baked[pizzaId] ?: 0, count))
            }
        }
        return load(code)
    }

    /** A guest takes a free item or gives their own back; the pizzaiolo hands anything to anybody. */
    fun bring(code: String, key: String?, itemId: String, guestId: String?): Party {
        val party = load(code)
        val w = who(code, key)
        val item = party.bring.firstOrNull { it.id == itemId } ?: refuse(HttpStatusCode.NotFound, "That is not on the list any more.")
        if (guestId != null && party.guests.none { it.id == guestId }) refuse(HttpStatusCode.NotFound, "That guest is not on the list.")
        if (!w.host) {
            val me = w.guestId ?: refuse(HttpStatusCode.Forbidden, "Tap your name first.")
            if (party.stage != PartyStage.ORDERS) refuse(HttpStatusCode.Forbidden, CLOSED)
            if (guestId != null && guestId != me) refuse(HttpStatusCode.Forbidden, "You can only sign yourself up.")
            if (item.guestId != null && item.guestId != me) {
                refuse(HttpStatusCode.Conflict, "${party.guestName(item.guestId) ?: "Somebody"} already brings that.")
            }
        }
        db.update("UPDATE party_bring SET guest_id = ? WHERE id = ?", guestId, itemId)
        return load(code)
    }

    companion object {
        /** No 0/o, 1/l/i: the code survives being read out or typed. */
        private const val CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"
        const val CODE_LENGTH = 10
        const val KEEP_MS = 60L * 24 * 3600 * 1000
        private const val CLOSED = "Orders are closed. Ask the pizzaiolo."
    }
}
