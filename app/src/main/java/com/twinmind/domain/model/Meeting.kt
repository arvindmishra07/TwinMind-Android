package com.twinmind.domain.model

data class Meeting(
    val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: MeetingStatus = MeetingStatus.RECORDING,
    val duration: Long = 0L,
    val audioFolderPath: String = ""
)

enum class MeetingStatus {
    RECORDING, COMPLETED, FAILED
}