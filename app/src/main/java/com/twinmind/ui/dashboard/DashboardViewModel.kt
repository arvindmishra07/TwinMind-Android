package com.twinmind.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.data.repository.MeetingRepository
import com.twinmind.domain.model.Meeting
import com.twinmind.service.RecordingService
import com.twinmind.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    val meetings = meetingRepository.getAllMeetings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun startRecording() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = Constants.ACTION_START_RECORDING
        }
        context.startForegroundService(intent)
    }
}