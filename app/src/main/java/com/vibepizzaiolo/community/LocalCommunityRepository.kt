package com.vibepizzaiolo.community

import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocalAccount(val id: String, val email: String, val name: String, val salt: String, val hash: String, val card: StyleCard? = null)

/** Everything the offline community remembers, saved as one JSON file. */
@Serializable
data class LocalState(
    val account: LocalAccount? = null,
    val loggedIn: Boolean = false,
    val posts: List<Pizza> = emptyList(),
    val swipes: Map<String, Boolean> = emptyMap(),
    val comments: Map<String, List<Comment>> = emptyMap(),
    val invites: List<Invite> = emptyList(),
    val messages: Map<String, List<ChatMessage>> = emptyMap(),
    val blocked: Set<String> = emptySet(),
    val reported: Set<String> = emptySet(),
)

/**
 * The community without a server: your account, your pizzas and your swipes live in [dir]
 * on this phone, and the other pizzaioli are the samples from [DemoData].
 */
class LocalCommunityRepository(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : CommunityRepository {
    override val offline = true

    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = File(dir, "community.json")
    private val lock = Mutex()
    private var cached: LocalState? = null

    private fun load(): LocalState = cached ?: run {
        val s = runCatching { json.decodeFromString<LocalState>(file.readText()) }.getOrElse { LocalState() }
        cached = s
        s
    }

    private fun save(s: LocalState) {
        dir.mkdirs()
        val tmp = File(dir, "community.json.tmp")
        tmp.writeText(json.encodeToString(LocalState.serializer(), s))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
        cached = s
    }

    private suspend fun <T> read(block: (LocalState) -> T): T = withContext(Dispatchers.IO) { lock.withLock { block(load()) } }

    /** Runs [block] on the state and saves what it returns alongside the result. */
    private suspend fun <T> write(block: (LocalState) -> Pair<LocalState, T>): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val (next, result) = block(load())
            save(next)
            result
        }
    }

    private fun LocalState.user(): LocalAccount =
        account?.takeIf { loggedIn } ?: throw CommunityException("Log in first.")

    private fun LocalAccount.pizzaiolo() = Pizzaiolo(id, name, card)

    // ---- account ----
    override suspend fun me(): Pizzaiolo? = read { s -> s.account?.takeIf { s.loggedIn }?.pizzaiolo() }

    override suspend fun signUp(email: String, password: String, name: String): Pizzaiolo {
        val e = Rules.email(email); val pw = Rules.password(password); val n = Rules.name(name)
        return write { s ->
            s.account?.let { throw CommunityException("This phone already has an offline account (${it.email}). Log in with it.") }
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val acc = LocalAccount(UUID.randomUUID().toString(), e, n, b64(salt), hash(pw, salt))
            s.copy(account = acc, loggedIn = true) to acc.pizzaiolo()
        }
    }

    override suspend fun logIn(email: String, password: String): Pizzaiolo {
        val e = email.trim().lowercase()
        return write { s ->
            val acc = s.account
            if (acc == null || acc.email != e || hash(password, Base64.getDecoder().decode(acc.salt)) != acc.hash) {
                throw CommunityException("Wrong email or password.")
            }
            s.copy(loggedIn = true) to acc.pizzaiolo()
        }
    }

    override suspend fun logOut() = write { s -> s.copy(loggedIn = false) to Unit }

    override suspend fun deleteAccount() = write { s ->
        s.user()
        File(dir, "photos").deleteRecursively()
        LocalState() to Unit
    }

    override suspend fun saveCard(card: StyleCard): Pizzaiolo = write { s ->
        val acc = s.user().copy(card = card.checked())
        s.copy(account = acc, posts = s.posts.map { it.copy(author = acc.pizzaiolo()) }) to acc.pizzaiolo()
    }

    // ---- pizzas ----
    override suspend fun sniff(photo: PhotoData): Sniff =
        withContext(Dispatchers.Default) { ColorSniffer.sniff(photo.pixels, photo.width, photo.height) }

    override suspend fun post(photo: PhotoData, draft: PizzaDraft): Pizza = write { s ->
        val acc = s.user()
        val id = UUID.randomUUID().toString()
        val photos = File(dir, "photos").apply { mkdirs() }
        val f = File(photos, "$id.jpg").apply { writeBytes(photo.jpeg) }
        val pizza = Pizza(
            id = id, author = acc.pizzaiolo(), photo = "file:${f.absolutePath}",
            type = draft.type, style = draft.style, base = draft.base,
            spice = draft.spice.coerceIn(0, StyleCard.MAX_SPICE), tempC = draft.tempC.coerceIn(StyleCard.TEMP_MIN, StyleCard.TEMP_MAX),
            oven = draft.oven, bakeSec = draft.bakeSec, hydration = draft.hydration,
            caption = draft.caption.trim().take(Rules.MAX_CAPTION), createdAt = now(), mine = true,
        )
        s.copy(posts = listOf(pizza) + s.posts) to pizza
    }

    /** Samples and my own posts, with my swipes, comments and invites folded in. */
    private fun all(s: LocalState): List<Pizza> {
        val demo = DemoData.pizzas(now()).filter { it.author.id !in s.blocked && it.id !in s.reported }
        return (s.posts + demo).map { p ->
            val swipe = s.swipes[p.id]
            p.copy(
                likes = p.likes + if (swipe == true) 1 else 0,
                passes = p.passes + if (swipe == false) 1 else 0,
                comments = DemoData.comments(p.id).size + (s.comments[p.id]?.size ?: 0),
                mySwipe = swipe,
                invited = s.invites.any { it.pizzaId == p.id },
            )
        }
    }

    override suspend fun feed(): List<Pizza> = read { s ->
        s.user()
        all(s).filter { !it.mine && it.mySwipe == null }
    }

    override suspend fun search(filter: Filter): List<Pizza> = read { s ->
        s.user()
        val found = all(s).filter(filter::matches)
        when (filter.sort) {
            Sort.NEWEST -> found.sortedByDescending { it.createdAt }
            // a pizza nobody has swiped yet counts as half liked: above the passed-over, below the loved
            Sort.BEST -> found.sortedWith(compareByDescending<Pizza> { it.likePercent ?: 50 }.thenByDescending { it.likes })
        }
    }

    override suspend fun mine(): List<Pizza> = read { s -> s.user(); all(s).filter { it.mine } }

    override suspend fun pizza(id: String): Pizza = read { s -> find(s, id) }

    private fun find(s: LocalState, id: String) = all(s).firstOrNull { it.id == id } ?: throw CommunityException("That pizza is gone.")

    override suspend fun swipe(pizzaId: String, like: Boolean) = write { s ->
        s.user()
        if (find(s, pizzaId).mine) throw CommunityException("You cannot rate your own pizza.")
        s.copy(swipes = s.swipes + (pizzaId to like)) to Unit
    }

    // ---- comments ----
    override suspend fun comments(pizzaId: String): List<Comment> = read { s ->
        (DemoData.comments(pizzaId) + s.comments[pizzaId].orEmpty()).sortedBy { it.at }
    }

    override suspend fun comment(pizzaId: String, text: String): Comment {
        val t = Rules.text(text)
        return write { s ->
            val acc = s.user()
            find(s, pizzaId)
            val c = Comment(UUID.randomUUID().toString(), acc.name, t, now(), mine = true)
            s.copy(comments = s.comments + (pizzaId to s.comments[pizzaId].orEmpty() + c)) to c
        }
    }

    // ---- let's eat together ----
    override suspend fun invite(pizzaId: String): Invite = write { s ->
        s.user()
        val p = find(s, pizzaId)
        if (p.mine) throw CommunityException("That is your own pizza.")
        s.invites.firstOrNull { it.pizzaId == pizzaId }?.let { return@write s to it }
        // the samples always say yes, so the whole flow can be tried offline
        val inv = Invite(
            id = UUID.randomUUID().toString(), pizzaId = p.id, pizzaPhoto = p.photo, pizzaType = p.type, other = p.author,
            outgoing = true, status = InviteStatus.ACCEPTED, at = now(), lastMessage = DemoData.YES,
        )
        val hello = ChatMessage(UUID.randomUUID().toString(), mine = false, text = DemoData.YES, at = now())
        s.copy(invites = listOf(inv) + s.invites, messages = s.messages + (inv.id to listOf(hello))) to inv
    }

    override suspend fun invites(): List<Invite> = read { s -> s.user(); s.invites }

    override suspend fun answer(inviteId: String, accept: Boolean): Invite = write { s ->
        s.user()
        val inv = s.invites.firstOrNull { it.id == inviteId } ?: throw CommunityException("That invitation is gone.")
        if (inv.outgoing) throw CommunityException("Only the invited pizzaiolo can answer.")
        val next = inv.copy(status = if (accept) InviteStatus.ACCEPTED else InviteStatus.DECLINED)
        s.copy(invites = s.invites.map { if (it.id == inviteId) next else it }) to next
    }

    override suspend fun messages(inviteId: String): List<ChatMessage> = read { s -> s.messages[inviteId].orEmpty() }

    override suspend fun send(inviteId: String, text: String): ChatMessage {
        val t = Rules.text(text)
        return write { s ->
            s.user()
            val inv = s.invites.firstOrNull { it.id == inviteId } ?: throw CommunityException("That invitation is gone.")
            if (inv.status != InviteStatus.ACCEPTED) throw CommunityException("You can chat once the invitation is accepted.")
            val mine = ChatMessage(UUID.randomUUID().toString(), mine = true, text = t, at = now())
            val before = s.messages[inviteId].orEmpty()
            // a sample pizzaiolo answers once more, saying plainly what it is
            val reply = if (inv.other.demo && before.none { it.text == DemoData.SAMPLE_NOTE }) {
                listOf(ChatMessage(UUID.randomUUID().toString(), mine = false, text = DemoData.SAMPLE_NOTE, at = now() + 1))
            } else emptyList()
            s.copy(
                messages = s.messages + (inviteId to before + mine + reply),
                invites = s.invites.map { if (it.id == inviteId) it.copy(lastMessage = (reply.lastOrNull() ?: mine).text) else it },
            ) to mine
        }
    }

    // ---- safety ----
    override suspend fun report(pizzaId: String, reason: String) = write { s -> s.user(); s.copy(reported = s.reported + pizzaId) to Unit }

    override suspend fun block(pizzaioloId: String) = write { s ->
        s.user()
        s.copy(blocked = s.blocked + pizzaioloId, invites = s.invites.filter { it.other.id != pizzaioloId }) to Unit
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun hash(password: String, salt: ByteArray): String =
        b64(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, 60_000, 256)).encoded)
}

