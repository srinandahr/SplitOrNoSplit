package com.srinandahr.splitornosplit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {


        val sharedPref = context.getSharedPreferences("SplitAppPrefs", Context.MODE_PRIVATE)
        val isPaused = sharedPref.getBoolean("isPaused", false)

        if (isPaused) {
            Log.d("SMS_TEST", "App is PAUSED. Ignoring SMS.")
            return // STOP HERE! Do not proceed.
        }


        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (sms in messages) {
                val messageBody = sms.messageBody ?: ""


                // 1. Check for "Sent Rs." or "debited"
                if (messageBody.contains("Sent Rs.") || messageBody.contains("debited")) {

                    // ... (Regex logic) ...
                    val amountRegex = Regex("(?i)(?:Sent\\s+)?Rs\\.?\\s*([\\d,]+(?:\\.\\d{2})?)")
                    val amountMatch = amountRegex.find(messageBody)
                    val amount = amountMatch?.groupValues?.get(1)

                    val payeeRegex = Regex("(?m)^To\\s+(.*)$")
                    val payeeMatch = payeeRegex.find(messageBody)
                    val payee = payeeMatch?.groupValues?.get(1)?.trim() ?: "Unknown"

                    if (amount != null) {
                        // Pass to notification
                        NotificationHelper(context).showSplitNotification(amount, payee)
                    }
                }
            }
        }
    }
}