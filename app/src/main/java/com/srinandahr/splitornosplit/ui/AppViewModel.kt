package com.srinandahr.splitornosplit.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.srinandahr.splitornosplit.NotificationHelper
import com.srinandahr.splitornosplit.data.Balance
import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.PendingSplit
import com.srinandahr.splitornosplit.data.PendingSplitStore
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.data.ProjectStore
import com.srinandahr.splitornosplit.data.key
import com.srinandahr.splitornosplit.net.Endpoints
import com.srinandahr.splitornosplit.split.IHateMoneyTarget
import kotlinx.coroutines.launch

/** Full-screen destinations. The tabbed shell lives behind [Screen.HOME]. */
enum class Screen { HOME, SETUP, CREATE, JOIN, PICK_MEMBER, ADD_EXPENSE }

enum class Tab { EXPENSES, BALANCES, GROUPS, SETTINGS }

data class UiState(
    val screen: Screen = Screen.SETUP,
    val tab: Tab = Tab.EXPENSES,
    val busy: Boolean = false,
    val projects: List<Project> = emptyList(),
    val active: Project? = null,
    val balances: List<Balance> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val paused: Boolean = false,
    val message: String? = null,
    val showLegacyNotice: Boolean = false,
    val pendingProject: Project? = null,
    /** Detected bank debits awaiting a Split / No Split decision, within the last 24 hours. */
    val pending: List<PendingSplit> = emptyList(),
) {
    /** Your own settlement figure in the active group, from the server's statistics. */
    val myBalance: Double
        get() {
            val me = active?.myMemberId ?: return 0.0
            return balances.firstOrNull { it.memberId == me }?.balance ?: 0.0
        }

    val myName: String?
        get() {
            val me = active?.myMemberId ?: return null
            return active.members.firstOrNull { it.id == me }?.name
        }
}

class AppViewModel(private val app: Application) : AndroidViewModel(app) {

    private val store = ProjectStore(app)
    private val pendingStore = PendingSplitStore(app)
    private val target = IHateMoneyTarget()

    var state by mutableStateOf(UiState())
        private set

    init {
        val projects = store.projects()
        val active = store.active()
        state = state.copy(
            projects = projects,
            active = active,
            paused = store.isPaused(),
            screen = if (active != null) Screen.HOME else Screen.SETUP,
            showLegacyNotice = store.hasLegacySplitwiseConfig(),
            pending = pendingStore.active(),
        )
        if (active != null) refresh()
    }

    // Receivers run in this same process, so a debit detected — or a shade button pressed —
    // while the app is foregrounded updates the list with no refresh needed. The callback can
    // arrive off the main thread, hence bouncing through viewModelScope.
    private val pendingListener = pendingStore.observe {
        viewModelScope.launch { refreshPending() }
    }

    override fun onCleared() {
        super.onCleared()
        pendingStore.stopObserving(pendingListener)
    }

    // ---- pending splits -----------------------------------------------------

    /** Re-reads the pending list, dropping anything past the 24 hour window. */
    fun refreshPending() {
        state = state.copy(pending = pendingStore.active())
    }

    fun splitPending(split: PendingSplit) {
        val project = state.active ?: return
        val payerId = project.myMemberId ?: run {
            state = state.copy(message = "Pick which member you are first")
            return
        }
        state = state.copy(busy = true)
        viewModelScope.launch {
            target.createExpense(
                project = project,
                amount = split.amount,
                description = split.payee,
                payerId = payerId,
                owerIds = project.members.map { it.id },
            ).onSuccess {
                resolvePending(split)
                state = state.copy(busy = false, message = "Split ${project.members.size} ways")
                refresh()
            }.onFailure { error ->
                state = state.copy(busy = false, message = error.friendly())
            }
        }
    }

    fun dismissPending(split: PendingSplit) {
        resolvePending(split)
    }

    /** Discards every pending split and tears down the notification stack with it. */
    fun clearAllPending() {
        val ids = state.pending.map { it.id }
        pendingStore.clear()
        NotificationHelper(app).cancelAll(ids)
        state = state.copy(pending = emptyList())
    }

    private fun resolvePending(split: PendingSplit) {
        pendingStore.remove(split.id)
        val remaining = pendingStore.active()
        NotificationHelper(app).clearNotification(split.id, remaining)
        state = state.copy(pending = remaining)
    }

    // ---- navigation ---------------------------------------------------------

    fun goTo(screen: Screen) {
        state = state.copy(screen = screen)
    }

    fun selectTab(tab: Tab) {
        state = state.copy(tab = tab)
    }

    fun back() {
        state = state.copy(
            screen = if (state.active != null) Screen.HOME else Screen.SETUP,
            pendingProject = null,
        )
    }

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    fun dismissLegacyNotice() {
        store.clearLegacySplitwiseConfig()
        state = state.copy(showLegacyNotice = false)
    }

    // ---- data ---------------------------------------------------------------

