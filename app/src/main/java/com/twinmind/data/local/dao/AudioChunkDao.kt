package com.twinmind.data.local.dao

import androidx.room.*
import com.twinmind.data.local.entity.AudioChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: AudioChunkEntity)

    @Update
    suspend fun update(chunk: AudioChunkEntity)

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    suspend fun getChunksForMeeting(meetingId: String): List<AudioChunkEntity>

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId ORDER BY chunkIndex ASC")
    fun getChunksForMeetingFlow(meetingId: String): Flow<List<AudioChunkEntity>>

    @Query("SELECT * FROM audio_chunks WHERE transcriptionStatus = 'PENDING' OR transcriptionStatus = 'FAILED'")
    suspend fun getPendingChunks(): List<AudioChunkEntity>

    @Query("UPDATE audio_chunks SET transcriptionStatus = :status, transcript = :transcript WHERE id = :id")
    suspend fun updateTranscriptionStatus(id: String, status: String, transcript: String?)

    @Query("UPDATE audio_chunks SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: String)

    @Query("SELECT * FROM audio_chunks WHERE meetingId = :meetingId AND transcriptionStatus = 'COMPLETED' ORDER BY chunkIndex ASC")
    suspend fun getCompletedChunks(meetingId: String): List<AudioChunkEntity>
}