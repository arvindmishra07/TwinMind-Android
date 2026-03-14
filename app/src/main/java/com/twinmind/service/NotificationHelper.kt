package com.twinmind.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.twinmind.MainActivity
import com.twinmind.R
import com.twinmind.util.Constants

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "TwinMind Recording Service"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun buildRecordingNotification(
        status: String,
        timer: String,
        showResume: Boolean = false
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            context, 1,
            Intent(context, RecordingService::class.java).apply {
                action = Constants.ACTION_STOP_RECORDING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TwinMind • $timer")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stopIntent)

        if (showResume) {
            val resumeIntent = PendingIntent.getService(
                context, 2,
                Intent(context, RecordingService::class.java).apply {
                    action = Constants.ACTION_RESUME_RECORDING
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Resume", resumeIntent)
        }

        return builder.build()
    }

    fun updateNotification(status: String, timer: String, showResume: Boolean = false) {
        notificationManager.notify(
            Constants.RECORDING_NOTIFICATION_ID,
            buildRecordingNotification(status, timer, showResume)
        )
    }
}