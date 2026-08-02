package com.srinandahr.splitornosplit.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A detected bank debit the user has not yet accepted or skipped.
 *
 * These are held rather than fired-and-forgotten because a notification is easy to lose:
 * swiping it away, a reboot, or a second transaction arriving all used to destroy the
 * prompt permanently, and with it the only record that the expense was ever detected.
 */
data class PendingSplit(
    /** Also used as the Android notification id, so each pending item has its own. */
    val id: Int,
    val amount: String,
    val payee: String,
    val detectedAt: Long,
    /**
     * Set once the user swipes the notification away.
     *
     * Swiping is a deliberate "not now", so the split is never posted to the shade again —
     * it lives on only in the app's list until it is actioned or expires. Defaults to false,
     * which is also what Gson gives rows written before this field existed.
     */
    val dismissed: Boolean = false,
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        now - detectedAt > TTL_MILLIS

    companion object {
        /** How long an unactioned split stays recoverable. */
        const val TTL_MILLIS = 24L * 60 * 60 * 1000
    }
}

/**
 * Stores pending splits in plain preferences — an amount and a payee name are not secrets,
 * and [SmsReceiver] reads this on every message where keystore latency would be wasted.
 */
class PendingSplitStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val gson = Gson()

    /** Everything still within the 24 hour window, newest first. Expired rows are dropped. */
    fun active(now: Long = System.currentTimeMillis()): List<PendingSplit> {
        val all = read()
        val live = all.filterNot { it.isExpired(now) }
        if (live.size != all.size) write(live)
        return live.sortedByDescending { it.detectedAt }
    }

    fun add(amount: String, payee: String, now: Long = System.currentTimeMillis()): PendingSplit {
        val pending = PendingSplit(id = nextId(), amount = amount, payee = payee, detectedAt = now)
        write(read().filterNot { it.isExpired(now) } + pending)
        return pending
    }

    fun remove(id: Int) {
        write(read().filterNot { it.id == id })
    }

    /** Records that the user swiped this split's notification away. */
    fun markDismissed(id: Int) {
        write(read().map { if (it.id == id) it.copy(dismissed = true) else it })
    }

    /** Swiping the group summary dismisses every notification in the bundle at once. */
    fun markAllDismissed() {
        write(read().map { it.copy(dismissed = true) })
    }

    fun clear() {
        write(emptyList())
    }

    /**
     * Notifies when the pending list changes anywhere in this process — SmsReceiver detecting
     * a debit, or ActionReceiver handling a shade button — so a foregrounded app reflects it
     * immediately instead of waiting for the next resume.
     */
    fun observe(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_PENDING) onChange()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun stopObserving(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // Notification ids must be stable, unique and small. A wrapping counter is enough:
    // collisions would need 10,000 unactioned splits inside a 24 hour window.
    private fun nextId(): Int {
        val next = prefs.getInt(KEY_COUNTER, 0) + 1
        val wrapped = if (next > 9_999) 1 else next
        prefs.edit().putInt(KEY_COUNTER, wrapped).apply()
        return BASE_ID + wrapped
    }

    private fun read(): List<PendingSplit> {
        val raw = prefs.getString(KEY_PENDING, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<PendingSplit>>(
                raw,
                object : TypeToken<List<PendingSplit>>() {}.type,
            )
        }.getOrNull().orEmpty()
    }

    private fun write(list: List<PendingSplit>) {
        prefs.edit().putString(KEY_PENDING, gson.toJson(list)).apply()
    }

    companion object {
        private const val FILE = "SplitAppPrefs"
        private const val KEY_PENDING = "pending_splits"
        private const val KEY_COUNTER = "pending_counter"

        /** Keeps per-split ids clear of the group summary id. */
        const val BASE_ID = 1_000
        const val SUMMARY_ID = 999
    }
}
