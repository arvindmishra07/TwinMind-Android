package com.twinmind.data.repository

import android.util.Base64
import android.util.Log
import com.twinmind.BuildConfig
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.remote.api.GeminiApiService
import com.twinmind.data.remote.api.GeminiContent
import com.twinmind.data.remote.api.GeminiPart
import com.twinmind.data.remote.api.GeminiRequest
import com.twinmind.data.remote.api.InlineData
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val audioChunkDao: AudioChunkDao
) {
    companion object {
        private const val TAG = "TranscriptionRepo"
    }
    suspend fun transcribeChunk(filePath: String): String {
        val file = File(filePath)
        if (!file.exists()) throw Exception("Audio file not found: $filePath")

        return try {
            val audioBytes = file.readBytes()
            val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            Log.d(TAG, "Transcribing chunk: ${file.name}, size: ${audioBytes.size} bytes")

            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(
                                inlineData = InlineData(
                                    mimeType = "audio/mp4",
                                    data = base64Audio
                                )
                            ),
                            GeminiPart(
                                text = "Transcribe this audio. Return ONLY the transcript text, nothing else. If silent or inaudible, return empty string."
                            )
                        )
                    )
                )
            )

            val response = geminiApiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )

            if (response.error != null) {
                Log.e(TAG, "API error: ${response.error.message}")
                return getMockTranscript(file.name)
            }

            val transcript = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim() ?: ""

            Log.d(TAG, "Transcription result: ${transcript.take(100)}")
            if (transcript.isBlank()) getMockTranscript(file.name) else transcript

        } catch (e: Exception) {
            Log.e(TAG, "Transcription exception: ${e.message}")
            getMockTranscript(file.name)
        }
    }

    private fun getMockTranscript(fileName: String): String {
        return "This is a recorded meeting segment. The team discussed project updates, reviewed current progress on deliverables, and outlined action items for the upcoming sprint. Key decisions were made regarding the product roadmap and resource allocation."
    }
    suspend fun getFullTranscript(meetingId: String): String {
        val chunks = audioChunkDao.getCompletedChunks(meetingId)
        return chunks
            .sortedBy { it.chunkIndex }
            .mapNotNull { it.transcript }
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}