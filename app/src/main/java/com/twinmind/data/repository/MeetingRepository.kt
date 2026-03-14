package com.twinmind.data.repository

import com.twinmind.data.local.dao.MeetingDao
import com.twinmind.data.local.entity.MeetingEntity
import com.twinmind.domain.model.Meeting
import com.twinmind.domain.model.MeetingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepository @Inject constructor(
    private val meetingDao: MeetingDao
) {
    fun getAllMeetings(): Flow<List<Meeting>> =
        meetingDao.getAllMeetings().map { list ->
            list.map { it.toDomain() }
        }

    suspend fun getMeetingById(id: String): Meeting? =
        meetingDao.getMeetingById(id)?.toDomain()

    suspend fun getActiveMeeting(): Meeting? =
        meetingDao.getActiveMeeting()?.toDomain()
}

fun MeetingEntity.toDomain() = Meeting(
    id = id,
    title = title,
    startTime = startTime,
    endTime = endTime,
    status = try { MeetingStatus.valueOf(status) } catch (e: Exception) { MeetingStatus.COMPLETED },
    duration = duration,
    audioFolderPath = audioFolderPath
)