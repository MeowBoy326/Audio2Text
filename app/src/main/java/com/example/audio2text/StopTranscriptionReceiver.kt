package com.example.audio2text

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.Keep
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Keep
class StopTranscriptionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "STOP_TRANSCRIPTION") {
            val preferences = context.getSharedPreferences(
                "MyPreferences",
                Context.MODE_PRIVATE
            )

            val workId = preferences.getString("WorkRequestId", null)
            if (workId != null) {
                val workRequestId = UUID.fromString(workId)

                WorkManager.getInstance(context)
                    .cancelWorkById(workRequestId)
            }
            val tempFilePath = preferences.getString("tempFilePath", "")
            val tempFile = tempFilePath?.let { File(it) }
            if (tempFile?.exists() == true) {
                tempFile.delete()
            }

            if (HomeViewModelHolder.viewModel.reachInference.value == true) {
                Log.d("HomeFragment", "stopped during inference called")
                Log.d("Whisper", "Annuler le travail de transcription")
                preferences.edit()?.putBoolean("isRunning", false)?.apply()
                preferences.edit()?.putBoolean("isGoingToStop", true)?.apply()
                CoroutineScope(Dispatchers.IO).launch {
                    TranscriptionWorker.releaseResources()
                }

                //preferences.edit().putBoolean("stoppedDuringInference", false).apply()
                val showText =
                    "Arrêt en cours. Veuillez patienter..."
                Log.d("Whisper", "Call to stop during inference")
                HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText)
            } else {
                preferences.edit()?.putBoolean("isRunning", false)?.apply()
                preferences.edit()?.putBoolean("isFullyStopped", true)?.apply()
                CoroutineScope(Dispatchers.IO).launch {
                    TranscriptionWorker.releaseResources(false)
                }
            }
        }
    }
}