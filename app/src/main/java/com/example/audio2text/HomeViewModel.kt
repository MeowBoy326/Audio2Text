package com.example.audio2text

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.concurrent.CopyOnWriteArrayList

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val isInitializedLiveData = MutableLiveData<Boolean>()
    val isNowReadyToCorrect = MutableLiveData<Boolean>()

    // Stockez l'état de vos éléments de vue ici, par exemple:
    val transcription = MutableLiveData<String>()
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
    val newSegment = MutableLiveData<String>()
    val pendingSegments = LinkedList<String>()
    val isRequestSpellingSuggestions = MutableLiveData<Boolean>()
    val isEditableTranscriptionText = MutableLiveData<Boolean>()
    val isEditingTranscriptionText = MutableLiveData<Boolean>()
    val misspelledWords = CopyOnWriteArrayList<MisspelledWordInfo>()
    val isSpellAlreadyRequested = MutableLiveData<Boolean>()

    //val isRunning = MutableLiveData<Boolean>()
    //private val contentResolver = application.contentResolver

    fun updateLiveData(key: String, value: Boolean) {
        when (key) {
            "isRunning" -> isRunningLiveData.postValue(value)
            "isFullyStopped" -> isFullyStoppedLiveData.postValue(value)
            "isFullySuccessful" -> isFullySuccessfulLiveData.postValue(value)
            "isGoingToStop" -> isGoingToStopLiveData.postValue(value)
            "isInitialized" -> isInitializedLiveData.postValue(value)
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
        val isInitialized = preferences.getBoolean("isInitialized", false)
        Log.d("SharePreferences", "isRunning: $isRunning")
        Log.d("SharePreferences", "isGoingToStop: $isGoingToStop")
        Log.d("SharePreferences", "isFullyStopped: $isFullyStopped")
        Log.d("SharePreferences", "isFullySuccessful: $isFullySuccessful")
        Log.d("SharePreferences", "isInitialized: $isInitialized")
        if (isRunning) {
            isRunningLiveData.postValue(true)
        } else if (isGoingToStop) {
            isGoingToStopLiveData.postValue(true)
        } else if (isFullyStopped) {
            isFullyStoppedLiveData.postValue(true)
        } else if (isFullySuccessful) {
            isFullySuccessfulLiveData.postValue(true)
        } else if (isInitialized) {
            isInitializedLiveData.postValue(true)
        }

        loadTranscription()
    }

    fun saveTemporaryTranscription(transcriptionText: String?) {
        TranscriptionContentProvider().updateTemporaryText(transcriptionText)
    }

    fun loadTranscription() {
        viewModelScope.launch {
            merge(
                isFullySuccessfulLiveData.asFlow(),
                isRunningLiveData.asFlow(),
                isGoingToStopLiveData.asFlow(),
                isFullyStoppedLiveData.asFlow(),
                isInitializedLiveData.asFlow()
            ).collect {
                when {
                    /*isFullySuccessfulLiveData.value == true -> {
                        Log.d("HomeViewModel", "isFullySuccessfulLiveData.value: true")
                        // Observer cachedTranscription
                        TranscriptionContentProvider.cachedTranscription.collect { cachedValue ->
                            Log.d("HomeViewModel", "cachedValue: ${cachedValue?.content}")
                            // Faire quelque chose avec cachedValue, par exemple:
                            transcription.postValue(cachedValue?.content)
                        }
                    }*/

                    isFullyStoppedLiveData.value == true || isInitializedLiveData.value == true -> {
                        // Observer temporaryText
                        transcription.postValue("")
                    }

                    isRunningLiveData.value == true || isGoingToStopLiveData.value == true || isFullySuccessfulLiveData.value == true -> {
                        // Observer temporaryText
                        TranscriptionContentProvider.temporaryText.collect { tempText ->
                            Log.d("HomeViewModel", "tempText: $tempText")
                            // Faire quelque chose avec tempText
                            if (tempText != null) {
                                transcription.postValue(tempText)
                            }
                        }
                    }
                }
            }
        }
    }
}