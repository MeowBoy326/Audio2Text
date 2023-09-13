package com.example.audio2text

import androidx.annotation.Keep

@Keep
interface SpellCheckerReadyListener {
    fun onSpellCheckerReady(segment: String)
}