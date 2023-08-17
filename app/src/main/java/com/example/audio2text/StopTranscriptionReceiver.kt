package com.example.audio2text

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

class StopTranscriptionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_TRANSCRIPTION") {
            val workIdString = intent.getStringExtra("WORK_ID")
            val workId = UUID.fromString(workIdString)
            WorkManager.getInstance(context).cancelWorkById(workId)
        }
    }
}