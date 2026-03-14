package com.twinmind.data.local.dao

import androidx.room.*
import com.twinmind.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryEntity)

    @Update
    suspend fun update(summary: SummaryEntity)

    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId")
    fun getSummaryForMeeting(meetingId: String): Flow<SummaryEntity?>

    @Query("SELECT * FROM summaries WHERE meetingId = :meetingId")
    suspend fun getSummaryById(meetingId: String): SummaryEntity?

    @Query("UPDATE summaries SET status = :status, errorMessage = :error WHERE meetingId = :meetingId")
    suspend fun updateStatus(meetingId: String, status: String, error: String? = null)
}