package com.twinmind.data.local.dao

import androidx.room.*
import com.twinmind.data.local.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meeting: MeetingEntity)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings ORDER BY startTime DESC")
    fun getAllMeetings(): Flow<List<MeetingEntity>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getMeetingById(id: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE status = 'RECORDING' LIMIT 1")
    suspend fun getActiveMeeting(): MeetingEntity?

    @Query("UPDATE meetings SET status = :status, endTime = :endTime, duration = :duration WHERE id = :id")
    suspend fun updateMeetingStatus(id: String, status: String, endTime: Long, duration: Long)
}