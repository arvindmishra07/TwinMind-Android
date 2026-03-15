package com.twinmind.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.local.dao.MeetingDao
import com.twinmind.data.local.entity.AudioChunkEntity
import com.twinmind.data.repository.TranscriptionRepository
import com.twinmind.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class FinalizeRecordingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val meetingDao: MeetingDao,
    private val audioChunkDao: AudioChunkDao,
    private val transcriptionRepository: TranscriptionRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_AUDIO_FOLDER = "audio_folder"
        private const val TAG = "FinalizeWorker"

        fun enqueue(context: Context, meetingId: String, audioFolderPath: String) {
            val inputData = workDataOf(
                KEY_MEETING_ID to meetingId,
                KEY_AUDIO_FOLDER to audioFolderPath
            )
            val request = OneTimeWorkRequestBuilder<FinalizeRecordingWorker>()
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(Constants.WORK_FINALIZE)
                .addTag("finalize_$meetingId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "finalize_$meetingId",
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }

        fun cancel(context: Context, meetingId: String) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("finalize_$meetingId")
        }
    }

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.failure()
        val audioFolder = inputData.getString(KEY_AUDIO_FOLDER) ?: return Result.failure()

        Log.d(TAG, "Finalizing recording for meeting: $meetingId")

        return try {
            // Check if meeting exists and is still in RECORDING state
            val meeting = meetingDao.getMeetingById(meetingId)

            if (meeting == null) {
                Log.d(TAG, "Meeting not found, nothing to finalize")
                return Result.success()
            }

            if (meeting.status != "RECORDING") {
                Log.d(TAG, "Meeting already finalized with status: ${meeting.status}")
                return Result.success()
            }

            // Find any audio files not yet saved to Room
            val folder = File(audioFolder)
            if (folder.exists()) {
                val audioFiles = folder.listFiles()
                    ?.filter { it.extension == "m4a" }
                    ?.sortedBy { it.name }
                    ?: emptyList()

                val existingChunks = audioChunkDao.getChunksForMeeting(meetingId)
                val existingPaths = existingChunks.map { it.filePath }.toSet()

                // Save any chunks that weren't saved before process death
                audioFiles.forEachIndexed { index, file ->
                    if (file.absolutePath !in existingPaths && file.length() > 0) {
                        Log.d(TAG, "Found unregistered chunk: ${file.name}")
                        val chunkId = UUID.randomUUID().toString()
                        audioChunkDao.insert(
                            AudioChunkEntity(
                                id = chunkId,
                                meetingId = meetingId,
                                chunkIndex = existingChunks.size + index,
                                filePath = file.absolutePath,
                                startTime = file.lastModified()
                            )
                        )
                        TranscriptionWorker.enqueue(applicationContext, chunkId, meetingId)
                    }
                }
            }

            // Mark meeting as completed
            meetingDao.updateMeetingStatus(
                id = meetingId,
                status = "COMPLETED",
                endTime = System.currentTimeMillis(),
                duration = System.currentTimeMillis() - meeting.startTime
            )

            // Retry any pending transcriptions
            val pendingChunks = audioChunkDao.getPendingChunks()
                .filter { it.meetingId == meetingId }

            pendingChunks.forEach { chunk ->
                Log.d(TAG, "Re-enqueueing pending chunk: ${chunk.id}")
                TranscriptionWorker.enqueue(applicationContext, chunk.id, meetingId)
            }

            Log.d(TAG, "Finalization complete for meeting: $meetingId")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Finalization failed: ${e.message}")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}