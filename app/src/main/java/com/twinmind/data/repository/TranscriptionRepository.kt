package com.twinmind.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor() {
    // We'll implement Gemini transcription in Phase 3
    // For now returning mock so the project builds
    suspend fun transcribeChunk(filePath: String): String {
        return "Mock transcript for chunk at $filePath"
    }
}