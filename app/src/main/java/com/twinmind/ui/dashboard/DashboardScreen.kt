package com.twinmind.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import com.twinmind.domain.model.Meeting
import com.twinmind.domain.model.MeetingStatus
import com.twinmind.ui.navigation.Screen
import com.twinmind.ui.theme.*
import com.twinmind.util.AudioUtils
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            viewModel.startRecording()
            navController.navigate(Screen.Recording.createRoute("new"))
        } else {
            android.widget.Toast.makeText(
                context,
                "Microphone permission is required to record",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TwinMindBlack)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DashboardTopBar()

            if (meetings.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(meetings, key = { it.id }) { meeting ->
                        MeetingCard(
                            meeting = meeting,
                            onClick = {
                                if (meeting.status == MeetingStatus.RECORDING) {
                                    navController.navigate(
                                        Screen.Recording.createRoute(meeting.id)
                                    )
                                } else {
                                    navController.navigate(
                                        Screen.Summary.createRoute(meeting.id)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // FAB Record Button
        RecordFab(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp),
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                )
            }
        )
    }
}

@Composable
fun DashboardTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "TwinMind",
                style = MaterialTheme.typography.headlineMedium,
                color = TwinMindTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Your AI Meeting Assistant",
                style = MaterialTheme.typography.bodySmall,
                color = TwinMindTextTertiary
            )
        }
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(TwinMindAccentBlue, TwinMindAccentBlueLight)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun MeetingCard(meeting: Meeting, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(meeting.startTime))

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TwinMindCardBg),
        border = BorderStroke(1.dp, TwinMindCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (meeting.status) {
                            MeetingStatus.RECORDING -> TwinMindRed.copy(alpha = 0.15f)
                            MeetingStatus.COMPLETED -> TwinMindAccentBlue.copy(alpha = 0.15f)
                            MeetingStatus.FAILED -> TwinMindTextTertiary.copy(alpha = 0.15f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = when (meeting.status) {
                        MeetingStatus.RECORDING -> TwinMindRed
                        MeetingStatus.COMPLETED -> TwinMindAccentBlue
                        MeetingStatus.FAILED -> TwinMindTextTertiary
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TwinMindTextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = TwinMindTextTertiary
                    )
                    if (meeting.duration > 0) {
                        Text(
                            text = "•",
                            color = TwinMindTextTertiary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = AudioUtils.formatDuration(meeting.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = TwinMindTextTertiary
                        )
                    }
                }
            }

            // Status badge
            when (meeting.status) {
                MeetingStatus.RECORDING -> {
                    RecordingBadge()
                }
                MeetingStatus.COMPLETED -> {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelMedium,
                        color = TwinMindAccentBlue
                    )
                }
                MeetingStatus.FAILED -> {
                    Text(
                        text = "Failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = TwinMindRed
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(TwinMindRed.copy(alpha = alpha))
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelMedium,
            color = TwinMindRed,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RecordFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(TwinMindRed.copy(alpha = 0.15f))
        )
        // Inner button
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            containerColor = TwinMindRed,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Start Recording",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(TwinMindCardBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = TwinMindTextTertiary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No meetings yet",
            style = MaterialTheme.typography.headlineSmall,
            color = TwinMindTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the button below to start recording",
            style = MaterialTheme.typography.bodyMedium,
            color = TwinMindTextTertiary
        )
    }
}