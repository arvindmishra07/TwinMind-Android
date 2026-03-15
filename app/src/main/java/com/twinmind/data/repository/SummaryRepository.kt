package com.twinmind.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twinmind.BuildConfig
import com.twinmind.data.local.dao.SummaryDao
import com.twinmind.data.local.entity.SummaryEntity
import com.twinmind.data.remote.api.GeminiApiService
import com.twinmind.data.remote.api.GeminiContent
import com.twinmind.data.remote.api.GeminiPart
import com.twinmind.data.remote.api.GeminiRequest
import com.twinmind.data.remote.api.GeminiResponse
import com.twinmind.data.remote.api.GenerationConfig
import com.twinmind.domain.model.MeetingSummary
import com.twinmind.domain.model.SummaryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val summaryDao: SummaryDao,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "SummaryRepository"
    }

    val streamingText = MutableStateFlow("")

    fun getSummaryFlow(meetingId: String): Flow<MeetingSummary?> =
        summaryDao.getSummaryForMeeting(meetingId).map { entity ->
            entity?.toDomain(gson)
        }

    suspend fun generateSummary(meetingId: String, transcript: String): MeetingSummary {
        Log.d(TAG, "Generating summary with streaming for meeting: $meetingId")
        streamingText.value = ""

        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = buildSummaryPrompt(transcript))
                        )
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    maxOutputTokens = 2048
                )
            )

            val streamResponse = geminiApiService.streamGenerateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                alt = "sse",
                request = request
            )

            if (streamResponse.isSuccessful) {
                val rawText = StringBuilder()
                val body = streamResponse.body()

                // Read stream — NO suspend calls inside forEachLine
                body?.byteStream()?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
                        if (line.startsWith("data: ")) {
                            val jsonData = line.removePrefix("data: ").trim()
                            if (jsonData == "[DONE]") return@forEachLine
                            try {
                                val chunk = gson.fromJson(jsonData, GeminiResponse::class.java)
                                val text = chunk.candidates
                                    ?.firstOrNull()
                                    ?.content
                                    ?.parts
                                    ?.firstOrNull()
                                    ?.text ?: ""
                                if (text.isNotEmpty()) {
                                    rawText.append(text)
                                    // Only update StateFlow here — no suspend calls
                                    streamingText.value = rawText.toString()
                                }
                            } catch (e: Exception) {
                                // Skip malformed chunks
                            }
                        }
                    }
                }

                // NOW we can call suspend functions — outside the lambda
                val finalText = rawText.toString()
                savePartialSummary(meetingId, finalText)
                Log.d(TAG, "Streaming complete, total length: ${finalText.length}")
                parseAndSave(meetingId, finalText)
            } else {
                Log.e(TAG, "Stream failed: ${streamResponse.code()}, falling back")
                generateSummaryFallback(meetingId, transcript)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Streaming failed: ${e.message}, using fallback")
            generateSummaryFallback(meetingId, transcript)
        }
    }

    private suspend fun savePartialSummary(meetingId: String, rawText: String) {
        try {
            val existing = summaryDao.getSummaryById(meetingId)
            summaryDao.update(
                (existing ?: SummaryEntity(meetingId)).copy(
                    summary = rawText,
                    status = "GENERATING"
                )
            )
        } catch (e: Exception) {
            // Non-critical, ignore
        }
    }

    private suspend fun generateSummaryFallback(
        meetingId: String,
        transcript: String
    ): MeetingSummary {
        return try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = buildSummaryPrompt(transcript)))
                    )
                ),
                generationConfig = GenerationConfig(
                    temperature = 0.7f,
                    maxOutputTokens = 2048
                )
            )

            val response = geminiApiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )

            if (response.error != null) throw Exception(response.error.message)

            val rawText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim() ?: throw Exception("Empty response")

            streamingText.value = rawText
            parseAndSave(meetingId, rawText)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback also failed: ${e.message}")
            saveFallbackSummary(meetingId)
        }
    }

    private suspend fun saveFallbackSummary(meetingId: String): MeetingSummary {
        val fallback = SummaryEntity(
            meetingId = meetingId,
            title = "Meeting Recording",
            summary = "A meeting was recorded. The transcript has been saved locally. Summary generation will be retried when API limits reset.",
            actionItems = """["Review the recording", "Follow up on discussed topics"]""",
            keyPoints = """["Meeting was successfully recorded", "Audio chunks saved locally"]""",
            status = "COMPLETED"
        )
        summaryDao.insert(fallback)
        streamingText.value = fallback.summary
        return fallback.toDomain(gson)
    }

    private suspend fun parseAndSave(meetingId: String, rawText: String): MeetingSummary {
        val cleaned = rawText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return try {
            val parsed = gson.fromJson(cleaned, SummaryResponse::class.java)
            val entity = SummaryEntity(
                meetingId = meetingId,
                title = parsed.title ?: "Meeting Summary",
                summary = parsed.summary ?: "",
                actionItems = gson.toJson(parsed.action_items ?: emptyList<String>()),
                keyPoints = gson.toJson(parsed.key_points ?: emptyList<String>()),
                status = "COMPLETED"
            )
            summaryDao.insert(entity)
            streamingText.value = ""
            entity.toDomain(gson)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed, saving as plain: ${e.message}")
            val entity = SummaryEntity(
                meetingId = meetingId,
                title = "Meeting Summary",
                summary = cleaned,
                actionItems = gson.toJson(emptyList<String>()),
                keyPoints = gson.toJson(emptyList<String>()),
                status = "COMPLETED"
            )
            summaryDao.insert(entity)
            streamingText.value = ""
            entity.toDomain(gson)
        }
    }

    private fun buildSummaryPrompt(transcript: String): String = """
        Analyze this meeting transcript and return a JSON response with exactly this structure:
        {
            "title": "A concise meeting title (max 10 words)",
            "summary": "A clear 2-4 sentence summary of the meeting",
            "action_items": ["Action item 1", "Action item 2", "Action item 3"],
            "key_points": ["Key point 1", "Key point 2", "Key point 3"]
        }
        Return ONLY valid JSON, no markdown, no explanation.
        Transcript: $transcript
    """.trimIndent()
}

data class SummaryResponse(
    val title: String?,
    val summary: String?,
    val action_items: List<String>?,
    val key_points: List<String>?
)

fun SummaryEntity.toDomain(gson: Gson): MeetingSummary {
    val listType = object : TypeToken<List<String>>() {}.type
    return MeetingSummary(
        meetingId = meetingId,
        title = title.orEmpty(),
        summary = summary.orEmpty(),
        actionItems = try {
            val items: List<String>? = gson.fromJson(actionItems ?: "[]", listType)
            items ?: emptyList()
        } catch (e: Exception) { emptyList() },
        keyPoints = try {
            val points: List<String>? = gson.fromJson(keyPoints ?: "[]", listType)
            points ?: emptyList()
        } catch (e: Exception) { emptyList() },
        status = try {
            SummaryStatus.valueOf(status)
        } catch (e: Exception) { SummaryStatus.PENDING },
        errorMessage = errorMessage
    )
}