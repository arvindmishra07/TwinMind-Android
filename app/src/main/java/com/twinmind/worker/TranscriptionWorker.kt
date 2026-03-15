package com.twinmind.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.local.dao.MeetingDao
import com.twinmind.data.repository.TranscriptionRepository
import com.twinmind.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val audioChunkDao: AudioChunkDao,
    private val meetingDao: MeetingDao,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_CHUNK_ID = "chunk_id"
        const val KEY_MEETING_ID = "meeting_id"
        private const val TAG = "TranscriptionWorker"

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
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(Constants.WORK_TRANSCRIPTION)
                .addTag("meeting_$meetingId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val chunkId = inputData.getString(KEY_CHUNK_ID) ?: return Result.failure()
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()

        Log.d(TAG, "Starting transcription for chunk: $chunkId")

        // Add delay based on attempt count to avoid rate limiting
        if (runAttemptCount > 0) {
            val delayMs = (runAttemptCount * 15000L)
            Log.d(TAG, "Waiting ${delayMs}ms before retry attempt $runAttemptCount")
            kotlinx.coroutines.delay(delayMs)
        }

        return try {
            audioChunkDao.updateTranscriptionStatus(chunkId, "PROCESSING", null)

            val chunks = audioChunkDao.getChunksForMeeting(meetingId)
            val chunk = chunks.find { it.id == chunkId }
                ?: run {
                    Log.e(TAG, "Chunk not found: $chunkId")
                    return Result.failure()
                }

            val transcript = transcriptionRepository.transcribeChunk(chunk.filePath)
            audioChunkDao.updateTranscriptionStatus(chunkId, "COMPLETED", transcript)
            Log.d(TAG, "Transcription completed for chunk: $chunkId")

            checkAndTriggerSummary(meetingId)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed for chunk $chunkId: ${e.message}")
            audioChunkDao.incrementRetryCount(chunkId)
            audioChunkDao.updateTranscriptionStatus(chunkId, "FAILED", null)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
    private suspend fun checkAndTriggerSummary(meetingId: String) {
        val meeting = meetingDao.getMeetingById(meetingId) ?: return

        // Only trigger summary if meeting is completed (not still recording)
        if (meeting.status != "COMPLETED") return

        val allChunks = audioChunkDao.getChunksForMeeting(meetingId)
        val pendingChunks = allChunks.filter {
            it.transcriptionStatus == "PENDING" ||
                    it.transcriptionStatus == "PROCESSING" ||
                    it.transcriptionStatus == "FAILED"
        }

        if (pendingChunks.isEmpty()) {
            Log.d(TAG, "All chunks transcribed for meeting $meetingId, triggering summary")
            SummaryWorker.enqueue(applicationContext, meetingId)
        }
    }
}