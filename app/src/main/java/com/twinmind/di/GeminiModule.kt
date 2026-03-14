package com.twinmind.di

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.twinmind.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeminiModule {

    @Provides
    @Singleton
    @Named("transcription")
    fun provideTranscriptionModel(): GenerativeModel =
        GenerativeModel(
            modelName = "gemini-2.5-flash-preview-04-17",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0f
            }
        )

    @Provides
    @Singleton
    @Named("summary")
    fun provideSummaryModel(): GenerativeModel =
        GenerativeModel(
            modelName = "gemini-2.5-flash-preview-04-17",
            apiKey = BuildConfig.GEMINI_API_KEY,
            generationConfig = generationConfig {
                temperature = 0.7f
            }
        )
}