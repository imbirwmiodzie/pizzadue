package com.vibepizzaiolo.party

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vibepizzaiolo.BuildConfig
import com.vibepizzaiolo.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PartyUi(
    /** Empty: no server is set, and a night needs one for its link. */
    val serverUrl: String = "",
    /** The stored night is being fetched for the first time. */
    val loading: Boolean = false,
    val party: Party? = null,
    val link: String = "",
    /** The form on top: a new night, or the open one being edited. */
    val form: PartyDraft? = null,
    val editing: Boolean = false,
    /** The guest whose order is open in a dialog. */
    val orderFor: String? = null,
    val busy: Boolean = false,
    /** One line to show and clear. */
    val message: String? = null,
)

/**
 * The Serata tab: the pizzaiolo's side of a pizza night. One night at a time; its code and
 * host key stay on this phone, friends use the link.
 */
class PartyViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("party", Context.MODE_PRIVATE)
    // the same server as the Pizzaioli tab, set there or here
    private val serverPrefs = app.getSharedPreferences("community", Context.MODE_PRIVATE)
    private val settings = SettingsRepository.get(app)

    private val _ui = MutableStateFlow(PartyUi())
    val ui: StateFlow<PartyUi> = _ui.asStateFlow()

    private var client: PartyClient? = null
    private var started = false

    private val code: String? get() = prefs.getString("code", null)
    private val hostKey: String? get() = prefs.getString("host_key", null)
    val hostGuestId: String? get() = prefs.getString("host_guest", null)
    private fun serverUrl(): String = (serverPrefs.getString("server_url", null) ?: BuildConfig.COMMUNITY_URL).trim().trimEnd('/')

    /** Called when the app opens: a night in progress is fetched, so the timer can show who is next. */
    fun start() {
        if (started) return
        started = true
        connect()
        // every pizza the timer sees out of the oven ticks the next one in the queue
        viewModelScope.launch {
            var seen = -1
            settings.bakeLog.collect { log ->
                if (seen >= 0 && log.count > seen && _ui.value.party?.stage == PartyStage.BAKING) bakedNext()
                seen = log.count
            }
        }
    }

    private fun connect() {
        val url = serverUrl()
        client = if (url.isEmpty()) null else PartyClient(url)
        val c = code
        _ui.update { PartyUi(serverUrl = url, loading = client != null && c != null, link = if (c != null) client?.link(c).orEmpty() else "") }
        if (client != null && c != null) refresh()
    }

    fun setServerUrl(url: String) {
        val u = url.trim()
        if (u.isNotEmpty() && !u.startsWith("https://")) {
            say("The address must start with https://")
            return
        }
        serverPrefs.edit().apply { if (u.isEmpty()) remove("server_url") else putString("server_url", u) }.apply()
        connect()
    }

    private fun say(text: String) = _ui.update { it.copy(message = text, busy = false) }
    fun messageShown() = _ui.update { it.copy(message = null) }

    /** Runs [block] with the open night; what the server refuses becomes one line on screen. */
    private fun go(busy: Boolean = false, block: suspend (PartyClient, String, String) -> Unit): Job = viewModelScope.launch {
        val c = client ?: return@launch
        val night = code ?: return@launch
        val key = hostKey ?: return@launch
        if (busy) _ui.update { it.copy(busy = true) }
        try {
            block(c, night, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PartyException) {
            say(e.message ?: "Something went wrong.")
        } finally {
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun show(party: Party) = _ui.update { it.copy(party = party, loading = false) }

    /** Fetches the night again; friends order from their browsers at any moment. Quiet when the server is away. */
    fun refresh() {
        val c = client ?: return
        val night = code ?: return
        viewModelScope.launch {
            try {
                show(c.view(night, hostKey).party)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PartyException) {
                if (e.status == 404) {
                    forget()
                    say("That pizza night is gone from the server.")
                } else {
                    _ui.update { it.copy(loading = false) }
                    if (_ui.value.party == null) say(e.message ?: "Something went wrong.")
                }
            }
        }
    }

    private fun forget() {
        prefs.edit().remove("code").remove("host_key").remove("host_guest").apply()
        _ui.update { it.copy(party = null, link = "", form = null, orderFor = null, loading = false) }
    }

    // ---- the form: a new night, or changes to the open one ----

    fun newNight() = _ui.update {
        it.copy(
            editing = false,
            form = PartyDraft(
                title = "Pizza night", hostName = prefs.getString("host_name", "").orEmpty(),
                menu = PartyRules.MENU_IDEAS.take(2), bring = emptyList(),
            ),
        )
    }

    fun editNight() {
        val p = _ui.value.party ?: return
        _ui.update { it.copy(editing = true, form = PartyDraft(p.title, p.hostName, p.whenText, p.note, p.spare, p.menu, p.bring)) }
    }

    fun editForm(transform: (PartyDraft) -> PartyDraft) = _ui.update { it.copy(form = it.form?.let(transform)) }
    fun closeForm() = _ui.update { it.copy(form = null) }

    fun submitForm() {
        val draft = _ui.value.form ?: return
        val c = client ?: return
        if (_ui.value.editing) {
            go(busy = true) { cl, night, key ->
                show(cl.update(night, key, draft))
                closeForm()
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val made = c.create(draft)
                prefs.edit()
                    .putString("code", made.party.code).putString("host_key", made.hostKey)
                    .putString("host_guest", made.hostGuestId).putString("host_name", made.party.hostName).apply()
                _ui.update { it.copy(party = made.party, link = c.link(made.party.code), form = null, busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PartyException) {
                say(e.message ?: "Something went wrong.")
            }
        }
    }

    // ---- the night ----

    fun setStage(stage: PartyStage) = go(busy = true) { c, night, key -> show(c.setStage(night, key, stage)) }

    /** Takes the night off the server (the link stops working) and off this phone. */
    fun deleteNight() = go(busy = true) { c, night, key ->
        c.delete(night, key)
        forget()
    }

    fun openOrder(guestId: String?) = _ui.update { it.copy(orderFor = guestId) }

    /** One more or one fewer of a pizza for a guest; the pizzaiolo can do this for anybody. */
    fun changeOrder(guestId: String, pizzaId: String, by: Int) {
        val guest = _ui.value.party?.guests?.firstOrNull { it.id == guestId } ?: return
        val counts = guest.orders.associate { it.pizzaId to it.count }.toMutableMap()
        val next = (counts[pizzaId] ?: 0) + by
        if (next < 0 || (by > 0 && counts.values.sum() >= PartyRules.MAX_PER_GUEST)) return
        counts[pizzaId] = next
        go { c, night, key -> show(c.order(night, key, guestId, counts.map { GuestOrder(it.key, it.value) })) }
    }

    fun addGuest(name: String) = go(busy = true) { c, night, key ->
        val before = _ui.value.party?.guests.orEmpty().map { it.id }.toSet()
        val party = c.addGuest(night, key, name)
        show(party)
        // straight to their order: that is why they were written in
        party.guests.firstOrNull { it.id !in before }?.let { g -> openOrder(g.id) }
    }

    fun removeGuest(guestId: String) = go(busy = true) { c, night, key ->
        show(c.removeGuest(night, key, guestId))
        openOrder(null)
    }

    fun assign(itemId: String, guestId: String?) = go { c, night, key -> show(c.bring(night, key, itemId, guestId)) }

    /** Ticks a pizza in the queue as baked, or takes the tick back. */
    fun toggle(item: QueueItem) {
        val party = _ui.value.party ?: return
        val request = if (item.guestId == null) {
            BakedRequest(baked = if (item.done) 0 else 1)
        } else {
            val baked = party.guests.firstOrNull { it.id == item.guestId }?.orders?.firstOrNull { it.pizzaId == item.pizzaId }?.baked ?: return
            BakedRequest(item.guestId, item.pizzaId, baked + if (item.done) -1 else 1)
        }
        go { c, night, key -> show(c.setBaked(night, key, request)) }
    }

    /** The next pizza in the queue is out of the oven. */
    fun bakedNext() {
        val next = _ui.value.party?.next() ?: return
        toggle(next)
    }

    /** Sets the Dough tab to this night's number of balls. */
    fun useBalls() {
        val balls = _ui.value.party?.balls ?: return
        viewModelScope.launch {
            settings.update { it.copy(balls = balls) }
            say("Dough is set to $balls balls.")
        }
    }
}
