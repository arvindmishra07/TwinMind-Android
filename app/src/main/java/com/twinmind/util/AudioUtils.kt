package com.twinmind.util

import android.media.AudioFormat
import android.media.MediaRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AudioUtils {

    fun createChunkFile(context: android.content.Context, meetingId: String, chunkIndex: Int): File {
        val dir = File(context.filesDir, "recordings/$meetingId")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "chunk_${chunkIndex.toString().padStart(4, '0')}.m4a")
    }

    fun getRecordingsDir(context: android.content.Context, meetingId: String): File {
        return File(context.filesDir, "recordings/$meetingId")
    }

    fun hasEnoughStorage(context: android.content.Context): Boolean {
        val stat = android.os.StatFs(context.filesDir.path)
        val available = stat.availableBlocksLong * stat.blockSizeLong
        return available > Constants.MIN_STORAGE_BYTES
    }

    fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = ms / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    fun generateMeetingTitle(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        return "Meeting - ${sdf.format(Date())}"
    }

    fun generateMeetingId(): String = java.util.UUID.randomUUID().toString()
}