/** The sample pizzaioli and their drawn pizzas. */
object DemoData {
    const val YES = "Volentieri! Dimmi quando, I bring the dough. 🍕"
    const val SAMPLE_NOTE = "(I am a sample pizzaiolo living in your phone, so my answers end here. Real ones reply once the community server is connected.)"

    private fun who(id: String, name: String, spice: Int, base: Base, style: PizzaStyle, temp: Int, oven: Oven) =
        Pizzaiolo("demo-$id", name, StyleCard(spice, base, style, temp, oven), demo = true)

    val pizzaioli = listOf(
        who("gennaro", "Gennaro", 1, Base.ROSSA, PizzaStyle.NAPOLETANA, 470, Oven.WOOD),
        who("kasia", "Kasia", 3, Base.ROSSA, PizzaStyle.NAPOLETANA, 450, Oven.GAS),
        who("livia", "Livia", 0, Base.BIANCA, PizzaStyle.ROMANA, 330, Oven.ELECTRIC),
        who("marek", "Marek", 2, Base.BOTH, PizzaStyle.TEGLIA, 270, Oven.HOME),
        who("sal", "Sal", 2, Base.ROSSA, PizzaStyle.NEW_YORK, 290, Oven.GAS),
        who("bruna", "Bruna", 1, Base.BIANCA, PizzaStyle.PINSA, 300, Oven.ELECTRIC),
    )

