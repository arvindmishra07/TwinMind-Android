package com.twinmind.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.twinmind.MainActivity
import com.twinmind.R
import com.twinmind.util.Constants

class LiveUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "LiveUpdateManager"
        const val LIVE_UPDATE_NOTIFICATION_ID = 2001
        const val LIVE_UPDATE_CHANNEL_ID = "live_update_channel"
    }

    fun buildLiveUpdateNotification(
        timer: String,
        status: String,
        isPaused: Boolean
    ): Notification? {
        // Live Updates only available on Android 16+ (API 36)
        if (Build.VERSION.SDK_INT < 36) return null

        return try {
            buildLiveNotificationApi36(timer, status, isPaused)
        } catch (e: Exception) {
            Log.e(TAG, "Live update failed: ${e.message}")
            null
        }
    }

    private fun buildLiveNotificationApi36(
        timer: String,
        status: String,
        isPaused: Boolean
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

        val pauseResumeIntent = PendingIntent.getService(
            context, 2,
            Intent(context, RecordingService::class.java).apply {
                action = if (isPaused) Constants.ACTION_RESUME_RECORDING
                else Constants.ACTION_PAUSE_RECORDING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(if (isPaused) status else "🔴 Recording • $timer")
            .setContentText(if (isPaused) "Tap to resume" else "Recording in progress")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    if (isPaused) "▶ Resume" else "⏸ Pause",
                    pauseResumeIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "⏹ Stop",
                    stopIntent
                ).build()
            )
            .setUsesChronometer(!isPaused)
            .setChronometerCountDown(false)
            .build()
    }

    fun cancelLiveUpdate() {
        try {
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as android.app.NotificationManager
            notificationManager.cancel(LIVE_UPDATE_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Cancel live update failed: ${e.message}")
        }
    }
}