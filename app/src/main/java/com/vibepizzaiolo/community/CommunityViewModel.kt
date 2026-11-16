package com.vibepizzaiolo.community

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibepizzaiolo.BuildConfig
import com.vibepizzaiolo.settings.SettingsRepository
import com.vibepizzaiolo.share.ShareUtils
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Stage { LOADING, AUTH, QUIZ, HOME }
enum class Section(val label: String) { SWIPE("Swipe"), GALLERY("Gallery"), TABLE("Tavola"), ME("Me") }

data class DetailUi(val pizza: Pizza, val comments: List<Comment> = emptyList(), val loading: Boolean = true)

/** Posting: the photo, what it looks like, and what the user settles on. */
data class PostUi(
    val photo: Bitmap? = null,
    val sniffing: Boolean = true,
    val sniff: Sniff? = null,
    val draft: PizzaDraft,
    val posting: Boolean = false,
)

data class ChatUi(val invite: Invite, val messages: List<ChatMessage> = emptyList())

data class CommunityUi(
    val stage: Stage = Stage.LOADING,
    /** Everything stays on this phone (no server set, or it did not answer). */
    val offline: Boolean = true,
    /** A server address is set, so "offline" means it did not answer. */
    val serverSet: Boolean = false,
    val serverUrl: String = "",
    val me: Pizzaiolo? = null,
    val section: Section = Section.SWIPE,
    val deck: List<Pizza> = emptyList(),
    val deckLoading: Boolean = false,
    val filter: Filter = Filter(),
    val results: List<Pizza> = emptyList(),
    val searching: Boolean = false,
    val detail: DetailUi? = null,
    val post: PostUi? = null,
    val invites: List<Invite> = emptyList(),
    val chat: ChatUi? = null,
    val myPizzas: List<Pizza> = emptyList(),
    val editingCard: Boolean = false,
    /** The "camera or gallery?" question is up. */
    val askPhoto: Boolean = false,
    val busy: Boolean = false,
    /** One line to show and clear. */
    val message: String? = null,
)

class CommunityViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("community", Context.MODE_PRIVATE)
    private val local = LocalCommunityRepository(File(app.filesDir, "community"))
    private var remote: RemoteCommunityRepository? = null
    private var repo: CommunityRepository = local

    private val tokens = object : TokenStore {
        override var token: String?
            get() = prefs.getString("token", null)
            set(value) { prefs.edit().putString("token", value).apply() }
    }

    private val _ui = MutableStateFlow(CommunityUi())
    val ui: StateFlow<CommunityUi> = _ui.asStateFlow()

    private var started = false

    private fun serverUrl(): String = (prefs.getString("server_url", null) ?: BuildConfig.COMMUNITY_URL).trim().trimEnd('/')

    /** Called when the Community tab is first shown. */
    fun start() {
        if (started) return
        started = true
        connect()
    }

    /** Picks the server if one is set and answers, the phone otherwise, then finds out who is logged in. */
    fun connect() {
        _ui.update { it.copy(stage = Stage.LOADING) }
        viewModelScope.launch {
            val url = serverUrl()
            remote = if (url.isNotEmpty()) RemoteCommunityRepository(url, tokens) else null
            val r = remote
            repo = if (r != null && r.healthy()) r else local
            _ui.update { CommunityUi(offline = repo.offline, serverSet = r != null, serverUrl = url, stage = Stage.LOADING) }
            enter(runCatching { repo.me() }.getOrNull())
        }
    }

    fun setServerUrl(url: String) {
        val u = url.trim()
        if (u.isNotEmpty() && !u.startsWith("https://")) {
            say("The address must start with https://")
            return
        }
        prefs.edit().apply { if (u.isEmpty()) remove("server_url") else putString("server_url", u) }.apply()
        connect()
    }

    private fun enter(me: Pizzaiolo?) {
        _ui.update {
            it.copy(
                me = me, busy = false,
                stage = when {
                    me == null -> Stage.AUTH
                    me.card == null -> Stage.QUIZ
                    else -> Stage.HOME
                },
            )
        }
        if (me?.card != null) refreshAll()
    }

    /** Runs [block]; a problem becomes one line on screen. A silent server offers the phone instead. */
    private fun go(busy: Boolean = false, block: suspend () -> Unit): Job = viewModelScope.launch {
        if (busy) _ui.update { it.copy(busy = true) }
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServerUnreachable) {
            say("The server is not answering. Check your connection, or continue on this phone only from the Me tab.")
        } catch (e: CommunityException) {
            say(e.message ?: "Something went wrong.")
        } catch (e: Exception) {
            say("Something went wrong (${e.javaClass.simpleName}).")
        } finally {
            if (busy) _ui.update { it.copy(busy = false) }
        }
    }

    private fun say(text: String) = _ui.update { it.copy(message = text) }
    fun messageShown() = _ui.update { it.copy(message = null) }

    // ---- account ----
    fun signUp(email: String, password: String, name: String) = go(busy = true) { enter(repo.signUp(email, password, name)) }
    fun logIn(email: String, password: String) = go(busy = true) { enter(repo.logIn(email, password)) }

    fun logOut() = go {
        repo.logOut()
        _ui.update { CommunityUi(offline = it.offline, serverSet = it.serverSet, serverUrl = it.serverUrl, stage = Stage.AUTH) }
    }

    fun deleteAccount() = go(busy = true) {
        repo.deleteAccount()
        _ui.update { CommunityUi(offline = it.offline, serverSet = it.serverSet, serverUrl = it.serverUrl, stage = Stage.AUTH, message = "Your account and everything in it is deleted.") }
    }

    fun saveCard(card: StyleCard) = go(busy = true) {
        val me = repo.saveCard(card)
        _ui.update { it.copy(editingCard = false) }
        enter(me)
    }

    fun askPhoto(on: Boolean) = _ui.update { it.copy(askPhoto = on) }

    fun editCard(on: Boolean) = _ui.update { it.copy(editingCard = on) }

    // ---- browsing ----
    fun select(section: Section) {
        _ui.update { it.copy(section = section) }
        when (section) {
            Section.SWIPE -> if (_ui.value.deck.isEmpty()) refreshDeck()
            Section.GALLERY -> search()
            Section.TABLE -> refreshInvites()
            Section.ME -> refreshMine()
        }
    }

    private fun refreshAll() { refreshDeck(); search(); refreshInvites(); refreshMine() }

    fun refreshDeck() {
        _ui.update { it.copy(deckLoading = true) }
        go { try { val d = repo.feed(); _ui.update { it.copy(deck = d) } } finally { _ui.update { it.copy(deckLoading = false) } } }
    }

    private var searchJob: Job? = null
    fun setFilter(filter: Filter) {
        _ui.update { it.copy(filter = filter) }
        search()
    }

    private fun search() {
        searchJob?.cancel()
        _ui.update { it.copy(searching = true) }
        searchJob = go {
            kotlinx.coroutines.delay(250) // typing in the search field
            val found = repo.search(_ui.value.filter)
            _ui.update { it.copy(results = found, searching = false) }
        }
    }

    private fun refreshInvites() = go { val i = repo.invites(); _ui.update { it.copy(invites = i) } }
    private fun refreshMine() = go { val m = repo.mine(); _ui.update { it.copy(myPizzas = m) } }

    /** The top card left the deck. */
    fun swipe(pizza: Pizza, like: Boolean) {
        _ui.update { it.copy(deck = it.deck.filter { p -> p.id != pizza.id }) }
        go {
            repo.swipe(pizza.id, like)
            if (_ui.value.deck.isEmpty()) refreshDeck()
        }
    }

    // ---- one pizza ----
    fun open(pizza: Pizza) {
        _ui.update { it.copy(detail = DetailUi(pizza)) }
        go {
            val fresh = repo.pizza(pizza.id)
            val comments = repo.comments(pizza.id)
            _ui.update { if (it.detail?.pizza?.id == pizza.id) it.copy(detail = DetailUi(fresh, comments, loading = false)) else it }
        }
    }

    fun closeDetail() = _ui.update { it.copy(detail = null) }

    /** Like or pass from the detail view. */
    fun rate(like: Boolean) {
        val p = _ui.value.detail?.pizza ?: return
        go {
            repo.swipe(p.id, like)
            val fresh = repo.pizza(p.id)
            _ui.update { s ->
                s.copy(
                    detail = s.detail?.takeIf { it.pizza.id == p.id }?.copy(pizza = fresh) ?: s.detail,
                    deck = s.deck.filter { it.id != p.id },
                    results = s.results.map { if (it.id == p.id) fresh else it },
                )
            }
        }
    }

    fun comment(text: String) {
        val p = _ui.value.detail?.pizza ?: return
        go {
            val c = repo.comment(p.id, text)
            _ui.update { s -> s.copy(detail = s.detail?.takeIf { it.pizza.id == p.id }?.let { it.copy(comments = it.comments + c, pizza = it.pizza.copy(comments = it.pizza.comments + 1)) } ?: s.detail) }
        }
    }

    /** "Mangiamo insieme?" */
    fun invite() {
        val p = _ui.value.detail?.pizza ?: return
        go(busy = true) {
            val inv = repo.invite(p.id)
            val invites = repo.invites()
            _ui.update { s -> s.copy(invites = invites, detail = s.detail?.copy(pizza = s.detail.pizza.copy(invited = true))) }
            if (inv.status == InviteStatus.ACCEPTED) openChat(inv) else say("Invitation sent to ${p.author.name}. You will find the answer under Tavola.")
        }
    }

    fun report(reason: String) {
        val p = _ui.value.detail?.pizza ?: return
        go {
            repo.report(p.id, reason)
            _ui.update { s -> s.copy(detail = null, deck = s.deck.filter { it.id != p.id }, results = s.results.filter { it.id != p.id }, message = "Reported. Thank you.") }
        }
    }

    fun block() {
        val p = _ui.value.detail?.pizza ?: return
        go {
            repo.block(p.author.id)
            _ui.update { s ->
                s.copy(
                    detail = null, message = "${p.author.name} is blocked.",
                    deck = s.deck.filter { it.author.id != p.author.id }, results = s.results.filter { it.author.id != p.author.id },
                    invites = s.invites.filter { it.other.id != p.author.id },
                )
            }
        }
    }

    // ---- posting ----
    fun startPost(uri: Uri) {
        val card = _ui.value.me?.card ?: StyleCard()
        viewModelScope.launch {
            val app = getApplication<Application>()
            val settings = SettingsRepository.get(app)
            val s = settings.flow.first()
            val log = settings.bakeLog.first()
            val draft = PizzaDraft(
                type = PizzaType.MARGHERITA, style = card.style, base = if (card.base == Base.BOTH) Base.ROSSA else card.base,
                spice = card.spice, tempC = card.tempC, oven = card.oven,
                bakeSec = if (log.lastBakeMs > 0) (log.lastBakeMs / 1000).toInt() else s.bakeSec, hydration = s.hydration.toInt(),
            )
            _ui.update { it.copy(post = PostUi(draft = draft)) }
            val photo = withContext(Dispatchers.IO) { ShareUtils.loadPhoto(app, uri, maxSide = 1600) }
            if (photo == null) {
                _ui.update { it.copy(post = null, message = "Could not open that photo. Try another one.") }
                return@launch
            }
            _ui.update { it.copy(post = it.post?.copy(photo = photo)) }
            val data = withContext(Dispatchers.Default) { Photos.prepare(photo) }
            prepared = data
            val sniff = runCatching { repo.sniff(data) }.getOrNull()
            _ui.update { ui ->
                val post = ui.post ?: return@update ui
                val best = sniff?.suggestions?.firstOrNull()?.type
                ui.copy(
                    post = post.copy(
                        sniffing = false, sniff = sniff,
                        draft = post.draft.copy(type = best ?: post.draft.type, base = sniff?.base ?: best?.base ?: post.draft.base),
                    )
                )
            }
        }
    }

    private var prepared: PhotoData? = null

    fun editDraft(change: (PizzaDraft) -> PizzaDraft) = _ui.update { it.copy(post = it.post?.let { p -> p.copy(draft = change(p.draft)) }) }

    fun cancelPost() { prepared = null; _ui.update { it.copy(post = null) } }

    fun submitPost() {
        val post = _ui.value.post ?: return
        val data = prepared ?: return
        _ui.update { it.copy(post = post.copy(posting = true)) }
        go {
            try {
                val pizza = repo.post(data, post.draft)
                prepared = null
                _ui.update { it.copy(post = null, section = Section.ME, myPizzas = listOf(pizza) + it.myPizzas, message = "Your ${pizza.type.label} is in the gallery.") }
                search()
            } finally {
                _ui.update { it.copy(post = it.post?.copy(posting = false)) }
            }
        }
    }

    // ---- la tavola: invitations and chat ----
    fun openChat(invite: Invite) {
        _ui.update { it.copy(chat = ChatUi(invite)) }
        go { val m = repo.messages(invite.id); _ui.update { if (it.chat?.invite?.id == invite.id) it.copy(chat = it.chat.copy(messages = m)) else it } }
    }

    fun closeChat() { _ui.update { it.copy(chat = null) }; refreshInvites() }

    fun sendChat(text: String) {
        val inv = _ui.value.chat?.invite ?: return
        go {
            repo.send(inv.id, text)
            val m = repo.messages(inv.id)
            _ui.update { if (it.chat?.invite?.id == inv.id) it.copy(chat = it.chat.copy(messages = m)) else it }
        }
    }

    fun answer(invite: Invite, accept: Boolean) = go {
        val next = repo.answer(invite.id, accept)
        _ui.update { s -> s.copy(invites = s.invites.map { if (it.id == next.id) next else it }) }
        if (accept) openChat(next)
    }

    /** Continue on the phone when the server is set but silent. */
    fun workOffline() {
        repo = local
        _ui.update { CommunityUi(offline = true, serverSet = it.serverSet, serverUrl = it.serverUrl) }
        viewModelScope.launch { enter(runCatching { local.me() }.getOrNull()) }
    }

    /** Back button: closes the innermost open thing. false: nothing was open. */
    fun back(): Boolean {
        val s = _ui.value
        when {
            s.chat != null -> closeChat()
            s.post != null -> cancelPost()
            s.detail != null -> closeDetail()
            s.editingCard -> editCard(false)
            else -> return false
        }
        return true
    }
}
