package com.twinmind.ui.summary

import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.twinmind.domain.model.MeetingSummary
import com.twinmind.ui.theme.*

@Composable
fun SummaryScreen(
    navController: NavController,
    meetingId: String,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    LaunchedEffect(meetingId) {
        viewModel.loadMeeting(meetingId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Trigger summary generation if nothing is happening
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading && uiState.summary == null) {
            kotlinx.coroutines.delay(3000)
            if (uiState.summary == null ||
                uiState.summary?.status == com.twinmind.domain.model.SummaryStatus.PENDING) {
                viewModel.generateSummaryNow(meetingId)
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TwinMindBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar
            SummaryTopBar(
                title = uiState.meeting?.title ?: "Meeting Summary",
                onBack = { navController.popBackStack() },
                onRetry = { viewModel.retrySummary(meetingId) },
                showRetry = uiState.errorMessage != null
            )

            when {
                uiState.isLoading -> LoadingState(streamingText = uiState.streamingText)
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage!!,
                    onRetry = { viewModel.retrySummary(meetingId) }
                )
                uiState.summary != null -> SummaryContent(
                    summary = uiState.summary!!,
                    transcript = uiState.fullTranscript
                )
                else -> LoadingState(streamingText = uiState.streamingText)
            }
        }
    }
}

@Composable
fun SummaryTopBar(
    title: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    showRetry: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TwinMindTextSecondary
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TwinMindTextPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (showRetry) {
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = TwinMindAccentBlue
                )
            }
        }
    }
}

@Composable
fun LoadingState(streamingText: String = "") {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        CircularProgressIndicator(
            color = TwinMindAccentBlue,
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Generating summary...",
            style = MaterialTheme.typography.bodyLarge,
            color = TwinMindTextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This may take a moment",
            style = MaterialTheme.typography.bodySmall,
            color = TwinMindTextTertiary
        )

        // Show streaming text as it comes in
        if (streamingText.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TwinMindCardBg),
                border = BorderStroke(1.dp, TwinMindCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Blinking cursor indicator
                        val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "blink"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(TwinMindAccentBlue.copy(alpha = alpha))
                        )
                        Text(
                            text = "AI is writing...",
                            style = MaterialTheme.typography.labelMedium,
                            color = TwinMindAccentBlue
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = streamingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TwinMindTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "⚠️", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Summary generation failed",
            style = MaterialTheme.typography.headlineSmall,
            color = TwinMindTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TwinMindTextTertiary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TwinMindAccentBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
fun SummaryContent(summary: MeetingSummary, transcript: String) {
    var showTranscript by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title Section
        if (summary.title.isNotBlank()) {
            SummarySection(
                title = "📋 Title",
                accentColor = TwinMindAccentBlue
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TwinMindTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Summary Section
        if (summary.summary.isNotBlank()) {
            SummarySection(
                title = "📝 Summary",
                accentColor = TwinMindAccentBlueLight
            ) {
                Text(
                    text = summary.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TwinMindTextSecondary,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                )
            }
        }

        // Action Items Section
        if (summary.actionItems.isNotEmpty()) {
            SummarySection(
                title = "✅ Action Items",
                accentColor = TwinMindGreen
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.actionItems.forEachIndexed { index, item ->
                        ActionItemRow(index = index + 1, text = item)
                    }
                }
            }
        }

        // Key Points Section
        if (summary.keyPoints.isNotEmpty()) {
            SummarySection(
                title = "💡 Key Points",
                accentColor = TwinMindRed
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    summary.keyPoints.forEach { point ->
                        KeyPointRow(text = point)
                    }
                }
            }
        }

        // Transcript toggle
        if (transcript.isNotBlank()) {
            OutlinedButton(
                onClick = { showTranscript = !showTranscript },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TwinMindCardBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TwinMindTextSecondary
                )
            ) {
                Text(if (showTranscript) "Hide Transcript" else "View Full Transcript")
            }

            AnimatedVisibility(visible = showTranscript) {
                SummarySection(title = "🎙️ Transcript", accentColor = TwinMindTextTertiary) {
                    Text(
                        text = transcript,
                        style = MaterialTheme.typography.bodySmall,
                        color = TwinMindTextTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SummarySection(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TwinMindCardBg),
        border = BorderStroke(1.dp, TwinMindCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TwinMindTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ActionItemRow(index: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(TwinMindGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = TwinMindGreen,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TwinMindTextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun KeyPointRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(y = 7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(TwinMindAccentBlue)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TwinMindTextSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}