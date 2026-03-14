package com.twinmind.ui.recording

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.twinmind.ui.navigation.Screen
import com.twinmind.ui.theme.*

@Composable
fun RecordingScreen(
    navController: NavController,
    meetingId: String,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate to summary when stopped
    LaunchedEffect(uiState.isStopped, uiState.meetingId) {
        if (uiState.isStopped && uiState.meetingId.isNotEmpty()) {
            navController.navigate(Screen.Summary.createRoute(uiState.meetingId)) {
                popUpTo(Screen.Dashboard.route)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TwinMindDarkNavy,
                        TwinMindBlack
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            RecordingTopBar(onBack = { navController.popBackStack() })

            Spacer(modifier = Modifier.weight(0.5f))

            // Status
            StatusChip(statusText = uiState.statusText, isPaused = uiState.isPaused)

            Spacer(modifier = Modifier.height(48.dp))

            // Timer
            Text(
                text = uiState.timer,
                fontSize = 64.sp,
                fontWeight = FontWeight.Thin,
                color = TwinMindTextPrimary,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (uiState.isPaused) "Paused" else "Recording in progress",
                style = MaterialTheme.typography.bodyMedium,
                color = TwinMindTextTertiary
            )

            Spacer(modifier = Modifier.weight(1f))

            // Waveform animation
            if (uiState.isRecording) {
                WaveformAnimation()
                Spacer(modifier = Modifier.height(48.dp))
            }

            // Stop button
            StopButton(onClick = { viewModel.stopRecording() })

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
fun RecordingTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            text = "Recording",
            style = MaterialTheme.typography.titleLarge,
            color = TwinMindTextPrimary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun StatusChip(statusText: String, isPaused: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "status")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPaused) 1f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(50.dp),
        color = if (isPaused)
            TwinMindTextTertiary.copy(alpha = 0.15f)
        else
            TwinMindRed.copy(alpha = 0.15f),
        modifier = Modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPaused) TwinMindTextTertiary
                        else TwinMindRed.copy(alpha = alpha)
                    )
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (isPaused) TwinMindTextTertiary else TwinMindRed,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun WaveformAnimation() {
    val bars = 28
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(48.dp)
    ) {
        repeat(bars) { index ->
            val delay = (index * 50) % 800
            val height by infiniteTransition.animateFloat(
                initialValue = 4f,
                targetValue = (20 + (index % 5) * 8).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600 + (index % 4) * 100,
                        delayMillis = delay,
                        easing = EaseInOut
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                TwinMindAccentBlue,
                                TwinMindRed
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun StopButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "stop_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        Box(
            modifier = Modifier
                .size(90.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(TwinMindRed.copy(alpha = 0.12f))
        )
        // Stop FAB
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            containerColor = TwinMindRed,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop Recording",
                modifier = Modifier.size(30.dp)
            )
        }
    }
}