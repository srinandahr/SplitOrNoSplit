package com.srinandahr.splitornosplit.data

/**
 * A shared-expense project on an I Hate Money instance.
 *
 * The project id plus its private code together are the ONLY credential — I Hate Money
 * has no per-user accounts. Anyone holding both has full read/write/delete on the
 * project, which is why this is stored via [SecurePrefs] and never logged.
 */
data class Project(
    val instanceUrl: String,
    val projectId: String,
    val privateCode: String,
    val name: String,
    val currency: String = DEFAULT_CURRENCY,
    /** Which member the person holding this phone is; fills the `payer` field on posts. */
    val myMemberId: Int? = null,
    /** Last known member list. Refreshed on open and before each post. */
    val members: List<Member> = emptyList(),
) {
    val isReadyToPost: Boolean get() = myMemberId != null && members.isNotEmpty()

    companion object {
        const val DEFAULT_INSTANCE = "https://ihatemoney.org"
        const val DEFAULT_CURRENCY = "INR"
    }
}

data class Member(
    val id: Int,
    val name: String,
    val weight: Double = 1.0,
    val activated: Boolean = true,
)

data class Bill(
    val id: Int,
    val what: String,
    val amount: Double,
    val payerId: Int,
    /** Full member objects, not just ids — weights are needed to work out each person's share. */
    val owers: List<Member>,
    val date: String,
    val currency: String?,
) {
    val owerIds: List<Int> get() = owers.map { it.id }

    /**
     * What [memberId] personally owes on this bill.
     *
     * I Hate Money supports uneven splits via per-member weights, so this is a weighted
     * share rather than amount / count. Returns 0 for someone not on the bill.
     */
    fun shareOf(memberId: Int): Double {
        val totalWeight = owers.sumOf { it.weight }
        if (totalWeight <= 0.0) return 0.0
        val mine = owers.firstOrNull { it.id == memberId } ?: return 0.0
        return amount * mine.weight / totalWeight
    }

    /**
     * Net effect of this bill on [memberId]: positive if they are owed money for it,
     * negative if they owe, zero if uninvolved.
     */
    fun netFor(memberId: Int): Double {
        val paid = if (payerId == memberId) amount else 0.0
        return paid - shareOf(memberId)
    }
}

/** Per-member settlement figures, as computed by the server's /statistics endpoint. */
data class Balance(
    val memberId: Int,
    val memberName: String,
    val paid: Double,
    val spent: Double,
    val balance: Double,
)

/** Currency symbols for the handful of currencies worth special-casing. */
fun currencySymbol(code: String): String = when (code.uppercase()) {
    "INR" -> "₹"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY" -> "¥"
    "XXX" -> ""
    else -> "$code "
}
