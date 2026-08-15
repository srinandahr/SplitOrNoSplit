package com.srinandahr.splitornosplit.split

import com.srinandahr.splitornosplit.data.Balance
import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.BillType
import com.srinandahr.splitornosplit.data.Member
import com.srinandahr.splitornosplit.data.Project

/**
 * The seam between this app and whatever service holds the shared ledger.
 *
 * v1 called Splitwise directly from ActionReceiver, so when Splitwise put the API behind a
 * subscription the change reached into every layer. Everything now goes through this
 * interface, so swapping or adding a backend is one new implementation rather than a rewrite.
 */
interface SplitTarget {

    suspend fun fetchMembers(project: Project): Result<List<Member>>

    /** Adds a member and returns the refreshed list, so callers never guess the new id. */
    suspend fun addMember(project: Project, name: String): Result<List<Member>>

    suspend fun createExpense(
        project: Project,
        amount: String,
        description: String,
        payerId: Int,
        owerIds: List<Int>,
        billType: BillType = BillType.EXPENSE,
        date: String? = null,
    ): Result<Unit>

    /**
     * Overwrites an existing bill. Every field is required because the server replaces the
     * whole record rather than patching it, [billType] very much included — see
     * `IHateMoneyApi.updateBill`.
     */
    suspend fun updateExpense(
        project: Project,
        billId: Int,
        amount: String,
        description: String,
        payerId: Int,
        owerIds: List<Int>,
        date: String,
        billType: BillType,
    ): Result<Unit>

    suspend fun deleteExpense(project: Project, billId: Int): Result<Unit>

    suspend fun fetchBalances(project: Project): Result<List<Balance>>

    suspend fun fetchBills(project: Project, limit: Int = 25): Result<List<Bill>>
}

/**
 * Creating and validating projects. Separate from [SplitTarget] because these run before a
 * [Project] exists, so they cannot take one as a parameter.
 */
interface ProjectProvisioner {

    suspend fun createProject(
        instanceUrl: String,
        name: String,
        contactEmail: String,
        currency: String,
        memberNames: List<String>,
    ): Result<Project>

    /** Confirms a project id + private code actually work, and returns the live project. */
    suspend fun verifyProject(
        instanceUrl: String,
        projectId: String,
        privateCode: String,
    ): Result<Project>
}
