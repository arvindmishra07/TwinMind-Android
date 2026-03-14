package com.twinmind.data.repository

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.twinmind.data.local.dao.SummaryDao
import com.twinmind.data.local.entity.SummaryEntity
import com.twinmind.domain.model.MeetingSummary
import com.twinmind.domain.model.SummaryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SummaryRepository @Inject constructor(
    @Named("summary") private val model: GenerativeModel,
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
        val prompt = buildSummaryPrompt(transcript)

        val responseText = StringBuilder()

        // Stream the response
        model.generateContentStream(content { text(prompt) })
            .collect { chunk ->
                chunk.text?.let { responseText.append(it) }
                // Parse partial and save incrementally
                tryParseAndSave(meetingId, responseText.toString())
            }

        // Final parse and save
        val finalSummary = parseAndSave(meetingId, responseText.toString())
        return finalSummary
    }

    private suspend fun tryParseAndSave(meetingId: String, rawText: String) {
        try {
            parseAndSave(meetingId, rawText)
        } catch (e: Exception) {
            // Partial JSON — just update with what we have as plain text
            val current = summaryDao.getSummaryById(meetingId)
            summaryDao.update(
                (current ?: SummaryEntity(meetingId)).copy(
                    summary = rawText,
                    status = "GENERATING"
                )
            )
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
                title = parsed.title ?: "",
                summary = parsed.summary ?: "",
                actionItems = gson.toJson(parsed.action_items ?: emptyList<String>()),
                keyPoints = gson.toJson(parsed.key_points ?: emptyList<String>()),
                status = "COMPLETED"
            )
            summaryDao.insert(entity)
            entity.toDomain(gson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse summary JSON: ${e.message}")
            val entity = SummaryEntity(
                meetingId = meetingId,
                summary = cleaned,
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
        title = title,
        summary = summary,
        actionItems = try { gson.fromJson(actionItems, listType) } catch (e: Exception) { emptyList() },
        keyPoints = try { gson.fromJson(keyPoints, listType) } catch (e: Exception) { emptyList() },
        status = try { SummaryStatus.valueOf(status) } catch (e: Exception) { SummaryStatus.PENDING },
        errorMessage = errorMessage
    )
}