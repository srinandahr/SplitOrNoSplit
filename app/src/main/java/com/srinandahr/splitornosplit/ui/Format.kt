package com.srinandahr.splitornosplit.ui

import com.srinandahr.splitornosplit.data.Balance
import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.BillType
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.data.Settlement
import com.srinandahr.splitornosplit.data.currencySymbol
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** "₹1,234.50" — grouping separators come from the locale, the symbol from the project. */
fun formatMoney(amount: Double, currency: String): String {
    val formatted = String.format(Locale.getDefault(), "%,.2f", abs(amount))
    val sign = if (amount < 0) "-" else ""
    return "$sign${currencySymbol(currency)}$formatted"
}

/**
 * "You pay Srinanda ₹25.00" — a suggested transfer, phrased from the viewer's side so the
 * one line they need to act on reads as an instruction rather than a fact about other people.
 */
fun settlementLine(settlement: Settlement, myMemberId: Int?, currency: String): String {
    val money = formatMoney(settlement.amount, currency)
    return when (myMemberId) {
        settlement.fromId -> "You pay ${settlement.toName} $money"
        settlement.toId -> "${settlement.fromName} pays you $money"
        else -> "${settlement.fromName} pays ${settlement.toName} $money"
    }
}

/**
 * "paid ₹200.00 · share ₹100.00 · paid back ₹150.00" — the figures behind someone's balance.
 *
 * Settlements have to appear here or the line stops adding up: once money has changed hands,
 * paid minus share no longer equals the balance shown beside it.
 */
fun balanceBreakdown(balance: Balance, currency: String): String = buildString {
    append("paid ${formatMoney(balance.paid, currency)}")
    append(" · share ${formatMoney(balance.spent, currency)}")
    if (balance.transferred > 0.005) {
        append(" · paid back ${formatMoney(balance.transferred, currency)}")
    }
    if (balance.received > 0.005) {
        append(" · got back ${formatMoney(balance.received, currency)}")
    }
}

/** Plain-language reading of a settlement balance. */
fun balanceLabel(balance: Double, currency: String): String = when {
    balance > 0.005 -> "gets back ${formatMoney(balance, currency)}"
    balance < -0.005 -> "owes ${formatMoney(-balance, currency)}"
    else -> "settled up"
}

// ---- dates ------------------------------------------------------------------

private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val MONTH_ABBREV = SimpleDateFormat("MMM", Locale.getDefault())
private val DAY = SimpleDateFormat("dd", Locale.getDefault())
private val MONTH_YEAR = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

private fun parseIso(date: String): Date? = runCatching { ISO.parse(date) }.getOrNull()

/** "Aug" / "01" for the two-line date block on each row. Falls back to the raw string. */
fun dateBlock(date: String): Pair<String, String> {
    val parsed = parseIso(date) ?: return date.takeLast(5) to ""
    return MONTH_ABBREV.format(parsed) to DAY.format(parsed)
}

/** "just now" / "18 min ago" / "6 hours ago" — pending splits never exceed 24h. */
fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - timestamp) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 120 -> "1 hour ago"
        else -> "${minutes / 60} hours ago"
    }
}

/** "July 2026" section header, or "This month" for the current one. */
fun monthHeader(date: String): String {
    val parsed = parseIso(date) ?: return "Earlier"
    val cal = Calendar.getInstance().apply { time = parsed }
    val now = Calendar.getInstance()
    val sameMonth = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    return if (sameMonth) "This month" else MONTH_YEAR.format(parsed)
}

/** Groups bills into month sections, newest first, preserving order within a section. */
fun groupByMonth(bills: List<Bill>): List<Pair<String, List<Bill>>> {
    val sorted = bills.sortedWith(compareByDescending<Bill> { it.date }.thenByDescending { it.id })
    return sorted.groupBy { monthHeader(it.date) }.toList()
}

// ---- per-row lent / borrowed -------------------------------------------------

enum class ShareDirection { LENT, BORROWED, NEUTRAL }

data class ShareSummary(
    val direction: ShareDirection,
    val label: String,
    val amount: Double,
)

/**
 * How a single bill affected you: what Splitwise renders as "you lent" / "you borrowed"
 * on the right of each row.
 */
fun shareSummary(bill: Bill, myMemberId: Int?): ShareSummary {
    if (myMemberId == null) return ShareSummary(ShareDirection.NEUTRAL, "not involved", 0.0)
    val net = bill.netFor(myMemberId)

    // A settlement moves money rather than sharing a cost, so "lent" and "borrowed" would be
    // actively misleading. The arithmetic is the same; only the wording changes.
    if (bill.billType == BillType.REIMBURSEMENT) {
        return when {
            net > 0.005 -> ShareSummary(ShareDirection.LENT, "you paid", net)
            net < -0.005 -> ShareSummary(ShareDirection.BORROWED, "you received", -net)
            else -> ShareSummary(ShareDirection.NEUTRAL, "not involved", 0.0)
        }
    }

    return when {
        net > 0.005 -> ShareSummary(ShareDirection.LENT, "you lent", net)
        net < -0.005 -> ShareSummary(ShareDirection.BORROWED, "you borrowed", -net)
        else -> ShareSummary(ShareDirection.NEUTRAL, "not involved", 0.0)
    }
}

/** "Srinanda paid ₹110.25" / "You paid ₹215.00" for the row subtitle. */
fun paidByLabel(bill: Bill, payerName: String?, myMemberId: Int?, currency: String): String {
    val who = if (bill.payerId == myMemberId) "You" else (payerName ?: "Someone")
    return "$who paid ${formatMoney(bill.amount, currency)}"
}

/**
 * The row subtitle. Settlements name both ends ("You paid Srinanda ₹25.00") because who the
 * money went to is the whole point of the entry.
 */
fun billSubtitle(bill: Bill, project: Project): String {
    val currency = bill.currency ?: project.currency
    val payerName = project.members.firstOrNull { it.id == bill.payerId }?.name
    if (bill.billType != BillType.REIMBURSEMENT) {
        return paidByLabel(bill, payerName, project.myMemberId, currency)
    }
    val who = if (bill.payerId == project.myMemberId) "You" else (payerName ?: "Someone")
    val recipientId = bill.reimbursedMemberId
    val recipient = when {
        recipientId == null -> "someone"
        recipientId == project.myMemberId -> "you"
        else -> project.members.firstOrNull { it.id == recipientId }?.name ?: "someone"
    }
    return "$who paid $recipient ${formatMoney(bill.amount, currency)}"
}
