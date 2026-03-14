package com.twinmind.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class AudioFocusHandler(
    private val context: Context,
    private val onFocusLost: () -> Unit,
    private val onFocusGained: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onFocusLost()
                AudioManager.AUDIOFOCUS_GAIN -> onFocusGained()
            }
        }
        .build()

    fun requestFocus() {
        audioManager.requestAudioFocus(focusRequest)
    }

    fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}