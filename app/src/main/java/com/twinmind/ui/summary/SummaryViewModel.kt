package com.twinmind.ui.summary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.data.repository.MeetingRepository
import com.twinmind.data.repository.SummaryRepository
import com.twinmind.data.repository.TranscriptionRepository
import com.twinmind.domain.model.Meeting
import com.twinmind.domain.model.MeetingSummary
import com.twinmind.domain.model.SummaryStatus
import com.twinmind.worker.SummaryWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val meeting: Meeting? = null,
    val summary: MeetingSummary? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val fullTranscript: String = ""
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val summaryRepository: SummaryRepository,
    private val meetingRepository: MeetingRepository,
    private val transcriptionRepository: TranscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    fun loadMeeting(meetingId: String) {
        viewModelScope.launch {
            val meeting = meetingRepository.getMeetingById(meetingId)
            _uiState.update { it.copy(meeting = meeting) }

            // Observe summary flow
            summaryRepository.getSummaryFlow(meetingId)
                .collect { summary ->
                    _uiState.update { current ->
                        current.copy(
                            summary = summary,
                            isLoading = summary == null ||
                                    summary.status == SummaryStatus.GENERATING,
                            errorMessage = if (summary?.status == SummaryStatus.FAILED)
                                summary.errorMessage ?: "Failed to generate summary"
                            else null
                        )
                    }
                }
        }

        // Load transcript
        viewModelScope.launch {
            val transcript = transcriptionRepository.getFullTranscript(meetingId)
            _uiState.update { it.copy(fullTranscript = transcript) }
        }
    }

    fun retrySummary(meetingId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        SummaryWorker.enqueue(context, meetingId)
    }

    fun generateSummaryNow(meetingId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val transcript = transcriptionRepository.getFullTranscript(meetingId)
                if (transcript.isNotBlank()) {
                    summaryRepository.generateSummary(meetingId, transcript)
                } else {
                    SummaryWorker.enqueue(context, meetingId)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to generate summary"
                    )
                }
            }
        }
    }
}