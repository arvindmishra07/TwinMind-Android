package com.twinmind.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.twinmind.util.AudioUtils
import com.twinmind.util.Constants
import kotlinx.coroutines.*
import java.io.File

class ChunkRecorder(
    private val context: Context,
    private val meetingId: String,
    private val onChunkReady: (File, Int) -> Unit,
    private val onSilenceDetected: () -> Unit,
    private val onStorageLow: () -> Unit,
    private val onAmplitudeUpdate: (Int) -> Unit
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentChunkIndex = 0
    private var isRecording = false
    private var chunkJob: Job? = null
    private var silenceJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var silenceDurationMs = 0L
    private var lastAmplitudeCheckTime = 0L

    fun startRecording() {
        if (!AudioUtils.hasEnoughStorage(context)) {
            onStorageLow()
            return
        }
        isRecording = true
        startNextChunk()
        startSilenceDetection()
    }

    private fun startNextChunk() {
        if (!isRecording) return

        val chunkFile = AudioUtils.createChunkFile(context, meetingId, currentChunkIndex)
        startMediaRecorder(chunkFile)

        chunkJob = scope.launch {
            delay(Constants.CHUNK_DURATION_MS - Constants.CHUNK_OVERLAP_MS)
            if (isRecording) {
                val nextIndex = currentChunkIndex + 1
                val nextFile = AudioUtils.createChunkFile(context, meetingId, nextIndex)

                // Start next recorder before stopping current (overlap)
                val nextRecorder = createMediaRecorder(nextFile)
                nextRecorder?.start()

                delay(Constants.CHUNK_OVERLAP_MS)

                // Stop current and notify
                val finishedFile = chunkFile
                val finishedIndex = currentChunkIndex
                stopCurrentRecorder()

                currentChunkIndex = nextIndex
                mediaRecorder = nextRecorder

                if (!AudioUtils.hasEnoughStorage(context)) {
                    onStorageLow()
                    stopRecording()
                    return@launch
                }

                onChunkReady(finishedFile, finishedIndex)
                startNextChunkTimer()
            }
        }
    }

    private fun startNextChunkTimer() {
        chunkJob = scope.launch {
            delay(Constants.CHUNK_DURATION_MS - Constants.CHUNK_OVERLAP_MS)
            if (isRecording) {
                val currentFile = AudioUtils.createChunkFile(context, meetingId, currentChunkIndex)
                val nextIndex = currentChunkIndex + 1
                val nextFile = AudioUtils.createChunkFile(context, meetingId, nextIndex)

                val nextRecorder = createMediaRecorder(nextFile)
                nextRecorder?.start()
                delay(Constants.CHUNK_OVERLAP_MS)

                val finishedIndex = currentChunkIndex
                stopCurrentRecorder()
                currentChunkIndex = nextIndex
                mediaRecorder = nextRecorder

                if (!AudioUtils.hasEnoughStorage(context)) {
                    onStorageLow()
                    stopRecording()
                    return@launch
                }

                onChunkReady(currentFile, finishedIndex)
                startNextChunkTimer()
            }
        }
    }

    private fun startSilenceDetection() {
        silenceJob = scope.launch {
            while (isRecording) {
                delay(500)
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                onAmplitudeUpdate(amplitude)
                if (amplitude < Constants.SILENCE_THRESHOLD) {
                    silenceDurationMs += 500
                    if (silenceDurationMs >= Constants.SILENCE_DETECTION_MS) {
                        onSilenceDetected()
                        silenceDurationMs = 0
                    }
                } else {
                    silenceDurationMs = 0
                }
            }
        }
    }

    private fun startMediaRecorder(file: File) {
        mediaRecorder = createMediaRecorder(file)
        mediaRecorder?.start()
    }

    private fun createMediaRecorder(file: File): MediaRecorder? {
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun stopCurrentRecorder() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pauseRecording() {
        isRecording = false
        chunkJob?.cancel()
        silenceJob?.cancel()
        stopCurrentRecorder()
    }

    fun resumeRecording() {
        isRecording = true
        currentChunkIndex++
        startNextChunk()
        startSilenceDetection()
    }

    fun stopRecording(): File? {
        isRecording = false
        chunkJob?.cancel()
        silenceJob?.cancel()
        val lastFile = AudioUtils.createChunkFile(context, meetingId, currentChunkIndex)
        stopCurrentRecorder()
        scope.cancel()
        return if (lastFile.exists() && lastFile.length() > 0) lastFile else null
    }

    fun getCurrentChunkIndex() = currentChunkIndex
}