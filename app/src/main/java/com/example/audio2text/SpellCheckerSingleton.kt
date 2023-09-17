package com.example.audio2text

import androidx.lifecycle.MutableLiveData

object SpellCheckerSingleton {
    var spellChecker: SpellChecker? = null
    var isSpellCheckerReady = MutableLiveData<Boolean>()
    var spellCheckerReadyListener: SpellCheckerReadyListener? = null

    fun release() {
        spellChecker?.unload()
        spellChecker = null
    }
}