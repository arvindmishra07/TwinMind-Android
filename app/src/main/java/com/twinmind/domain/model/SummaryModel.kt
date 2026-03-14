package com.twinmind.domain.model

data class MeetingSummary(
    val meetingId: String,
    val title: String = "",
    val summary: String = "",
    val actionItems: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList(),
    val status: SummaryStatus = SummaryStatus.PENDING,
    val errorMessage: String? = null
)

enum class SummaryStatus {
    PENDING,
    GENERATING,
    COMPLETED,
    FAILED
}