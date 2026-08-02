package com.srinandahr.splitornosplit

import com.srinandahr.splitornosplit.split.generatePrivateCode
import com.srinandahr.splitornosplit.split.slugify
import com.srinandahr.splitornosplit.split.uniqueProjectId
import com.srinandahr.splitornosplit.ui.balanceLabel
import com.srinandahr.splitornosplit.ui.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SlugTest {

    @Test
    fun `strips characters project ids may not contain`() {
        assertEquals("flatmates", slugify("Flatmates"))
        assertEquals("goa-trip-2026", slugify("Goa Trip 2026"))
        assertEquals("bob-alice", slugify("  Bob & Alice  "))
    }

    @Test
    fun `never produces an empty id`() {
        assertEquals("group", slugify("!!!"))
        assertEquals("group", slugify(""))
    }

    @Test
    fun `project ids get a random suffix so two groups of the same name do not collide`() {
        val a = uniqueProjectId("Flatmates")
        val b = uniqueProjectId("Flatmates")
        assertTrue(a.startsWith("flatmates-"))
        assertNotEquals(a, b)
    }

    @Test
    fun `private codes are the requested length and use an unambiguous alphabet`() {
        val code = generatePrivateCode(20)
        assertEquals(20, code.length)
        // 'l', 'I', 'O', '0', '1' are excluded so codes survive being read aloud or retyped.
        assertTrue(code.none { it in "lIO01" })
    }
}

class FormatTest {

    @Before
    fun fixLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `formats amounts with the project currency symbol`() {
        assertEquals("₹1,234.50", formatMoney(1234.5, "INR"))
        assertEquals("$99.00", formatMoney(99.0, "USD"))
        assertEquals("-₹40.00", formatMoney(-40.0, "INR"))
    }

    @Test
    fun `falls back to the raw code for currencies without a symbol`() {
        assertEquals("AUD 10.00", formatMoney(10.0, "AUD"))
    }

    @Test
    fun `describes balances in plain language`() {
        assertEquals("gets back ₹250.00", balanceLabel(250.0, "INR"))
        assertEquals("owes ₹250.00", balanceLabel(-250.0, "INR"))
        assertEquals("settled up", balanceLabel(0.0, "INR"))
    }

    @Test
    fun `treats sub-cent balances as settled`() {
        // Equal splits of odd amounts leave rounding dust; it should not read as a debt.
        assertEquals("settled up", balanceLabel(0.001, "INR"))
        assertEquals("settled up", balanceLabel(-0.001, "INR"))
    }
}
