package com.example.audio2text

import androidx.annotation.Keep

@Keep
interface InferenceStoppedListener {
    fun onInferenceStopped()
}