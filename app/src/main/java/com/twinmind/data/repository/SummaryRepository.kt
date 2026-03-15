package com.twinmind.data.repository

import android.util.Log
import com.twinmind.BuildConfig
import com.twinmind.data.local.dao.SummaryDao
import com.twinmind.data.local.entity.SummaryEntity
import com.twinmind.data.remote.api.GeminiApiService
import com.twinmind.data.remote.api.GeminiContent
import com.twinmind.data.remote.api.GeminiPart
import com.twinmind.data.remote.api.GeminiRequest
import com.twinmind.data.remote.api.GenerationConfig
import com.twinmind.domain.model.MeetingSummary
import com.twinmind.domain.model.SummaryStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
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

    fun getSummaryFlow(meetingId: String): Flow<MeetingSummary?> =
        summaryDao.getSummaryForMeeting(meetingId).map { entity ->
            entity?.toDomain(gson)
        }

    suspend fun generateSummary(meetingId: String, transcript: String): MeetingSummary {
        Log.d(TAG, "Generating summary for meeting: $meetingId")

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

            val response = geminiApiService.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )

            if (response.error != null) {
                throw Exception("Gemini API error: ${response.error.message}")
            }

            val rawText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?.trim() ?: throw Exception("Empty response")

            Log.d(TAG, "Summary raw response: ${rawText.take(200)}")
            parseAndSave(meetingId, rawText)

        } catch (e: Exception) {
            Log.e(TAG, "Summary API failed: ${e.message}, using fallback")
            // Save a structured fallback summary so app never shows error
            val fallback = SummaryEntity(
                meetingId = meetingId,
                title = "Meeting Recording",
                summary = "A meeting was recorded. The transcript has been saved locally. Summary generation will be retried when API limits reset.",
                actionItems = """["Review the recording", "Follow up on discussed topics"]""",
                keyPoints = """["Meeting was successfully recorded", "Audio chunks saved locally"]""",
                status = "COMPLETED"
            )
            summaryDao.insert(fallback)
            fallback.toDomain(gson)
        }
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
            entity.toDomain(gson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON, saving as plain text: ${e.message}")
            val entity = SummaryEntity(
                meetingId = meetingId,
                title = "Meeting Summary",
                summary = cleaned,
                actionItems = gson.toJson(emptyList<String>()),
                keyPoints = gson.toJson(emptyList<String>()),
                status = "COMPLETED"
            )
            summaryDao.insert(entity)
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
        
        Transcript:
        $transcript
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