    private data class Sample(val who: Int, val type: PizzaType, val spice: Int, val caption: String, val likes: Int, val passes: Int, val hoursAgo: Int)

    private val samples = listOf(
        Sample(0, PizzaType.MARGHERITA, 0, "San Marzano, fior di latte, 70 seconds.", 48, 3, 2),
        Sample(1, PizzaType.DIAVOLA, 3, "Spianata calabra and a lot of 'nduja.", 37, 9, 5),
        Sample(2, PizzaType.BIANCA, 0, "Rosemary, oil, salt. Nothing to hide behind.", 21, 6, 8),
        Sample(3, PizzaType.CAPRICCIOSA, 1, "Pan pizza for the whole staircase.", 19, 11, 13),
        Sample(4, PizzaType.PROSCIUTTO_FUNGHI, 1, "18 inches, fold it.", 25, 8, 20),
        Sample(5, PizzaType.QUATTRO_FORMAGGI, 0, "Gorgonzola first, honey after.", 33, 5, 26),
        Sample(0, PizzaType.MARINARA, 1, "No cheese, no excuses.", 29, 7, 31),
        Sample(1, PizzaType.NAPOLI, 2, "Anchovies from Cetara.", 17, 12, 40),
        Sample(3, PizzaType.ORTOLANA, 0, "Courgettes from the allotment.", 22, 4, 52),
        Sample(4, PizzaType.DIAVOLA, 2, "Cup-and-char pepperoni.", 41, 6, 60),
        Sample(2, PizzaType.QUATTRO_FORMAGGI, 0, "Thin, crisp, loud.", 15, 5, 75),
        Sample(5, PizzaType.MARGHERITA, 1, "Pinsa, 80% hydration, three days cold.", 27, 3, 90),
        Sample(0, PizzaType.CAPRICCIOSA, 1, "Sunday.", 30, 9, 110),
        Sample(1, PizzaType.MARGHERITA, 3, "Margherita, but with chili oil.", 24, 10, 130),
    )

