package com.srinandahr.splitornosplit.split

import java.security.SecureRandom

/**
 * Project id and private code generation. Pure functions, kept apart from the network
 * layer so they can be unit tested.
 */

private const val CODE_ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** I Hate Money project ids must have no spaces or special characters. */
fun slugify(name: String): String {
    val slug = name.trim().lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")
    return if (slug.isEmpty()) "group" else slug.take(24)
}

/**
 * Appends a random suffix so two people creating "Flatmates" on the same instance don't
 * collide — project ids are global to an instance, not scoped to a user.
 */
fun uniqueProjectId(name: String, random: SecureRandom = SecureRandom()): String {
    val suffix = (1..6).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
    return "${slugify(name)}-$suffix"
}

/**
 * The private code is the only thing protecting the ledger, and it gets shared over chat
 * apps, so it is generated rather than chosen by the user.
 */
fun generatePrivateCode(length: Int = 20, random: SecureRandom = SecureRandom()): String =
    (1..length).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
