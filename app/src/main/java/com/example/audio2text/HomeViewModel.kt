package com.example.audio2text

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.text.SpannableString
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    // Stockez l'état de vos éléments de vue ici, par exemple:
    val transcription = MutableLiveData<String?>()
    val isTranscriptionTextEnabled = MutableLiveData<Boolean>()
    val isStopTranscriptionButtonVisible = MutableLiveData<Boolean>()
    val isSelectFileButtonVisible = MutableLiveData<Boolean>()
    val isHeaderVisible = MutableLiveData<Boolean>()
    val isTranscriptionTextVisible = MutableLiveData<Boolean>()
    val isSelectFileButtonEnabled = MutableLiveData<Boolean>()
    val isRunningLiveData = MutableLiveData<Boolean>()
    val isFullyStoppedLiveData = MutableLiveData<Boolean>()
    val isFullySuccessfulLiveData = MutableLiveData<Boolean>()
    val isGoingToStopLiveData = MutableLiveData<Boolean>()
    var spellChecker: SpellChecker? = null
    val spellCheckerReady = MutableLiveData<Boolean>()
    val cursor = try {
        application.applicationContext.contentResolver.query(
            TranscriptionContentProvider.CONTENT_URI,
            null,
            null,
            null,
            null
        )
    } catch (e: Exception) {
        null
    }

    //val isRunning = MutableLiveData<Boolean>()
    //private val contentResolver = application.contentResolver

    fun updateLiveData(key: String, value: Boolean) {
        when (key) {
            "isRunning" -> isRunningLiveData.postValue(value)
            "isFullyStopped" -> isFullyStoppedLiveData.postValue(value)
            "isFullySuccessful" -> isFullySuccessfulLiveData.postValue(value)
            "isGoingToStop" -> isGoingToStopLiveData.postValue(value)
        }
    }

    init {
        // Obtenez une référence à l'application personnalisée
        Log.d("HomeViewModel", "Call to init")
        val preferences = application.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val isRunning = preferences.getBoolean("isRunning", false)
        val isGoingToStop = preferences.getBoolean("isGoingToStop", false)
        val isFullyStopped = preferences.getBoolean("isFullyStopped", false)
        val isFullySuccessful = preferences.getBoolean("isFullySuccessful", false)
        Log.d("SharePreferences", "isRunning: $isRunning")
        Log.d("SharePreferences", "isGoingToStop: $isGoingToStop")
        Log.d("SharePreferences", "isFullyStopped: $isFullyStopped")
        Log.d("SharePreferences", "isFullySuccessful: $isFullySuccessful")
        if (isRunning) {
            isRunningLiveData.postValue(true)
        } else if (isGoingToStop) {
            isGoingToStopLiveData.postValue(true)
        } else if (isFullyStopped) {
            isFullyStoppedLiveData.postValue(true)
        } else if (isFullySuccessful) {
            isFullySuccessfulLiveData.postValue(true)
        }
        loadTranscription()
    }

    fun initSpellChecker(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            if (spellChecker == null) {
                val preferences =
                    context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
                val selectedDictionaryId = preferences.getString("selectedDictionaryId", "")
                if (selectedDictionaryId != null) {
                    val path =
                        DictionaryManager.getDictionaryItemById(selectedDictionaryId)?.fileRelativePath
                    spellChecker = SpellChecker.loadFrequencyDictionary(
                        context,
                        path!!
                    ).withEditDistanceFunction(::DamerauLevenshtein).build()
                }
            }
            spellCheckerReady.postValue(true)
        }
    }

    fun loadTranscription() {
        if (cursor != null && cursor.moveToFirst()) {
            cursor.use { cursorUsed ->
                val columnIndex = cursorUsed.getColumnIndex("transcription")
                val transcriptionText = cursorUsed.getString(columnIndex)
                Log.d("Whisper", "Call to load transcription : $transcriptionText")
                CoroutineScope(Dispatchers.IO).launch {
                    TranscriptionContentProvider._lastTranscription.emit(Transcription(content = transcriptionText))  // Émettre la nouvelle transcription
                }
            }
        }
    }
}