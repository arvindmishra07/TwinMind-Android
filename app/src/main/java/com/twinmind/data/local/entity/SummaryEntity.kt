package com.twinmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey val meetingId: String,
    val title: String = "",
    val summary: String = "",
    val actionItems: String = "[]",
    val keyPoints: String = "[]",
    val status: String = "PENDING",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)