    fun refresh() {
        // Pull the pending list too: a debit detected while the app was already open is not
        // otherwise picked up by a manual refresh.
        refreshPending()
        val project = state.active ?: return
        state = state.copy(busy = true)
        viewModelScope.launch {
            // Members are refreshed alongside balances so a flatmate who joined since the
            // last open is included in tonight's split.
            target.fetchMembers(project).onSuccess { members ->
                if (members.isNotEmpty()) {
                    val updated = project.copy(members = members)
                    store.upsert(updated)
                    state = state.copy(active = updated, projects = store.projects())
                }
            }
            val current = state.active ?: project
            val balances = target.fetchBalances(current)
            val bills = target.fetchBills(current, limit = 100)
            state = state.copy(
                busy = false,
                balances = balances.getOrDefault(emptyList()),
                bills = bills.getOrDefault(emptyList()),
                message = balances.exceptionOrNull()?.message
                    ?: bills.exceptionOrNull()?.message,
            )
        }
    }

    fun addMember(name: String) {
        val project = state.active ?: return
        if (name.isBlank()) return
        state = state.copy(busy = true)
        viewModelScope.launch {
            target.addMember(project, name)
                .onSuccess { members ->
                    val updated = project.copy(members = members)
                    store.upsert(updated)
                    state = state.copy(
                        busy = false,
                        active = updated,
                        projects = store.projects(),
                        message = "Added ${name.trim()}",
                    )
                    refresh()
                }
                .onFailure { error ->
                    state = state.copy(busy = false, message = error.friendly())
                }
        }
    }

    fun addExpense(amount: String, description: String, payerId: Int, owerIds: List<Int>) {
        val project = state.active ?: return
        state = state.copy(busy = true)
        viewModelScope.launch {
            target.createExpense(project, amount, description, payerId, owerIds)
                .onSuccess {
                    state = state.copy(
                        busy = false,
                        screen = Screen.HOME,
                        tab = Tab.EXPENSES,
                        message = "Expense added",
                    )
                    refresh()
                }
                .onFailure { error ->
                    state = state.copy(busy = false, message = error.friendly())
                }
        }
    }

    // ---- onboarding ---------------------------------------------------------

    fun createProject(
        instanceUrl: String,
        name: String,
        email: String,
        currency: String,
        memberNames: List<String>,
    ) {
        state = state.copy(busy = true)
        viewModelScope.launch {
            target.createProject(instanceUrl, name, email, currency, memberNames)
                .onSuccess { project ->
                    state = state.copy(
                        busy = false,
                        pendingProject = project,
                        screen = Screen.PICK_MEMBER,
                    )
                }
                .onFailure { error ->
                    state = state.copy(busy = false, message = error.friendly())
                }
        }
    }

    fun joinProject(instanceUrl: String, projectId: String, privateCode: String) {
        state = state.copy(busy = true)
        viewModelScope.launch {
            target.verifyProject(instanceUrl, projectId.trim(), privateCode.trim())
                .onSuccess { project ->
                    state = state.copy(
                        busy = false,
                        pendingProject = project,
                        screen = Screen.PICK_MEMBER,
                    )
                }
                .onFailure { error ->
                    state = state.copy(busy = false, message = error.friendly())
                }
        }
    }

    /** Completes onboarding: without a member id the app has no `payer` to post as. */
    fun confirmMember(memberId: Int) {
        val pending = state.pendingProject ?: state.active ?: return
        val saved = pending.copy(myMemberId = memberId)
        store.upsert(saved)
        store.setActive(saved)
        state = state.copy(
            active = saved,
            projects = store.projects(),
            pendingProject = null,
            screen = Screen.HOME,
            tab = Tab.EXPENSES,
        )
        refresh()
    }

    // ---- project management -------------------------------------------------

    fun setActive(project: Project) {
        store.setActive(project)
        state = state.copy(
            active = project,
            balances = emptyList(),
            bills = emptyList(),
            tab = Tab.EXPENSES,
        )
        refresh()
    }

    fun changeMyMember() {
        state = state.copy(pendingProject = state.active, screen = Screen.PICK_MEMBER)
    }

    fun removeProject(project: Project) {
        store.remove(project)
        val remaining = store.projects()
        val active = store.active()
        state = state.copy(
            projects = remaining,
            active = active,
            balances = emptyList(),
            bills = emptyList(),
            screen = if (active != null) Screen.HOME else Screen.SETUP,
        )
        if (active != null) refresh()
    }

    fun setPaused(paused: Boolean) {
        store.setPaused(paused)
        state = state.copy(paused = paused)
    }

    fun resetEverything() {
        store.clearAll()
        state = UiState(screen = Screen.SETUP)
    }

    /** Handles a `splitornosplit://join?...` deep link or a scanned QR payload. */
    fun handleJoinLink(instanceUrl: String, projectId: String, privateCode: String) {
        // Normalise before comparing: stored projects keep the canonical instance URL, so a
        // link written with a trailing slash or no scheme must still match what we hold.
        val normalized = Endpoints.normalizeInstance(instanceUrl)
        val alreadyHave = state.projects.firstOrNull { it.key == "$normalized|$projectId" }
        if (alreadyHave != null) {
            setActive(alreadyHave)
            state = state.copy(message = "Already joined ${alreadyHave.name}.")
        } else {
            joinProject(instanceUrl, projectId, privateCode)
        }
    }
}

private fun Throwable.friendly(): String = when (this) {
    is java.net.UnknownHostException -> "No internet connection."
    is java.net.SocketTimeoutException -> "The server took too long to respond."
    else -> message ?: "Something went wrong."
}
