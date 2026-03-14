package com.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: String = "RECORDING", // RECORDING, COMPLETED, FAILED
    val audioFolderPath: String,
    val duration: Long = 0L
)