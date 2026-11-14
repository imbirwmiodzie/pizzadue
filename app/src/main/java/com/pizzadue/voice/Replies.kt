package com.pizzadue.voice

import com.pizzadue.dough.DoughCalc
import com.pizzadue.dough.DoughResult

/** What the app says back. Kept free of command words so it never triggers itself. */
object Replies {
    fun duration(totalSec: Long, lang: Lang): String {
        val m = totalSec / 60
        val s = totalSec % 60
        val parts = mutableListOf<String>()
        if (lang == Lang.EN) {
            if (m > 0) parts += "$m ${if (m == 1L) "minute" else "minutes"}"
            if (s > 0 || m == 0L) parts += "$s ${if (s == 1L) "second" else "seconds"}"
        } else {
            if (m > 0) parts += "$m ${NumberWords.plForm(m.toInt(), "minuta", "minuty", "minut")}"
            if (s > 0 || m == 0L) parts += "$s ${NumberWords.plForm(s.toInt(), "sekunda", "sekundy", "sekund")}"
        }
        return parts.joinToString(" ")
    }

    fun bakeStarted(sec: Long, l: Lang) = if (l == Lang.EN) "Go! ${duration(sec, l)}." else "Jedziemy! ${duration(sec, l)}."
    fun preheatStarted(sec: Long, l: Lang) = if (l == Lang.EN) "Preheating, ${duration(sec, l)}." else "Rozgrzewam piec, ${duration(sec, l)}."
    fun stopped(sec: Long, l: Lang) = if (l == Lang.EN) "Done after ${duration(sec, l)}." else "Gotowe po ${duration(sec, l)}."
    fun silenced(l: Lang) = if (l == Lang.EN) "OK." else "Dobrze."
    fun nothingRunning(l: Lang) = if (l == Lang.EN) "Nothing is running." else "Nic nie jest włączone."
    fun left(sec: Long, l: Lang) = if (l == Lang.EN) "${duration(sec, l)} left." else "Zostało ${duration(sec, l)}."
    fun paused(l: Lang) = if (l == Lang.EN) "Paused." else "Wstrzymane."
    fun resumed(l: Lang) = if (l == Lang.EN) "Going on." else "Wznawiam."
    fun bakeSet(sec: Long, l: Lang) = if (l == Lang.EN) "Bake time ${duration(sec, l)}." else "Czas pieczenia ${duration(sec, l)}."
    fun bright(on: Boolean, l: Lang) = when {
        l == Lang.EN && on -> "Screen always on."
        l == Lang.EN -> "Normal screen."
        on -> "Ekran zawsze włączony."
        else -> "Zwykły ekran."
    }
    fun hydration(p: Int, l: Lang) = if (l == Lang.EN) "Hydration $p percent." else "Nawodnienie $p procent."
    fun ticks(on: Boolean, l: Lang) = when {
        l == Lang.EN -> if (on) "Ticks on." else "Ticks off."
        else -> if (on) "Tykanie włączone." else "Tykanie wyłączone."
    }

    fun dough(r: DoughResult, l: Lang): String {
        fun g(v: Double) = DoughCalc.formatGrams(v).let { if (l == Lang.PL) it.replace('.', ',') else it }
        return if (l == Lang.EN) "Flour ${g(r.flour)}, water ${g(r.water)}, salt ${g(r.salt)}, yeast ${g(r.yeast)} grams."
        else "Mąka ${g(r.flour)}, woda ${g(r.water)}, sól ${g(r.salt)}, drożdże ${g(r.yeast)} gramów."
    }
}
