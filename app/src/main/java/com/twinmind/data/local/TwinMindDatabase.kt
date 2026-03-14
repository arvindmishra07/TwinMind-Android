package com.twinmind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.local.dao.MeetingDao
import com.twinmind.data.local.dao.SummaryDao
import com.twinmind.data.local.entity.AudioChunkEntity
import com.twinmind.data.local.entity.MeetingEntity
import com.twinmind.data.local.entity.SummaryEntity

@Database(
    entities = [MeetingEntity::class, AudioChunkEntity::class, SummaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TwinMindDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao
    abstract fun audioChunkDao(): AudioChunkDao
    abstract fun summaryDao(): SummaryDao
}