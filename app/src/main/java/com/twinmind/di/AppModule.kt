package com.twinmind.di

import android.content.Context
import androidx.room.Room
import com.twinmind.data.local.TwinMindDatabase
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.local.dao.MeetingDao
import com.twinmind.data.local.dao.SummaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.google.gson.Gson

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TwinMindDatabase =
        Room.databaseBuilder(
            context,
            TwinMindDatabase::class.java,
            "twinmind_db"
        ).build()

    @Provides
    fun provideMeetingDao(db: TwinMindDatabase): MeetingDao = db.meetingDao()

    @Provides
    fun provideAudioChunkDao(db: TwinMindDatabase): AudioChunkDao = db.audioChunkDao()

    @Provides
    fun provideSummaryDao(db: TwinMindDatabase): SummaryDao = db.summaryDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}