package com.example.audio2text

import android.app.Application
import android.util.Log
import android.view.animation.Transformation
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.audio2text.DictionaryManager.listDictionaries

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {
    private val dictionaryDao = AppDatabase.getDatabase(application).dictionaryDao()
    private val dictionaryRepository = DictionaryRepository(dictionaryDao)
    val dictionaryItems: MutableLiveData<List<DictionaryItem>> = MutableLiveData(listDictionaries)
    var lastUpdatedId: String? = null

    suspend fun deleteDictionary(id: String) {
        dictionaryDao.deleteById(id)
    }

    // Mettre à jour l'état d'un dictionnaire en particulier
    fun updateDictionaryState(id: String, action: (DictionaryItem) -> Unit) {
        val currentStates = dictionaryItems.value ?: emptyList()
        val updatedStates = currentStates.map { item ->
            if (item.id == id) {
                val newItem = item.copy().apply { action(this) }
                if (newItem.isDownloadComplete) {
                    Log.d("Whisper", "isDownloadComplete: ${newItem.isDownloadComplete} for id: ${newItem.id}")
                    dictionaryDao.insertOrUpdate(dictionaryRepository.mapItemToEntity(newItem))
                    lastUpdatedId = newItem.id
                }
                newItem
            } else {
                item
            }
        }

        // Mettre à jour le LiveData
        dictionaryItems.postValue(updatedStates)
    }
}