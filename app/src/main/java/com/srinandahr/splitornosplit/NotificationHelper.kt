package com.srinandahr.splitornosplit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.srinandahr.splitornosplit.data.PendingSplit
import com.srinandahr.splitornosplit.data.PendingSplitStore
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.data.ProjectStore
import com.srinandahr.splitornosplit.data.currencySymbol

class NotificationHelper(private val context: Context) {

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Rebuilds the whole notification stack from the pending list.
     *
     * v1 posted every prompt under a single fixed id, so a second bank SMS silently replaced
     * the first and that transaction was lost. Each pending split now owns an id, and they
     * bundle under one summary. Re-posting the full set also means a prompt the user swiped
     * away without deciding reappears the next time anything arrives.
     */
    fun syncPendingNotifications(all: List<PendingSplit>) {
        ensureChannel()

        // Anything the user swiped away stays out of the shade for good. It is still visible
        // in the app, so re-posting it would only nag about a decision already deferred.
        val pending = all.filterNot { it.dismissed }

        if (pending.isEmpty()) {
            manager.cancel(PendingSplitStore.SUMMARY_ID)
            return
        }

        val project = ProjectStore(context).active()
        val symbol = currencySymbol(project?.currency ?: Project.DEFAULT_CURRENCY)
        val largeIcon = BitmapFactory.decodeResource(
            context.resources,
            R.mipmap.ic_launcher_foreground,
        )

        pending.forEach { split ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_input_add)
                .setLargeIcon(largeIcon)
                .setContentTitle("Split or No Split")
                .setContentText("Paid $symbol${split.amount} to ${split.payee}")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(split.detectedAt)
                .setShowWhen(true)
                .setAutoCancel(true)
                // Re-posting the stack must not make already-seen splits buzz again. A new
                // split gets a new id, so it still alerts normally.
                .setOnlyAlertOnce(true)
                .setGroup(GROUP_KEY)
                .setDeleteIntent(actionIntent(ActionReceiver.ACTION_DISMISSED, split))
                .addAction(
                    android.R.drawable.ic_menu_share,
                    "Split",
                    actionIntent(ActionReceiver.ACTION_SPLIT, split),
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "No Split",
                    actionIntent(ActionReceiver.ACTION_NO_SPLIT, split),
                )
                .build()
            manager.notify(split.id, notification)
        }

        // A group needs an explicit summary, otherwise pre-Android-N devices show nothing
        // sensible and newer ones bundle without a header.
        val inbox = NotificationCompat.InboxStyle()
            .setBigContentTitle("${pending.size} waiting to be split")
        pending.take(5).forEach { inbox.addLine("$symbol${it.amount} · ${it.payee}") }
        if (pending.size > 5) inbox.setSummaryText("and ${pending.size - 5} more")

        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_input_add)
            .setContentTitle("${pending.size} waiting to be split")
            .setContentText("Tap to review")
            .setStyle(inbox)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(dismissAllIntent())
            .build()
        manager.notify(PendingSplitStore.SUMMARY_ID, summary)
    }

    /** Clears one split's notification, then rebuilds the summary from what is left. */
    fun clearNotification(id: Int, remaining: List<PendingSplit>) {
        manager.cancel(id)
        if (remaining.isEmpty()) {
            manager.cancel(PendingSplitStore.SUMMARY_ID)
        } else {
            syncPendingNotifications(remaining)
        }
    }

    /** Tears the whole stack down at once, for "Clear all". */
    fun cancelAll(ids: List<Int>) {
        ids.forEach { manager.cancel(it) }
        manager.cancel(PendingSplitStore.SUMMARY_ID)
    }

    private fun actionIntent(action: String, split: PendingSplit): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(ActionReceiver.EXTRA_PENDING_ID, split.id)
            putExtra(ActionReceiver.EXTRA_AMOUNT, split.amount)
            putExtra(ActionReceiver.EXTRA_PAYEE, split.payee)
        }
        // The request code must vary per split AND per action, or PendingIntents collapse
        // onto one another and every button would act on the same expense. Split ids start
        // at 1001, so these codes never reach REQUEST_DISMISS_ALL.
        val slot = when (action) {
            ActionReceiver.ACTION_SPLIT -> 0
            ActionReceiver.ACTION_NO_SPLIT -> 1
            else -> 2
        }
        return PendingIntent.getBroadcast(
            context,
            split.id * 3 + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissAllIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_DISMISS_ALL,
        Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_DISMISSED_ALL
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Expense Alerts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications for splitting expenses"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "split_expense_channel"
        private const val GROUP_KEY = "com.srinandahr.splitornosplit.SPLITS"
        private const val REQUEST_DISMISS_ALL = 1
    }
}
