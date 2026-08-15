package com.srinandahr.splitornosplit.split

import com.srinandahr.splitornosplit.data.Balance
import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.BillType
import com.srinandahr.splitornosplit.data.Member
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.net.ApiClient
import com.srinandahr.splitornosplit.net.Endpoints
import com.srinandahr.splitornosplit.net.IHateMoneyApi
import com.srinandahr.splitornosplit.net.MemberDto
import com.srinandahr.splitornosplit.net.basicAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class IHateMoneyTarget(
    private val api: IHateMoneyApi = ApiClient.api,
) : SplitTarget, ProjectProvisioner {

    // ---- SplitTarget --------------------------------------------------------

    override suspend fun fetchMembers(project: Project): Result<List<Member>> = io {
        val response = api.getMembers(
            Endpoints.members(project.instanceUrl, project.projectId),
            project.auth,
        )
        response.requireBody("members").map { it.toMember() }.filter { it.activated }
    }

    override suspend fun addMember(project: Project, name: String): Result<List<Member>> = io {
        val url = Endpoints.members(project.instanceUrl, project.projectId)
        api.addMember(url, project.auth, name.trim()).requireSuccess("add member")
        api.getMembers(url, project.auth).requireBody("members").map { it.toMember() }
    }

    override suspend fun createExpense(
        project: Project,
        amount: String,
        description: String,
        payerId: Int,
        owerIds: List<Int>,
        billType: BillType,
        date: String?,
    ): Result<Unit> = io {
        require(owerIds.isNotEmpty()) { "No members to split between" }
        val response = api.createBill(
            url = Endpoints.bills(project.instanceUrl, project.projectId),
            auth = project.auth,
            what = description,
            payer = payerId,
            // An equal split is expressed by listing every member once. There is no
            // split_equally flag as there was on Splitwise.
            payedFor = owerIds,
            amount = amount,
            date = date ?: today(),
            originalCurrency = project.currency,
            billType = billType.wire,
        )
        response.requireSuccess(if (billType == BillType.REIMBURSEMENT) "record payment" else "add expense")
    }

    override suspend fun updateExpense(
        project: Project,
        billId: Int,
        amount: String,
        description: String,
        payerId: Int,
        owerIds: List<Int>,
        date: String,
        billType: BillType,
    ): Result<Unit> = io {
        require(owerIds.isNotEmpty()) { "No members to split between" }
        val response = api.updateBill(
            url = Endpoints.bill(project.instanceUrl, project.projectId, billId),
            auth = project.auth,
            what = description,
            payer = payerId,
            payedFor = owerIds,
            amount = amount,
            date = date,
            originalCurrency = project.currency,
            billType = billType.wire,
        )
        response.requireSuccess("save changes")
    }

    override suspend fun deleteExpense(project: Project, billId: Int): Result<Unit> = io {
        val response = api.deleteBill(
            Endpoints.bill(project.instanceUrl, project.projectId, billId),
            project.auth,
        )
        // A 404 means somebody else already deleted it. The end state is what was asked for,
        // so treat it as done rather than showing an error for a row about to vanish anyway.
        if (response.code() != 404) response.requireSuccess("delete expense")
    }

    override suspend fun fetchBalances(project: Project): Result<List<Balance>> = io {
        val response = api.getStatistics(
            Endpoints.statistics(project.instanceUrl, project.projectId),
            project.auth,
        )
        response.requireBody("balances").mapNotNull { stat ->
            val member = stat.member ?: return@mapNotNull null
            Balance(
                memberId = member.id,
                memberName = member.name.orEmpty(),
                paid = stat.paid ?: 0.0,
                // The server reports `spent` as a negative (balance = paid + spent). Store the
                // magnitude so callers can render "share ₹475" without special-casing a sign.
                spent = abs(stat.spent ?: 0.0),
                balance = stat.balance ?: 0.0,
                transferred = stat.transferred ?: 0.0,
                // `received` comes back negative for the same reason `spent` does.
                received = abs(stat.received ?: 0.0),
            )
        }
    }

    override suspend fun fetchBills(project: Project, limit: Int): Result<List<Bill>> = io {
        val response = api.getBills(
            Endpoints.bills(project.instanceUrl, project.projectId),
            project.auth,
        )
        response.requireBody("expenses")
            .sortedByDescending { it.id }
            .take(limit)
            .map { dto ->
                Bill(
                    id = dto.id,
                    what = dto.what.orEmpty(),
                    amount = dto.amount ?: 0.0,
                    payerId = dto.payerId ?: -1,
                    owers = dto.owers?.map { it.toMember() }.orEmpty(),
                    date = dto.date.orEmpty(),
                    currency = dto.originalCurrency,
                    billType = BillType.fromWire(dto.billType),
                )
            }
    }

    // ---- ProjectProvisioner -------------------------------------------------

    override suspend fun createProject(
        instanceUrl: String,
        name: String,
        contactEmail: String,
        currency: String,
        memberNames: List<String>,
    ): Result<Project> = io {
        val instance = Endpoints.normalizeInstance(instanceUrl)
        val projectId = uniqueProjectId(name)
        val privateCode = generatePrivateCode()

        api.createProject(
            url = Endpoints.projects(instance),
            name = name,
            id = projectId,
            password = privateCode,
            contactEmail = contactEmail,
            defaultCurrency = currency,
        ).requireSuccess("create project")

        val auth = basicAuth(projectId, privateCode)
        val membersUrl = Endpoints.members(instance, projectId)
        memberNames.map { it.trim() }.filter { it.isNotEmpty() }.forEach { memberName ->
            api.addMember(membersUrl, auth, memberName).requireSuccess("add member")
        }

        // Re-read rather than trusting the create responses: the server assigns the ids,
        // and this is the list every future split is built from.
        val members = api.getMembers(membersUrl, auth).requireBody("members").map { it.toMember() }

        Project(
            instanceUrl = instance,
            projectId = projectId,
            privateCode = privateCode,
            name = name,
            currency = currency,
            myMemberId = null,
            members = members,
        )
    }

    override suspend fun verifyProject(
        instanceUrl: String,
        projectId: String,
        privateCode: String,
    ): Result<Project> = io {
        val instance = Endpoints.normalizeInstance(instanceUrl)
        val auth = basicAuth(projectId, privateCode)

        val project = api.getProject(Endpoints.project(instance, projectId), auth)
            .requireBody("project")
        val members = api.getMembers(Endpoints.members(instance, projectId), auth)
            .requireBody("members").map { it.toMember() }

        Project(
            instanceUrl = instance,
            projectId = project.id ?: projectId,
            privateCode = privateCode,
            name = project.name ?: projectId,
            currency = project.defaultCurrency?.takeIf { it.isNotBlank() && it != "XXX" }
                ?: Project.DEFAULT_CURRENCY,
            myMemberId = null,
            members = members,
        )
    }
}

// ---- helpers ----------------------------------------------------------------

private val Project.auth: String get() = basicAuth(projectId, privateCode)

private fun MemberDto.toMember() = Member(
    id = id,
    name = name.orEmpty(),
    weight = weight ?: 1.0,
    activated = activated ?: true,
)

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

private suspend fun <T> io(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    runCatching { block() }
}

/** Turns HTTP failures into messages a user can act on, rather than a bare status code. */
private fun failureMessage(code: Int, action: String): String = when (code) {
    401, 403 -> "Wrong private code for this project."
    404 -> "Project not found on this server."
    400 -> "Server rejected the request ($action). Check the project settings."
    429 -> "Too many requests — wait a moment and try again."
    in 500..599 -> "The server is having trouble. Try again shortly."
    else -> "Could not $action (HTTP $code)."
}

private fun <T> Response<T>.requireBody(action: String): T {
    if (!isSuccessful) throw ApiException(failureMessage(code(), action))
    return body() ?: throw ApiException("Server returned an empty response for $action.")
}

private fun <T> Response<T>.requireSuccess(action: String) {
    if (!isSuccessful) throw ApiException(failureMessage(code(), action))
}

class ApiException(message: String) : Exception(message)
