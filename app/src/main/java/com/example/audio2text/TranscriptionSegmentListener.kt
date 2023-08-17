package com.example.audio2text

interface TranscriptionSegmentListener {
    fun onNewSegment(segment: String)
}