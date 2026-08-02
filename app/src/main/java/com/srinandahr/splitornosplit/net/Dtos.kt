package com.srinandahr.splitornosplit.net

import com.google.gson.annotations.SerializedName

/**
 * Wire types for the I Hate Money REST API.
 *
 * Optional fields are nullable on purpose: these come from a third-party instance that
 * may be running an older version than ihatemoney.org, and a missing field should
 * degrade gracefully rather than throw.
 */

data class MemberDto(
    val id: Int,
    val name: String?,
    val weight: Double?,
    val activated: Boolean?,
)

data class ProjectDto(
    val id: String?,
    val name: String?,
    @SerializedName("default_currency") val defaultCurrency: String?,
    val members: List<MemberDto>?,
)

data class BillDto(
    val id: Int,
    @SerializedName("payer_id") val payerId: Int?,
    val owers: List<MemberDto>?,
    val amount: Double?,
    val date: String?,
    val what: String?,
    @SerializedName("original_currency") val originalCurrency: String?,
)

/** One entry per member from /statistics — this is where the settlement maths comes from. */
data class StatisticsDto(
    val member: MemberDto?,
    val paid: Double?,
    val spent: Double?,
    val balance: Double?,
)
