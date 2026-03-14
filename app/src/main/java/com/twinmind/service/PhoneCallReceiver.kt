package com.twinmind.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.twinmind.util.Constants

class PhoneCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val serviceIntent = Intent(context, RecordingService::class.java)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                serviceIntent.action = Constants.ACTION_PAUSE_RECORDING
                serviceIntent.putExtra("reason", "PHONE_CALL")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                serviceIntent.action = Constants.ACTION_RESUME_RECORDING
                serviceIntent.putExtra("reason", "PHONE_CALL_ENDED")
            }
            else -> return
        }
        context.startService(serviceIntent)
    }
}