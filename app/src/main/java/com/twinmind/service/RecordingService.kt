package com.twinmind.service

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.twinmind.data.local.dao.AudioChunkDao
import com.twinmind.data.local.dao.MeetingDao
import com.twinmind.data.local.entity.AudioChunkEntity
import com.twinmind.data.local.entity.MeetingEntity
import com.twinmind.domain.model.PauseReason
import com.twinmind.domain.model.RecordingState
import com.twinmind.util.AudioUtils
import com.twinmind.util.Constants
import com.twinmind.worker.TranscriptionWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var meetingDao: MeetingDao
    @Inject lateinit var audioChunkDao: AudioChunkDao

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var chunkRecorder: ChunkRecorder? = null
    private var notificationHelper: NotificationHelper? = null
    private var audioFocusHandler: AudioFocusHandler? = null
    private var headsetReceiver: HeadsetReceiver? = null
    private var phoneCallReceiver: PhoneCallReceiver? = null

    private var currentMeetingId: String? = null
    private var recordingState: RecordingState = RecordingState.Idle
    private var startTimeMs = 0L
    private var elapsedMs = 0L
    private var timerJob: Job? = null
    private var pausedByPhoneCall = false
    private var pausedByAudioFocus = false

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        setupAudioFocusHandler()
        setupHeadsetReceiver()
        setupPhoneCallReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_RECORDING -> startRecording()
            Constants.ACTION_STOP_RECORDING -> stopRecording()
            Constants.ACTION_PAUSE_RECORDING -> {
                val reason = intent.getStringExtra("reason")
                if (reason == "PHONE_CALL") pauseForPhoneCall()
                else pauseRecording(PauseReason.MANUAL)
            }
            Constants.ACTION_RESUME_RECORDING -> {
                val reason = intent.getStringExtra("reason")
                if (reason == "PHONE_CALL_ENDED" && pausedByPhoneCall) resumeFromPhoneCall()
                else if (pausedByAudioFocus) resumeFromAudioFocus()
                else resumeRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (!AudioUtils.hasEnoughStorage(this)) {
            broadcastState(RecordingState.Error("Recording stopped - Low storage"))
            stopSelf()
            return
        }

        val meetingId = AudioUtils.generateMeetingId()
        currentMeetingId = meetingId
        startTimeMs = SystemClock.elapsedRealtime()

        serviceScope.launch {
            meetingDao.insert(
                MeetingEntity(
                    id = meetingId,
                    title = AudioUtils.generateMeetingTitle(),
                    startTime = System.currentTimeMillis(),
                    audioFolderPath = AudioUtils.getRecordingsDir(
                        this@RecordingService, meetingId
                    ).absolutePath
                )
            )
        }

        chunkRecorder = ChunkRecorder(
            context = this,
            meetingId = meetingId,
            onChunkReady = { file, index -> handleChunkReady(file, index, meetingId) },
            onSilenceDetected = { handleSilenceDetected() },
            onStorageLow = { handleStorageLow() },
            onAmplitudeUpdate = { /* can use for UI waveform later */ }
        )

        chunkRecorder?.startRecording()
        audioFocusHandler?.requestFocus()

        recordingState = RecordingState.Recording(meetingId)
        startForeground(
            Constants.RECORDING_NOTIFICATION_ID,
            notificationHelper!!.buildRecordingNotification("Recording...", "00:00")
        )
        startTimer()
        broadcastState(recordingState)
    }

    private fun handleChunkReady(file: File, index: Int, meetingId: String) {
        serviceScope.launch {
            val chunkId = UUID.randomUUID().toString()
            audioChunkDao.insert(
                AudioChunkEntity(
                    id = chunkId,
                    meetingId = meetingId,
                    chunkIndex = index,
                    filePath = file.absolutePath,
                    startTime = System.currentTimeMillis()
                )
            )
            // Enqueue transcription for this chunk
            TranscriptionWorker.enqueue(this@RecordingService, chunkId, meetingId)
        }
    }

    private fun handleSilenceDetected() {
        notificationHelper?.updateNotification(
            "No audio detected - Check microphone",
            formatElapsed()
        )
    }

    private fun handleStorageLow() {
        recordingState = RecordingState.Error("Recording stopped - Low storage")
        broadcastState(recordingState)
        notificationHelper?.updateNotification("Recording stopped - Low storage", formatElapsed())
        stopRecording()
    }

    private fun pauseForPhoneCall() {
        if (recordingState !is RecordingState.Recording) return
        pausedByPhoneCall = true
        pauseRecording(PauseReason.PHONE_CALL)
    }

    private fun resumeFromPhoneCall() {
        pausedByPhoneCall = false
        resumeRecording()
    }

    private fun pauseForAudioFocus() {
        if (recordingState !is RecordingState.Recording) return
        pausedByAudioFocus = true
        pauseRecording(PauseReason.AUDIO_FOCUS_LOST)
    }

    private fun resumeFromAudioFocus() {
        pausedByAudioFocus = false
        resumeRecording()
    }

    private fun pauseRecording(reason: PauseReason) {
        chunkRecorder?.pauseRecording()
        timerJob?.cancel()
        val meetingId = currentMeetingId ?: return
        recordingState = RecordingState.Paused(meetingId, reason, elapsedMs)
        notificationHelper?.updateNotification(
            reason.displayText,
            formatElapsed(),
            showResume = reason != PauseReason.PHONE_CALL
        )
        broadcastState(recordingState)
    }

    private fun resumeRecording() {
        chunkRecorder?.resumeRecording()
        val meetingId = currentMeetingId ?: return
        recordingState = RecordingState.Recording(meetingId, elapsedMs)
        notificationHelper?.updateNotification("Recording...", formatElapsed())
        startTimer()
        broadcastState(recordingState)
    }

    private fun stopRecording() {
        timerJob?.cancel()
        val lastFile = chunkRecorder?.stopRecording()
        val meetingId = currentMeetingId ?: run { stopSelf(); return }

        // Handle last chunk
        lastFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                serviceScope.launch {
                    val chunkId = UUID.randomUUID().toString()
                    val nextIndex = (chunkRecorder?.getCurrentChunkIndex() ?: 0)
                    audioChunkDao.insert(
                        AudioChunkEntity(
                            id = chunkId,
                            meetingId = meetingId,
                            chunkIndex = nextIndex,
                            filePath = file.absolutePath,
                            startTime = System.currentTimeMillis()
                        )
                    )
                    TranscriptionWorker.enqueue(this@RecordingService, chunkId, meetingId)
                }
            }
        }

        serviceScope.launch {
            meetingDao.updateMeetingStatus(
                id = meetingId,
                status = "COMPLETED",
                endTime = System.currentTimeMillis(),
                duration = elapsedMs
            )
        }

        audioFocusHandler?.abandonFocus()
        recordingState = RecordingState.Stopped(meetingId)
        broadcastState(recordingState)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setupAudioFocusHandler() {
        audioFocusHandler = AudioFocusHandler(
            context = this,
            onFocusLost = { pauseForAudioFocus() },
            onFocusGained = { if (pausedByAudioFocus) resumeFromAudioFocus() }
        )
    }

    private fun setupHeadsetReceiver() {
        headsetReceiver = HeadsetReceiver { message ->
            notificationHelper?.updateNotification(message, formatElapsed())
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(android.media.AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        }
        registerReceiver(headsetReceiver, filter)
    }

    private fun setupPhoneCallReceiver() {
        phoneCallReceiver = PhoneCallReceiver()
        val filter = IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneCallReceiver, filter)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                elapsedMs += 1000
                val state = recordingState
                if (state is RecordingState.Recording) {
                    recordingState = state.copy(elapsedMs = elapsedMs)
                }
                notificationHelper?.updateNotification(
                    getStatusText(),
                    formatElapsed()
                )
                broadcastState(recordingState)
            }
        }
    }

    private fun broadcastState(state: RecordingState) {
        val intent = Intent(Constants.BROADCAST_RECORDING_STATE).apply {
            putExtra(Constants.EXTRA_RECORDING_STATE, state.javaClass.simpleName)
            putExtra(Constants.EXTRA_MEETING_ID, currentMeetingId)
            putExtra(Constants.EXTRA_TIMER, formatElapsed())
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun getStatusText(): String = when (val s = recordingState) {
        is RecordingState.Recording -> "Recording..."
        is RecordingState.Paused -> s.reason.displayText
        is RecordingState.Error -> s.message
        else -> "Stopped"
    }

    private fun formatElapsed(): String = AudioUtils.formatDuration(elapsedMs)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        chunkRecorder?.stopRecording()
        audioFocusHandler?.abandonFocus()
        try {
            unregisterReceiver(headsetReceiver)
            unregisterReceiver(phoneCallReceiver)
        } catch (e: Exception) { }
        serviceScope.cancel()
    }
}