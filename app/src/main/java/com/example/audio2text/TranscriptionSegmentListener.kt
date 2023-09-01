package com.example.audio2text

import androidx.annotation.Keep

@Keep
interface TranscriptionSegmentListener {
    fun onNewSegment(segment: String)
}