    fun pizzas(now: Long): List<Pizza> = samples.mapIndexed { i, s ->
        val w = pizzaioli[s.who]
        val card = w.card!!
        Pizza(
            id = "demo-pizza-$i", author = w, photo = "paint:${s.type.name}:${i + 1}",
            type = s.type, style = card.style, base = s.type.base, spice = s.spice, tempC = card.tempC, oven = card.oven,
            bakeSec = if (card.style == PizzaStyle.NAPOLETANA) 60 + i * 3 else 300 + i * 20,
            hydration = 60 + (i * 7) % 22, caption = s.caption,
            createdAt = now - s.hoursAgo * 3_600_000L, likes = s.likes, passes = s.passes,
        )
    }

    private val lines = listOf(
        "Che cornicione!", "I would eat two.", "A little more basil next time?", "That leoparding is perfect.",
        "What flour is this?", "Bella!", "The base looks wonderfully thin.",
    )

    fun comments(pizzaId: String): List<Comment> {
        val i = pizzaId.removePrefix("demo-pizza-").toIntOrNull() ?: return emptyList()
        return List(i % 3) { k ->
            Comment("demo-comment-$i-$k", pizzaioli[(i + k + 1) % pizzaioli.size].name, lines[(i * 2 + k) % lines.size], at = 1_700_000_000_000L + (i * 10 + k) * 60_000L)
        }
    }
}
