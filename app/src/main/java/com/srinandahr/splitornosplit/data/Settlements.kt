package com.srinandahr.splitornosplit.data

import kotlin.math.round

/** One suggested payment: [fromName] hands [amount] to [toName] to square up. */
data class Settlement(
    val fromId: Int,
    val fromName: String,
    val toId: Int,
    val toName: String,
    val amount: Double,
)

/**
 * Works out the shortest set of payments that clears every balance.
 *
 * I Hate Money has no settle-up endpoint — the server only reports where everyone stands, so
 * the suggestion has to be computed here. This is the standard greedy pass: repeatedly send
 * the deepest debtor's money to the biggest creditor. It is not provably the minimum number of
 * transfers (that problem is NP-hard), but it never exceeds n-1 payments and matches what
 * Splitwise and I Hate Money's own web UI produce for realistic group sizes.
 *
 * Amounts are rounded to two decimals so the figures shown are the figures posted.
 */
fun suggestSettlements(balances: List<Balance>, epsilon: Double = 0.01): List<Settlement> {
    // Mutable copies: the pass draws each person's balance down to zero as it pairs them off.
    val debtors = balances
        .filter { it.balance < -epsilon }
        .sortedBy { it.balance }
        .map { Running(it.memberId, it.memberName, -it.balance) }
    val creditors = balances
        .filter { it.balance > epsilon }
        .sortedByDescending { it.balance }
        .map { Running(it.memberId, it.memberName, it.balance) }

    val settlements = mutableListOf<Settlement>()
    var d = 0
    var c = 0
    while (d < debtors.size && c < creditors.size) {
        val debtor = debtors[d]
        val creditor = creditors[c]
        val amount = minOf(debtor.remaining, creditor.remaining)

        if (amount > epsilon) {
            settlements += Settlement(
                fromId = debtor.id,
                fromName = debtor.name,
                toId = creditor.id,
                toName = creditor.name,
                amount = round2(amount),
            )
        }

        debtor.remaining -= amount
        creditor.remaining -= amount
        // At least one side is always exhausted here, so the loop cannot stall.
        if (debtor.remaining <= epsilon) d++
        if (creditor.remaining <= epsilon) c++
    }
    return settlements
}

/** The suggestions [memberId] is personally on either end of, which is what they can act on. */
fun List<Settlement>.involving(memberId: Int?): List<Settlement> =
    if (memberId == null) this else filter { it.fromId == memberId || it.toId == memberId }

private fun round2(value: Double): Double = round(value * 100.0) / 100.0

private class Running(val id: Int, val name: String, var remaining: Double)
