package com.twinmind.domain.model

sealed class RecordingState {
    object Idle : RecordingState()
    data class Recording(val meetingId: String, val elapsedMs: Long = 0L) : RecordingState()
    data class Paused(val meetingId: String, val reason: PauseReason, val elapsedMs: Long = 0L) : RecordingState()
    data class Stopped(val meetingId: String) : RecordingState()
    data class Error(val message: String) : RecordingState()
}

enum class PauseReason(val displayText: String) {
    PHONE_CALL("Paused - Phone call"),
    AUDIO_FOCUS_LOST("Paused - Audio focus lost"),
    LOW_STORAGE("Recording stopped - Low storage"),
    MANUAL("Paused")
}

data class RecordingStatus(
    val state: RecordingState = RecordingState.Idle,
    val currentChunkIndex: Int = 0,
    val silenceDetected: Boolean = false,
    val audioSource: AudioSource = AudioSource.BUILT_IN_MIC
)

enum class AudioSource {
    BUILT_IN_MIC,
    WIRED_HEADSET,
    BLUETOOTH_HEADSET
}