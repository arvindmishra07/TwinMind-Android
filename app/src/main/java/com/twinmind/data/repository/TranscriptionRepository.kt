package com.twinmind.data.repository

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.local.entity.AudioChunkEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    @Named("transcription") private val model: GenerativeModel,
    private val audioChunkDao: AudioChunkDao
) {
    companion object {
        private const val TAG = "TranscriptionRepo"
    }

    suspend fun transcribeChunk(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) throw Exception("Audio file not found: $filePath")

        val audioBytes = file.readBytes()
        val mimeType = "audio/m4a"

        Log.d(TAG, "Transcribing chunk: ${file.name}, size: ${audioBytes.size} bytes")

        val response = model.generateContent(
            content {
                blob(mimeType, audioBytes)
                text(
                    """
                    Transcribe this audio recording accurately.
                    Return ONLY the transcript text, nothing else.
                    Preserve speaker changes with new lines if multiple speakers.
                    If the audio is silent or inaudible, return an empty string.
                    """.trimIndent()
                )
            }
        )

        val transcript = response.text?.trim() ?: ""
        Log.d(TAG, "Transcription result: ${transcript.take(100)}...")
        return transcript
    }

    suspend fun getFullTranscript(meetingId: String): String {
        val chunks = audioChunkDao.getCompletedChunks(meetingId)
        return chunks
            .sortedBy { it.chunkIndex }
            .mapNotNull { it.transcript }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    suspend fun retryFailedChunks(meetingId: String): Boolean {
        val chunks = audioChunkDao.getChunksForMeeting(meetingId)
        val failedChunks = chunks.filter {
            it.transcriptionStatus == "FAILED" || it.transcriptionStatus == "PENDING"
        }

        if (failedChunks.isEmpty()) return true

        var allSuccess = true
        for (chunk in failedChunks) {
            try {
                audioChunkDao.updateTranscriptionStatus(chunk.id, "PROCESSING", null)
                val transcript = transcribeChunk(chunk.filePath)
                audioChunkDao.updateTranscriptionStatus(chunk.id, "COMPLETED", transcript)
            } catch (e: Exception) {
                Log.e(TAG, "Retry failed for chunk ${chunk.id}: ${e.message}")
                audioChunkDao.updateTranscriptionStatus(chunk.id, "FAILED", null)
                allSuccess = false
            }
        }
        return allSuccess
    }
}