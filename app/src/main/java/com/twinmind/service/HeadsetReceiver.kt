package com.twinmind.service

import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

class HeadsetReceiver(
    private val onSourceChanged: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                val message = if (state == 1) "Wired headset connected"
                else "Wired headset disconnected"
                onSourceChanged(message)
            }
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(
                    BluetoothHeadset.EXTRA_STATE,
                    BluetoothHeadset.STATE_DISCONNECTED
                )
                val message = if (state == BluetoothHeadset.STATE_CONNECTED)
                    "Bluetooth headset connected"
                else "Bluetooth headset disconnected"
                onSourceChanged(message)
            }
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    onSourceChanged("Bluetooth audio active")
                }
            }
        }
    }
}