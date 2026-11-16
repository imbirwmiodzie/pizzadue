package com.vibepizzaiolo

import com.vibepizzaiolo.party.GuestOrder
import com.vibepizzaiolo.party.MenuPizza
import com.vibepizzaiolo.party.Party
import com.vibepizzaiolo.party.PartyGuest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartyQueueTest {
    private val menu = listOf(MenuPizza("m", "Margherita"), MenuPizza("d", "Diavola"))
    private fun party(spare: Int = 1, spareBaked: Int = 0, vararg guests: PartyGuest) =
        Party(code = "c", title = "Night", spare = spare, spareBaked = spareBaked, menu = menu, guests = guests.toList())

    private fun Party.names() = queue().map { "${it.guestName}:${it.pizzaName}" }

    @Test fun everybodyGetsTheirFirstPizzaBeforeAnybodyGetsASecond() {
        val p = party(
            1, 0,
            PartyGuest("h", "Marek", host = true, orders = listOf(GuestOrder("d", 1))),
            PartyGuest("a", "Ania", orders = listOf(GuestOrder("m", 2), GuestOrder("d", 1))),
            PartyGuest("o", "Ola"),
        )
        assertEquals(
            listOf("everyone:Test pizza", "Marek:Diavola", "Ania:Margherita", "Ania:Margherita", "Ania:Diavola"),
            p.names(),
        )
        assertEquals(4, p.ordered)
        assertEquals(5, p.balls)
        assertEquals(2, p.count("d"))
        assertEquals("Marek", p.hostName)
    }

    @Test fun bakedPizzasAreTickedAndTheNextOneIsTheFirstWithoutATick() {
        val p = party(
            1, 1,
            PartyGuest("a", "Ania", orders = listOf(GuestOrder("m", 2, baked = 1))),
            PartyGuest("o", "Ola", orders = listOf(GuestOrder("d", 1))),
        )
        assertEquals(listOf(true, true, false, false), p.queue().map { it.done })
        assertEquals("Ola", p.next()!!.guestName)
    }

    @Test fun withoutASpareBallThereIsNoTestPizza() {
        val p = party(0, 0, PartyGuest("a", "Ania", orders = listOf(GuestOrder("m", 1, baked = 1))))
        assertEquals(listOf("Ania:Margherita"), p.names())
        assertNull(p.next())
        assertEquals(1, p.balls)
    }
}
