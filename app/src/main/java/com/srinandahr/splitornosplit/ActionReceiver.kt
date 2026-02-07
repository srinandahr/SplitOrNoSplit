package com.srinandahr.splitornosplit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // 1. Dismiss the Notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1)

        // 2. Handle "SPLIT" Action
        if (intent.action == "ACTION_SPLIT") {

            // A. Get Data from the Intent (passed from SmsReceiver)
            val amount = intent.getStringExtra("AMOUNT") ?: "0"
            val payee = intent.getStringExtra("PAYEE") ?: "Unknown"

            // B. READ SAVED SETTINGS (The "Memory")
            val sharedPref = context.getSharedPreferences("SplitAppPrefs", Context.MODE_PRIVATE)
            val apiKey = sharedPref.getString("API_KEY", "") ?: ""
            val groupId = sharedPref.getLong("GROUP_ID", 0L)

            // C. VALIDATION: Did the user setup the app?
            if (apiKey.isEmpty() || groupId == 0L) {
                Toast.makeText(context, "⚠️ Setup incomplete! Open App to configure.", Toast.LENGTH_LONG).show()

                // Optional: Open the App for them
                val mainIntent = Intent(context, MainActivity::class.java)
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(mainIntent)
                return
            }

            // D. FEEDBACK: Tell user we are working
            Toast.makeText(context, "Adding ₹$amount to Splitwise...", Toast.LENGTH_SHORT).show()

            // E. THE NETWORK CALL (The "Muscle")
            val goAsync = goAsync() // Keep app alive for background work

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Create the Request Object
                    val request = ExpenseRequest(
                        cost = amount,
                        description = payee,
                        group_id = groupId,
                        split_equally = true,
                        currency_code = "INR"
                    )

                    // 2. Ensure Key has "Bearer " prefix
                    val finalKey = if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey"

                    // 3. SEND TO SPLITWISE
                    val response = SplitwiseNetwork.api.createExpense(finalKey, request)

                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            Toast.makeText(context, "Success! Added to Splitwise.", Toast.LENGTH_LONG).show()
                        } else {
                            val errorError = response.errorBody()?.string() ?: "Unknown Error"
                            Toast.makeText(context, "Failed: ${response.code()}", Toast.LENGTH_LONG).show()
                            Log.e("SPLIT_API", "Error: $errorError")
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                        Log.e("SPLIT_API", "Exception: ${e.message}")
                    }
                } finally {
                    goAsync.finish()
                }
            }

        } else if (intent.action == "ACTION_NO_SPLIT") {
            Toast.makeText(context, "Cancelled", Toast.LENGTH_SHORT).show()
        }
    }
}