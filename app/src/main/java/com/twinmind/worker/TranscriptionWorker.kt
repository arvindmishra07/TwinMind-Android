package com.twinmind.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.repository.TranscriptionRepository
import com.twinmind.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val audioChunkDao: AudioChunkDao,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val chunkId = inputData.getString(KEY_CHUNK_ID) ?: return Result.failure()
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()

        return try {
            audioChunkDao.updateTranscriptionStatus(chunkId, "PROCESSING", null)
            val chunk = audioChunkDao.getChunksForMeeting(meetingId)
                .find { it.id == chunkId } ?: return Result.failure()

            val transcript = transcriptionRepository.transcribeChunk(chunk.filePath)
            audioChunkDao.updateTranscriptionStatus(chunkId, "COMPLETED", transcript)
            Result.success()
        } catch (e: Exception) {
            audioChunkDao.incrementRetryCount(chunkId)
            audioChunkDao.updateTranscriptionStatus(chunkId, "FAILED", null)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_CHUNK_ID = "chunk_id"
        const val KEY_MEETING_ID = "meeting_id"

        fun enqueue(context: Context, chunkId: String, meetingId: String) {
            val inputData = workDataOf(
                KEY_CHUNK_ID to chunkId,
                KEY_MEETING_ID to meetingId
            )
            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .addTag(Constants.WORK_TRANSCRIPTION)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}