package com.vibepizzaiolo.community

import kotlinx.serialization.Serializable

// The community's data, shared by the app and the server (both compile this file),
// so what one sends is exactly what the other reads.

/** Sauce: red, white, or a pizzaiolo who makes both. */
@Serializable
enum class Base(val label: String) { ROSSA("Rossa"), BIANCA("Bianca"), BOTH("Rossa e bianca") }

/** The dough and bake tradition. Not the topping: that is [PizzaType]. */
@Serializable
enum class PizzaStyle(val label: String, val hint: String) {
    NAPOLETANA("Napoletana", "soft, puffy cornicione, 60–90 s"),
    ROMANA("Romana tonda", "thin and crisp to the edge"),
    TEGLIA("Teglia / al taglio", "pan-baked, airy, by the slice"),
    PINSA("Pinsa", "oval, light, mixed flours"),
    NEW_YORK("New York", "large, foldable slice"),
    DETROIT("Detroit", "square, cheese-crisp edge"),
    CHICAGO("Chicago", "deep dish"),
    OTHER("Other", "your own way"),
}

@Serializable
enum class Oven(val label: String) { WOOD("wood"), GAS("gas"), ELECTRIC("electric"), HOME("home oven") }

/** The topping. [base] is the sauce it normally has. */
@Serializable
enum class PizzaType(val label: String, val base: Base) {
    MARGHERITA("Margherita", Base.ROSSA),
    MARINARA("Marinara", Base.ROSSA),
    DIAVOLA("Diavola", Base.ROSSA),
    CAPRICCIOSA("Capricciosa", Base.ROSSA),
    PROSCIUTTO_FUNGHI("Prosciutto e funghi", Base.ROSSA),
    NAPOLI("Napoli", Base.ROSSA),
    ORTOLANA("Ortolana", Base.ROSSA),
    QUATTRO_FORMAGGI("Quattro formaggi", Base.BIANCA),
    BIANCA("Bianca", Base.BIANCA),
    OTHER("Other", Base.ROSSA),
}

/** A pizzaiolo's style: how spicy, red or white, which tradition, how hot. */
@Serializable
data class StyleCard(
    /** 0 mild … 3 calabrese. */
    val spice: Int = 1,
    val base: Base = Base.ROSSA,
    val style: PizzaStyle = PizzaStyle.NAPOLETANA,
    val tempC: Int = 430,
    val oven: Oven = Oven.WOOD,
) {
    fun checked() = copy(spice = spice.coerceIn(0, MAX_SPICE), tempC = tempC.coerceIn(TEMP_MIN, TEMP_MAX))

    companion object {
        const val MAX_SPICE = 3
        const val TEMP_MIN = 200
        const val TEMP_MAX = 500
    }
}

@Serializable
data class Pizzaiolo(
    val id: String,
    val name: String,
    val card: StyleCard? = null,
    /** A sample pizzaiolo that lives in the app, not a person. */
    val demo: Boolean = false,
)

@Serializable
data class Pizza(
    val id: String,
    val author: Pizzaiolo,
    /** `https://…` from the server, `file:…` on the phone, or `paint:TYPE:seed` for a drawn sample. */
    val photo: String,
    val type: PizzaType,
    val style: PizzaStyle,
    val base: Base,
    val spice: Int,
    val tempC: Int,
    val oven: Oven,
    val bakeSec: Int? = null,
    val hydration: Int? = null,
    val caption: String = "",
    val createdAt: Long,
    val likes: Int = 0,
    val passes: Int = 0,
    val comments: Int = 0,
    val mine: Boolean = false,
    /** true liked, false passed, null not swiped yet. */
    val mySwipe: Boolean? = null,
    /** I already asked this pizzaiolo to eat together over this pizza. */
    val invited: Boolean = false,
) {
    /** Share of likes among all swipes, 0–100, or null before the first swipe. */
    val likePercent: Int? get() = if (likes + passes == 0) null else likes * 100 / (likes + passes)
}

