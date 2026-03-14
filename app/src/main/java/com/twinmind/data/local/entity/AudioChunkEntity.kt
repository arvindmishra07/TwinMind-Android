package com.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_chunks")
data class AudioChunkEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    val chunkIndex: Int,
    val filePath: String,
    val startTime: Long,
    val duration: Long = 30000L,
    val transcriptionStatus: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED
    val transcript: String? = null,
    val retryCount: Int = 0
)