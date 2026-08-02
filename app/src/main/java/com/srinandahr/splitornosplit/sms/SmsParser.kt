package com.srinandahr.splitornosplit.sms

/**
 * Pulls the amount and payee out of a bank debit SMS.
 *
 * Kept free of Android types so it can be unit tested — this is the most fragile logic in
 * the app (bank SMS formats vary by bank and change without warning) and it had no tests.
 */
object SmsParser {

    private val AMOUNT = Regex("(?i)(?:Sent\\s+)?(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    private val PAYEE = Regex("(?m)^To\\s+(.*)$")
    private val PAYEE_INLINE = Regex("(?i)\\bto\\s+([A-Za-z0-9@._\\-\\s]{2,40}?)(?=\\s+on\\b|\\s*[.;]|$)")

    fun looksLikeDebit(body: String): Boolean =
        body.contains("Sent Rs.", ignoreCase = true) || body.contains("debited", ignoreCase = true)

    /**
     * Returns the amount as a plain decimal string, or null.
     *
     * Grouping separators are stripped: "1,234.00" would be rejected by the expenses API,
     * which is a bug that survived from v1 because nothing ever parsed the value back.
     */
    fun parseAmount(body: String): String? {
        val raw = AMOUNT.find(body)?.groupValues?.getOrNull(1) ?: return null
        val normalized = raw.replace(",", "").trim()
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        return normalized
    }

    fun parsePayee(body: String): String {
        PAYEE.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it.trimEnd('.', ',', ';') }
        PAYEE_INLINE.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it.trimEnd('.', ',', ';') }
        return "Unknown"
    }
}
