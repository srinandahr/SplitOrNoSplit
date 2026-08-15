package com.srinandahr.splitornosplit

import com.srinandahr.splitornosplit.data.Balance
import com.srinandahr.splitornosplit.data.involving
import com.srinandahr.splitornosplit.data.suggestSettlements
import com.srinandahr.splitornosplit.ui.balanceBreakdown
import com.srinandahr.splitornosplit.ui.settlementLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The settle-up suggestions. The server has no settle endpoint, so this is computed here —
 * and a wrong figure here is money someone actually hands over.
 */
class SettlementTest {

    private fun balance(id: Int, name: String, balance: Double) =
        Balance(memberId = id, memberName = name, paid = 0.0, spent = 0.0, balance = balance)

    @Test
    fun `two people settle with a single payment`() {
        val settlements = suggestSettlements(
            listOf(balance(1, "Alice", 250.0), balance(2, "Bob", -250.0)),
        )
        assertEquals(1, settlements.size)
        val s = settlements.single()
        assertEquals(2, s.fromId)
        assertEquals(1, s.toId)
        assertEquals(250.0, s.amount, 0.001)
    }

    @Test
    fun `an already settled group needs no payments`() {
        val settlements = suggestSettlements(
            listOf(balance(1, "Alice", 0.0), balance(2, "Bob", 0.0)),
        )
        assertTrue(settlements.isEmpty())
    }

    @Test
    fun `rounding dust does not produce a payment`() {
        // Floating point balances rarely land on exactly zero; a fraction of a paisa is settled.
        val settlements = suggestSettlements(
            listOf(balance(1, "Alice", 0.004), balance(2, "Bob", -0.004)),
        )
        assertTrue(settlements.isEmpty())
    }

    @Test
    fun `one debtor covering two creditors splits across both`() {
        val settlements = suggestSettlements(
            listOf(
                balance(1, "Alice", 100.0),
                balance(2, "Bob", 50.0),
                balance(3, "Carol", -150.0),
            ),
        )
        assertEquals(2, settlements.size)
        assertTrue(settlements.all { it.fromId == 3 })
        assertEquals(150.0, settlements.sumOf { it.amount }, 0.001)
        // The biggest creditor is paid first, so the largest debt clears soonest.
        assertEquals(1, settlements.first().toId)
        assertEquals(100.0, settlements.first().amount, 0.001)
    }

    @Test
    fun `every settlement plan fully clears the group`() {
        val balances = listOf(
            balance(1, "Alice", 340.75),
            balance(2, "Bob", -120.25),
            balance(3, "Carol", -95.50),
            balance(4, "Dave", -125.00),
        )
        val settlements = suggestSettlements(balances)

        // Applying the plan must leave everyone at zero, which is the only property that
        // actually matters — the exact pairing is an implementation detail.
        val net = balances.associate { it.memberId to it.balance }.toMutableMap()
        settlements.forEach { s ->
            net[s.fromId] = net.getValue(s.fromId) + s.amount
            net[s.toId] = net.getValue(s.toId) - s.amount
        }
        net.forEach { (id, remaining) ->
            assertTrue("member $id left at $remaining", abs(remaining) < 0.01)
        }
    }

    @Test
    fun `never suggests more payments than people`() {
        val balances = listOf(
            balance(1, "A", 500.0),
            balance(2, "B", -100.0),
            balance(3, "C", -100.0),
            balance(4, "D", -150.0),
            balance(5, "E", -150.0),
        )
        assertTrue(suggestSettlements(balances).size <= balances.size - 1)
    }

    @Test
    fun `filters to the payments the viewer is part of`() {
        val settlements = suggestSettlements(
            listOf(
                balance(1, "Alice", 100.0),
                balance(2, "Bob", -50.0),
                balance(3, "Carol", -50.0),
            ),
        )
        // Bob only cares about the leg he is on, not Carol's.
        assertEquals(1, settlements.involving(2).size)
        assertEquals(2, settlements.involving(1).size)
        assertEquals(settlements.size, settlements.involving(null).size)
    }

    @Test
    fun `the balance breakdown accounts for money that changed hands`() {
        // Bob paid 200, owes 100 of it, and has since handed over 150: 200 - 100 + 150 = 250.
        val bob = Balance(
            memberId = 2, memberName = "Bob",
            paid = 200.0, spent = 100.0, balance = 250.0, transferred = 150.0,
        )
        assertEquals(
            "paid ₹200.00 · share ₹100.00 · paid back ₹150.00",
            balanceBreakdown(bob, "INR"),
        )

        val alice = Balance(
            memberId = 1, memberName = "Alice",
            paid = 0.0, spent = 100.0, balance = -250.0, received = 150.0,
        )
        assertEquals(
            "paid ₹0.00 · share ₹100.00 · got back ₹150.00",
            balanceBreakdown(alice, "INR"),
        )

        // With no settlements the line stays as short as it was before settle-up existed.
        val plain = Balance(memberId = 3, memberName = "Carol", paid = 50.0, spent = 25.0, balance = 25.0)
        assertEquals("paid ₹50.00 · share ₹25.00", balanceBreakdown(plain, "INR"))
    }

    @Test
    fun `phrases each line from the viewer's side`() {
        val settlements = suggestSettlements(
            listOf(balance(1, "Alice", 250.0), balance(2, "Bob", -250.0)),
        )
        val s = settlements.single()
        assertEquals("You pay Alice ₹250.00", settlementLine(s, myMemberId = 2, currency = "INR"))
        assertEquals("Bob pays you ₹250.00", settlementLine(s, myMemberId = 1, currency = "INR"))
        assertEquals(
            "Bob pays Alice ₹250.00",
            settlementLine(s, myMemberId = 99, currency = "INR"),
        )
    }
}
