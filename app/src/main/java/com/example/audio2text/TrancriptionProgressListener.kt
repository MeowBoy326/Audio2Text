package com.example.audio2text

import androidx.annotation.Keep

@Keep
interface TranscriptionProgressListener {
    suspend fun onTranscriptionProgress(progress: Int)
    fun onTranscriptionProgressNonSuspend(progress: Int)
}