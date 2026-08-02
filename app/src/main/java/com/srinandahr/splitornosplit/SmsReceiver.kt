package com.srinandahr.splitornosplit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.srinandahr.splitornosplit.data.PendingSplitStore
import com.srinandahr.splitornosplit.data.ProjectStore
import com.srinandahr.splitornosplit.sms.SmsParser

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        if (ProjectStore(context).isPaused()) {
            Log.d(TAG, "Paused - ignoring SMS")
            return
        }

        val store = PendingSplitStore(context)
        var detected = false

        for (sms in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val body = sms.messageBody ?: continue
            if (!SmsParser.looksLikeDebit(body)) continue

            val amount = SmsParser.parseAmount(body) ?: continue
            val payee = SmsParser.parsePayee(body)
            store.add(amount, payee)
            detected = true
        }

        // Re-post the whole stack rather than just the new item, so anything the user swiped
        // away without deciding comes back alongside it. Expired rows are dropped by active().
        if (detected) {
            NotificationHelper(context).syncPendingNotifications(store.active())
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
