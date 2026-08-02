package com.srinandahr.splitornosplit

import com.srinandahr.splitornosplit.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SMS parser is the most fragile part of the app — bank formats vary and change
 * without notice — and it had no tests before this migration.
 */
class SmsParserTest {

    @Test
    fun `detects debit messages`() {
        assertTrue(SmsParser.looksLikeDebit("Sent Rs.450.00 from Kotak Bank"))
        assertTrue(SmsParser.looksLikeDebit("Rs.1,234.50 debited from A/c XX1234"))
        assertTrue(SmsParser.looksLikeDebit("your account is DEBITED"))
    }

    @Test
    fun `ignores credits and unrelated messages`() {
        assertFalse(SmsParser.looksLikeDebit("Rs.500 credited to your account"))
        assertFalse(SmsParser.looksLikeDebit("Your OTP is 123456"))
    }

    @Test
    fun `parses a plain amount`() {
        assertEquals("450.00", SmsParser.parseAmount("Sent Rs.450.00 from Kotak Bank AC X1234"))
        assertEquals("99", SmsParser.parseAmount("A/c XX999 debited for Rs 99 on 01-Aug"))
    }

    @Test
    fun `strips grouping separators so the API accepts the amount`() {
        // "1,234.50" would be rejected by the expenses endpoint. This regressed silently in
        // v1 because nothing ever parsed the value back out.
        assertEquals("1234.50", SmsParser.parseAmount("Rs.1,234.50 debited from A/c XX1234"))
        assertEquals("100000", SmsParser.parseAmount("Sent Rs.1,00,000 to Landlord"))
    }

    @Test
    fun `handles INR prefix`() {
        assertEquals("250.75", SmsParser.parseAmount("INR 250.75 debited"))
    }

    @Test
    fun `rejects messages without a usable amount`() {
        assertNull(SmsParser.parseAmount("Your account was debited"))
        assertNull(SmsParser.parseAmount("Rs.0.00 debited"))
    }

    @Test
    fun `parses payee on its own line`() {
        val sms = """
            Sent Rs.450.00
            From Kotak Bank AC X1234
            To Zomato
            On 01-08-26
        """.trimIndent()
        assertEquals("Zomato", SmsParser.parsePayee(sms))
    }

    @Test
    fun `parses payee from an inline message`() {
        assertEquals(
            "Zomato",
            SmsParser.parsePayee("Sent Rs.450.00 from Kotak Bank AC X1234 To Zomato on 01-08-26"),
        )
        assertEquals(
            "SWIGGY",
            SmsParser.parsePayee("Rs.1,234.50 debited from A/c XX1234 on 01-08-26 to SWIGGY"),
        )
    }

    @Test
    fun `falls back to Unknown when no payee is present`() {
        assertEquals("Unknown", SmsParser.parsePayee("Rs.500 debited from your account"))
    }
}
