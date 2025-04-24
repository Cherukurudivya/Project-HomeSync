package edu.cs371m.homesync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class NotificationReceiver : BroadcastReceiver() {
    private val TAG = "NotificationReceiver"

    override fun onReceive(context: Context, intent: Intent) {

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted; cannot send notification")
            return
        }

        val activity = intent.getStringExtra("activity") ?: "Schedule"
        val assignee = intent.getStringExtra("assignee") ?: "Someone"

        val builder = NotificationCompat.Builder(context, "SCHEDULE_CHANNEL")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Upcoming Schedule")
            .setContentText("$activity in 15 minutes (Assigned to: $assignee)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(System.currentTimeMillis().toInt(), builder.build())
                Log.d(TAG, "Notification sent for activity: $activity")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException while sending notification: ${e.message}", e)
            }
        }
    }
}