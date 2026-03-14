package com.twinmind.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.twinmind.data.local.dao.SummaryDao
import com.twinmind.data.local.entity.SummaryEntity
import com.twinmind.data.repository.SummaryRepository
import com.twinmind.data.repository.TranscriptionRepository
import com.twinmind.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val summaryDao: SummaryDao,
    private val summaryRepository: SummaryRepository,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        private const val TAG = "SummaryWorker"

        fun enqueue(context: Context, meetingId: String) {
            val inputData = workDataOf(KEY_MEETING_ID to meetingId)
            val request = OneTimeWorkRequestBuilder<SummaryWorker>()
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
                .addTag(Constants.WORK_SUMMARY)
                .addTag("summary_$meetingId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "summary_$meetingId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        Log.d(TAG, "Starting summary generation for meeting: $meetingId")

        return try {
            // Insert or update summary status
            val existing = summaryDao.getSummaryById(meetingId)
            if (existing == null) {
                summaryDao.insert(SummaryEntity(meetingId = meetingId, status = "GENERATING"))
            } else {
                summaryDao.updateStatus(meetingId, "GENERATING")
            }

            // Get full transcript
            val fullTranscript = transcriptionRepository.getFullTranscript(meetingId)
            if (fullTranscript.isBlank()) {
                summaryDao.updateStatus(meetingId, "FAILED", "No transcript available")
                return Result.failure()
            }

            // Generate summary
            val summary = summaryRepository.generateSummary(meetingId, fullTranscript)
            Log.d(TAG, "Summary generated for meeting: $meetingId")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Summary failed for meeting $meetingId: ${e.message}")
            summaryDao.updateStatus(meetingId, "FAILED", e.message)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}