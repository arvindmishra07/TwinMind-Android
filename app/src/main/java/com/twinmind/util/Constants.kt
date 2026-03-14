package com.twinmind.util

object Constants {
    // Recording
    const val CHUNK_DURATION_MS = 30_000L
    const val CHUNK_OVERLAP_MS = 2_000L
    const val SILENCE_DETECTION_MS = 10_000L
    const val SILENCE_THRESHOLD = 500 // amplitude threshold
    const val MIN_STORAGE_BYTES = 50 * 1024 * 1024L // 50MB

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "recording_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Recording Service"
    const val RECORDING_NOTIFICATION_ID = 1001

    // Service Actions
    const val ACTION_START_RECORDING = "action_start_recording"
    const val ACTION_STOP_RECORDING = "action_stop_recording"
    const val ACTION_PAUSE_RECORDING = "action_pause_recording"
    const val ACTION_RESUME_RECORDING = "action_resume_recording"

    // WorkManager
    const val WORK_TRANSCRIPTION = "transcription_work"
    const val WORK_SUMMARY = "summary_work"
    const val WORK_FINALIZE = "finalize_work"

    // DataStore
    const val PREF_ACTIVE_MEETING_ID = "active_meeting_id"

    // Broadcast
    const val BROADCAST_RECORDING_STATE = "com.twinmind.RECORDING_STATE"
    const val EXTRA_RECORDING_STATE = "recording_state"
    const val EXTRA_MEETING_ID = "meeting_id"
    const val EXTRA_TIMER = "timer"
}