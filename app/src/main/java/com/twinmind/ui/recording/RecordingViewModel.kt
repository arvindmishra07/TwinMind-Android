package com.twinmind.ui.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.twinmind.data.repository.MeetingRepository
import com.twinmind.domain.model.Meeting
import com.twinmind.service.RecordingService
import com.twinmind.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingUiState(
    val meetingId: String = "",
    val statusText: String = "Recording...",
    val timer: String = "00:00",
    val isRecording: Boolean = true,
    val isPaused: Boolean = false,
    val isStopped: Boolean = false,
    val showSilenceWarning: Boolean = false
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val meetingRepository: MeetingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val state = intent.getStringExtra(Constants.EXTRA_RECORDING_STATE) ?: return
            val meetingId = intent.getStringExtra(Constants.EXTRA_MEETING_ID) ?: ""
            val timer = intent.getStringExtra(Constants.EXTRA_TIMER) ?: "00:00"

            _uiState.update { current ->
                when (state) {
                    "Recording" -> current.copy(
                        meetingId = meetingId,
                        statusText = "Recording...",
                        timer = timer,
                        isRecording = true,
                        isPaused = false,
                        isStopped = false
                    )
                    "Paused" -> current.copy(
                        meetingId = meetingId,
                        timer = timer,
                        isRecording = false,
                        isPaused = true,
                        isStopped = false
                    )
                    "Stopped" -> current.copy(
                        meetingId = meetingId,
                        timer = timer,
                        isRecording = false,
                        isPaused = false,
                        isStopped = true
                    )
                    "Error" -> current.copy(
                        meetingId = meetingId,
                        statusText = "Error occurred",
                        isRecording = false,
                        isStopped = true
                    )
                    else -> current
                }
            }
        }
    }

    init {
        LocalBroadcastManager.getInstance(context)
            .registerReceiver(
                broadcastReceiver,
                IntentFilter(Constants.BROADCAST_RECORDING_STATE)
            )
    }

    fun stopRecording() {
        val intent = Intent(context, RecordingService::class.java).apply {
            action = Constants.ACTION_STOP_RECORDING
        }
        context.startService(intent)
    }

    fun getMeetingById(meetingId: String): Flow<Meeting?> = flow {
        emit(meetingRepository.getMeetingById(meetingId))
    }

    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(context)
            .unregisterReceiver(broadcastReceiver)
    }
}