/** What the user fills in when posting. The photo travels separately. */
@Serializable
data class PizzaDraft(
    val type: PizzaType,
    val style: PizzaStyle,
    val base: Base,
    val spice: Int,
    val tempC: Int,
    val oven: Oven,
    val bakeSec: Int? = null,
    val hydration: Int? = null,
    val caption: String = "",
    /** Server only: the uploaded photo this pizza uses. */
    val photoId: String? = null,
)

@Serializable
data class Comment(val id: String, val author: String, val text: String, val at: Long, val mine: Boolean = false)

@Serializable
enum class InviteStatus { PENDING, ACCEPTED, DECLINED }

/** "Mangiamo insieme?" over one pizza; a chat opens once it is accepted. */
@Serializable
data class Invite(
    val id: String,
    val pizzaId: String,
    val pizzaPhoto: String,
    val pizzaType: PizzaType,
    val other: Pizzaiolo,
    /** true: I asked. false: I was asked. */
    val outgoing: Boolean,
    val status: InviteStatus,
    val at: Long,
    val lastMessage: String? = null,
)

@Serializable
data class ChatMessage(val id: String, val mine: Boolean, val text: String, val at: Long)

@Serializable
data class Suggestion(val type: PizzaType, val confidence: Int)

/** What the photo looks like: up to three types, best first, and the sauce if it could be told. */
@Serializable
data class Sniff(
    val suggestions: List<Suggestion>,
    val base: Base? = null,
    val isPizza: Boolean = true,
    /** "colours" for the rough on-phone guess, "vision" for the server's model. */
    val source: String = "colours",
)

@Serializable
enum class Sort { NEWEST, BEST }

@Serializable
data class Filter(
    val type: PizzaType? = null,
    val style: PizzaStyle? = null,
    val base: Base? = null,
    val minSpice: Int = 0,
    val minTemp: Int = StyleCard.TEMP_MIN,
    val maxTemp: Int = StyleCard.TEMP_MAX,
    val query: String = "",
    val sort: Sort = Sort.NEWEST,
) {
    val isEmpty: Boolean
        get() = type == null && style == null && base == null && minSpice == 0 &&
            minTemp == StyleCard.TEMP_MIN && maxTemp == StyleCard.TEMP_MAX && query.isBlank()

    fun matches(p: Pizza): Boolean {
        if (type != null && p.type != type) return false
        if (style != null && p.style != style) return false
        if (base != null && p.base != base) return false
        if (p.spice < minSpice) return false
        if (p.tempC < minTemp || p.tempC > maxTemp) return false
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return listOf(p.type.label, p.style.label, p.base.label, p.caption, p.author.name).any { it.lowercase().contains(q) }
    }
}

/** What two pizzaioli share, for the invite: "you both bake Napoletana", "both above 430 °C"… */
fun StyleCard.inCommonWith(other: StyleCard): List<String> = buildList {
    if (style == other.style) add("you both bake ${style.label}")
    if (base == other.base || base == Base.BOTH || other.base == Base.BOTH) {
        add(if (base == other.base) "both ${base.label.lowercase()}" else "you meet on ${(if (base == Base.BOTH) other.base else base).label.lowercase()}")
    }
    if (spice == other.spice) add(if (spice == 0) "both mild" else "same heat on the tongue")
    if (kotlin.math.abs(tempC - other.tempC) <= 30) add("both around ${(tempC + other.tempC) / 2 / 10 * 10} °C")
    if (oven == other.oven) add("both ${oven.label}")
}

fun StyleCard.badge(): String = "${style.label} · ${base.label.lowercase()} · ${"🌶".repeat(spice).ifEmpty { "mild" }} · $tempC °C ${oven.label}"

// ---- what the server and the app say to each other ----

@Serializable data class SignUpRequest(val email: String, val password: String, val name: String)
@Serializable data class LogInRequest(val email: String, val password: String)
@Serializable data class Session(val token: String, val me: Pizzaiolo)
@Serializable data class SwipeRequest(val like: Boolean)
@Serializable data class TextRequest(val text: String)
@Serializable data class AnswerRequest(val accept: Boolean)
@Serializable data class PhotoUploaded(val photoId: String)
@Serializable data class ApiError(val error: String)
