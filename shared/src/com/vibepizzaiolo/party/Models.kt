package com.vibepizzaiolo.party

import kotlinx.serialization.Serializable

// A pizza night ("Serata"): the pizzaiolo sets a menu and shares one link, friends pick their
// pizzas in the browser, the app counts dough balls and keeps the baking queue.
// Shared by the app and the server, like the community's models.

/** ORDERS: friends choose. BAKING: orders are closed and phones go away. DONE: the night is over. */
@Serializable
enum class PartyStage { ORDERS, BAKING, DONE }

/** One pizza on tonight's menu. An empty [id] in a draft means "new". */
@Serializable
data class MenuPizza(val id: String = "", val name: String, val note: String = "")

/** [count] of one menu pizza for one guest; [baked] of them are already out of the oven. */
@Serializable
data class GuestOrder(val pizzaId: String, val count: Int, val baked: Int = 0)

@Serializable
data class PartyGuest(
    val id: String,
    val name: String,
    /** The pizzaiolo, who eats too. */
    val host: Boolean = false,
    val orders: List<GuestOrder> = emptyList(),
) {
    val pizzas: Int get() = orders.sumOf { it.count }
}

/** Something to bring ("mozzarella", "wine"); [guestId] is who took it, null while it is free. */
@Serializable
data class BringItem(val id: String = "", val name: String, val guestId: String? = null)

/** One pizza in the baking queue. A null [guestId] is the test pizza everybody shares. */
data class QueueItem(
    val guestId: String?,
    val guestName: String,
    val pizzaId: String?,
    val pizzaName: String,
    val done: Boolean,
)

@Serializable
data class Party(
    /** The secret in the link: whoever has it is invited. */
    val code: String,
    val title: String,
    /** Free text, as the pizzaiolo would say it: "Saturday 19:00". */
    val whenText: String = "",
    val note: String = "",
    val stage: PartyStage = PartyStage.ORDERS,
    /** Extra dough balls: the first is baked as a test pizza, the rest are a reserve. */
    val spare: Int = 1,
    /** 1 once the test pizza is out. */
    val spareBaked: Int = 0,
    val menu: List<MenuPizza> = emptyList(),
    val guests: List<PartyGuest> = emptyList(),
    val bring: List<BringItem> = emptyList(),
    val createdAt: Long = 0,
) {
    /** Pizzas ordered by everybody. */
    val ordered: Int get() = guests.sumOf { it.pizzas }

    /** Dough balls to make: one per ordered pizza plus the spare ones. */
    val balls: Int get() = ordered + spare

    val hostName: String get() = guests.firstOrNull { it.host }?.name.orEmpty()

    /** How many of one menu pizza are ordered in total. */
    fun count(pizzaId: String): Int = guests.sumOf { g -> g.orders.filter { it.pizzaId == pizzaId }.sumOf { it.count } }

    fun guestName(id: String?): String? = guests.firstOrNull { it.id == id }?.name

    /**
     * The order pizzas go into the oven: the test pizza, then rounds, so everybody has their first
     * pizza before anybody gets a second. Inside a round guests come in the order they joined.
     */
    fun queue(): List<QueueItem> {
        val out = mutableListOf<QueueItem>()
        if (spare > 0) out += QueueItem(null, TEST_GUEST, null, TEST_PIZZA, spareBaked > 0)
        val names = menu.associate { it.id to it.name }
        // each guest's pizzas one by one, in menu order; the first `baked` of a kind are done
        val perGuest = guests.map { g ->
            g to menu.flatMap { m ->
                val o = g.orders.firstOrNull { it.pizzaId == m.id } ?: return@flatMap emptyList()
                List(o.count) { i -> m.id to (i < o.baked) }
            }
        }
        val rounds = perGuest.maxOfOrNull { it.second.size } ?: 0
        for (round in 0 until rounds) {
            for ((g, units) in perGuest) {
                val (pizzaId, done) = units.getOrNull(round) ?: continue
                out += QueueItem(g.id, g.name, pizzaId, names[pizzaId].orEmpty(), done)
            }
        }
        return out
    }

    /** The next pizza for the oven, or null when everything is baked. */
    fun next(): QueueItem? = queue().firstOrNull { !it.done }

    companion object {
        const val TEST_GUEST = "everyone"
        const val TEST_PIZZA = "Test pizza"
    }
}

/** What the pizzaiolo fills in. [hostName] only matters when the night is created. */
@Serializable
data class PartyDraft(
    val title: String,
    val hostName: String = "",
    val whenText: String = "",
    val note: String = "",
    val spare: Int = 1,
    val menu: List<MenuPizza> = emptyList(),
    val bring: List<BringItem> = emptyList(),
)

/** [hostKey] is the pizzaiolo's secret for this night: it stays on their phone. */
@Serializable
data class PartyCreated(val party: Party, val hostKey: String, val hostGuestId: String)

/** A night as one caller sees it: [me] is their guest id when the server knows their key. */
@Serializable
data class PartyView(val party: Party, val me: String? = null, val host: Boolean = false)

@Serializable
data class JoinRequest(val name: String)

/** "I am this guest" from a browser that forgot: the guest gets a fresh key. */
@Serializable
data class ClaimRequest(val guestId: String)

@Serializable
data class Joined(val guestId: String, val key: String, val party: Party)

@Serializable
data class OrderRequest(val orders: List<GuestOrder>)

/** Who brings an item; null frees it. */
@Serializable
data class BringRequest(val guestId: String? = null)

@Serializable
data class StageRequest(val stage: PartyStage)

/** How many of a guest's pizza are out of the oven; a null [guestId] is the test pizza. */
@Serializable
data class BakedRequest(val guestId: String? = null, val pizzaId: String? = null, val baked: Int)

object PartyRules {
    const val MAX_MENU = 12
    const val MAX_GUESTS = 40
    const val MAX_BRING = 40
    /** Pizzas one guest can order. */
    const val MAX_PER_GUEST = 6
    const val MAX_SPARE = 10
    const val TITLE = 60
    const val NAME = 30
    const val ITEM = 40
    const val PIZZA_NOTE = 120
    const val WHEN = 60
    const val NOTE = 300

    /** Pizzas to start a menu from. */
    val MENU_IDEAS = listOf(
        MenuPizza(name = "Margherita", note = "tomato, mozzarella, basil"),
        MenuPizza(name = "Marinara", note = "tomato, garlic, oregano"),
        MenuPizza(name = "Diavola", note = "tomato, mozzarella, spicy salami"),
        MenuPizza(name = "Capricciosa", note = "ham, mushrooms, artichokes, olives"),
        MenuPizza(name = "Prosciutto e funghi", note = "ham and mushrooms"),
        MenuPizza(name = "Quattro formaggi", note = "four cheeses, no tomato"),
        MenuPizza(name = "Ortolana", note = "grilled vegetables"),
        MenuPizza(name = "Tonno e cipolla", note = "tomato, mozzarella, tuna, red onion"),
        MenuPizza(name = "Nutella", note = "the dessert pizza"),
    )

    /** What friends usually bring to a pizza night. */
    val BRING_IDEAS = listOf(
        "Mozzarella", "Tomato passata", "Basil", "Olive oil", "Salami", "Mushrooms",
        "Olives", "Beer", "Wine", "Spritz", "Limoncello", "Tiramisù",
    )
}
