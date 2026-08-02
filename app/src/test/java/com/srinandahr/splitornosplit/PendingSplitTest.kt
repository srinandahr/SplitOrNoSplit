package com.srinandahr.splitornosplit

import com.srinandahr.splitornosplit.data.PendingSplit
import com.srinandahr.splitornosplit.ui.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSplitTest {

    private val now = 1_800_000_000_000L
    private fun split(detectedAt: Long) =
        PendingSplit(id = 1, amount = "450.00", payee = "Zomato", detectedAt = detectedAt)

    @Test
    fun `a fresh split is live`() {
        assertFalse(split(now).isExpired(now))
    }

    @Test
    fun `still live just inside the 24 hour window`() {
        val almost = now - PendingSplit.TTL_MILLIS + 60_000
        assertFalse(split(almost).isExpired(now))
    }

    @Test
    fun `expires once past 24 hours`() {
        val stale = now - PendingSplit.TTL_MILLIS - 1
        assertTrue(split(stale).isExpired(now))
    }

    @Test
    fun `a swiped split is still live, just no longer notifiable`() {
        val swiped = split(now).copy(dismissed = true)
        // It must survive in the store — the user deferred a decision, they did not make one.
        assertFalse(swiped.isExpired(now))
        assertTrue(swiped.dismissed)
        // And it must be filtered out of anything destined for the shade.
        val notifiable = listOf(split(now), swiped.copy(id = 2)).filterNot { it.dismissed }
        assertEquals(1, notifiable.size)
        assertEquals(1, notifiable.first().id)
    }

    @Test
    fun `dismissal does not survive expiry`() {
        val swipedAndStale = split(now - PendingSplit.TTL_MILLIS - 1).copy(dismissed = true)
        assertTrue(swipedAndStale.isExpired(now))
    }

    @Test
    fun `describes age in plain language`() {
        assertEquals("just now", relativeTime(now, now))
        assertEquals("18 min ago", relativeTime(now - 18 * 60_000, now))
        assertEquals("1 hour ago", relativeTime(now - 65 * 60_000, now))
        assertEquals("6 hours ago", relativeTime(now - 6 * 60 * 60_000, now))
        assertEquals("23 hours ago", relativeTime(now - 23 * 60 * 60_000, now))
    }
}
