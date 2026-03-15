package com.twinmind

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.twinmind.data.local.TwinMindDatabase
import com.twinmind.worker.FinalizeRecordingWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TwinMindApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var database: TwinMindDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        recoverInterruptedSessions()
    }

    private fun recoverInterruptedSessions() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find any meetings still in RECORDING state — these were interrupted
                val activeMeeting = database.meetingDao().getActiveMeeting()
                if (activeMeeting != null) {
                    Log.d("TwinMindApp", "Found interrupted session: ${activeMeeting.id}")
                    // Enqueue finalize worker to recover
                    FinalizeRecordingWorker.enqueue(
                        this@TwinMindApp,
                        activeMeeting.id,
                        activeMeeting.audioFolderPath
                    )
                }
            } catch (e: Exception) {
                Log.e("TwinMindApp", "Recovery failed: ${e.message}")
            }
        }
    }
}