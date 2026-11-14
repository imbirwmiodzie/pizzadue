package com.pizzadue.voice

/** Spelled-out numbers the way the speech models write them (lowercase, words separated by spaces). */
object NumberWords {
    private val enOnes = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val enTens = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun en(n: Int): String {
        require(n in 1..999)
        val parts = mutableListOf<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts += "${enOnes[h]} hundred"
        if (rest in 1..19) parts += enOnes[rest]
        else if (rest >= 20) parts += if (rest % 10 == 0) enTens[rest / 10] else "${enTens[rest / 10]} ${enOnes[rest % 10]}"
        return parts.joinToString(" ")
    }

    private val plOnes = listOf(
        "zero", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć", "dziesięć",
        "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście", "szesnaście", "siedemnaście",
        "osiemnaście", "dziewiętnaście",
    )
    private val plTens = listOf(
        "", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt", "sześćdziesiąt",
        "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt",
    )
    private val plHundreds = listOf("", "sto", "dwieście", "trzysta", "czterysta", "pięćset", "sześćset", "siedemset", "osiemset", "dziewięćset")

    /**
     * Polish number. [feminine] gives the form used with feminine nouns in the accusative
     * ("jedną minutę", "dwie kulki"); only 1 and 2 change.
     */
    fun pl(n: Int, feminine: Boolean = false): String {
        require(n in 1..999)
        val parts = mutableListOf<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts += plHundreds[h]
        if (rest in 1..19) parts += plOnes[rest]
        else if (rest >= 20) {
            parts += plTens[rest / 10]
            if (rest % 10 != 0) parts += plOnes[rest % 10]
        }
        if (feminine) {
            if (n == 1) return "jedną"
            if (parts.last() == "dwa") parts[parts.size - 1] = "dwie"
        }
        return parts.joinToString(" ")
    }

    /** Polish noun form: 1 -> [one], 2-4 (not 12-14) -> [few], otherwise [many]. */
    fun plForm(n: Int, one: String, few: String, many: String): String = when {
        n == 1 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }
}
