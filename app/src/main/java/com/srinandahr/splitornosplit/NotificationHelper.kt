package com.srinandahr.splitornosplit

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(val context: Context) {

    private val CHANNEL_ID = "split_expense_channel"

    fun showSplitNotification(amount: String, payee: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. Create Channel (Required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Expense Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for splitting expenses"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Setup "Split" Action
        val splitIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "ACTION_SPLIT"
            putExtra("AMOUNT", amount)
            putExtra("PAYEE", payee)
        }
        val splitPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            splitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Setup "No Split" Action
        val noSplitIntent = Intent(context, ActionReceiver::class.java).apply {
            action = "ACTION_NO_SPLIT"
        }
        val noSplitPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            noSplitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. PREPARE THE LOGO (Large Icon)
        // This takes your App Icon (ic_launcher) and turns it into a Bitmap
        val largeIconBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        // 5. Build the Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // SMALL ICON: Must be a silhouette. We use a standard money icon or system icon.
            // If you use your logo here, it becomes a white square.
            .setSmallIcon(android.R.drawable.ic_input_add)

            // LARGE ICON: This is your colorful App Logo!
            .setLargeIcon(largeIconBitmap)

            .setContentTitle("Split or No Split")
            .setContentText("Paid ₹$amount to $payee")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

            // --- THE BUTTONS ARE BACK! ---
            .addAction(android.R.drawable.ic_menu_share, "Split", splitPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "No Split", noSplitPendingIntent)
            // -----------------------------

            .build()

        notificationManager.notify(1, notification)
    }
}