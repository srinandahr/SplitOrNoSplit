package com.srinandahr.splitornosplit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.srinandahr.splitornosplit.data.PendingSplitStore
import com.srinandahr.splitornosplit.data.ProjectStore
import com.srinandahr.splitornosplit.data.currencySymbol
import com.srinandahr.splitornosplit.split.IHateMoneyTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingId = intent.getIntExtra(EXTRA_PENDING_ID, -1)

        when (intent.action) {
            ACTION_SPLIT -> handleSplit(context, intent, pendingId)

            ACTION_NO_SPLIT -> {
                resolve(context, pendingId)
                Toast.makeText(context, "Skipped", Toast.LENGTH_SHORT).show()
            }

            // Swiped away. The split is deliberately kept — it is still undecided and still
            // listed in the app — but it is flagged so it is never posted to the shade again.
            ACTION_DISMISSED -> if (pendingId >= 0) {
                PendingSplitStore(context).markDismissed(pendingId)
            }

            ACTION_DISMISSED_ALL -> PendingSplitStore(context).markAllDismissed()
        }
    }

    private fun handleSplit(context: Context, intent: Intent, pendingId: Int) {
        val amount = intent.getStringExtra(EXTRA_AMOUNT) ?: return
        val payee = intent.getStringExtra(EXTRA_PAYEE) ?: "Unknown"

        val store = ProjectStore(context)
        val project = store.active()
        val payerId = project?.myMemberId

        if (project == null || payerId == null) {
            // Leave it pending: the user has not decided, they simply cannot act yet.
            Toast.makeText(context, "Set up a group first", Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }

        resolve(context, pendingId)

        Toast.makeText(
            context,
            "Adding ${currencySymbol(project.currency)}$amount…",
            Toast.LENGTH_SHORT,
        ).show()

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val target = IHateMoneyTarget()

                // Refresh members so someone who joined the group since the last app open is
                // included. Bounded, because this whole block runs inside goAsync()'s ~10s
                // window — if the refresh is slow we split across the cached list rather
                // than losing the expense entirely.
                val fresh = withTimeoutOrNull(4_000) {
                    target.fetchMembers(project).getOrNull()
                }
                val members = fresh?.takeIf { it.isNotEmpty() } ?: project.members
                if (fresh != null && fresh.isNotEmpty() && fresh != project.members) {
                    store.upsert(project.copy(members = fresh))
                }

                if (members.isEmpty()) {
                    toast(context, "No members in this group yet")
                    return@launch
                }

                target.createExpense(
                    project = project,
                    amount = amount,
                    description = payee,
                    payerId = payerId,
                    owerIds = members.map { it.id },
                ).onSuccess {
                    toast(context, "Split ${members.size} ways ✓")
                }.onFailure { error ->
                    Log.e(TAG, "Split failed", error)
                    toast(context, error.message ?: "Could not add the expense")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Split failed", e)
                toast(context, "Could not add the expense")
            } finally {
                pending.finish()
            }
        }
    }

    /** Drops the split from the pending list and rebuilds the remaining notification stack. */
    private fun resolve(context: Context, pendingId: Int) {
        if (pendingId < 0) return
        val pendingStore = PendingSplitStore(context)
        pendingStore.remove(pendingId)
        NotificationHelper(context).clearNotification(pendingId, pendingStore.active())
    }

    private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        const val ACTION_SPLIT = "ACTION_SPLIT"
        const val ACTION_NO_SPLIT = "ACTION_NO_SPLIT"
        const val ACTION_DISMISSED = "ACTION_DISMISSED"
        const val ACTION_DISMISSED_ALL = "ACTION_DISMISSED_ALL"
        const val EXTRA_AMOUNT = "AMOUNT"
        const val EXTRA_PAYEE = "PAYEE"
        const val EXTRA_PENDING_ID = "PENDING_ID"
        private const val TAG = "ActionReceiver"
    }
}
