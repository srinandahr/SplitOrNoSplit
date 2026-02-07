package com.srinandahr.splitornosplit

// This matches the JSON we get from Splitwise
data class GroupsResponse(
    val groups: List<Group>
)

data class Group(
    val id: Long,
    val name: String
)

// This matches the JSON we SEND to Splitwise
data class ExpenseRequest(
    val cost: String,
    val description: String,
    val group_id: Long,
    val split_equally: Boolean = true,
    val currency_code: String = "INR"
)