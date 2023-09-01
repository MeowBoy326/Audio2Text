package com.example.audio2text

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch

class TranscriptionContentProvider : ContentProvider() {
    private val TRANSCRIPTION_URI_CODE = 1
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    private lateinit var appDatabase: AppDatabase
    val lastTranscription: StateFlow<Transcription?> get() = _lastTranscription
    private val _cachedTranscription = MutableStateFlow<Transcription?>(null)
    val cachedTranscription: StateFlow<Transcription?> get() = _cachedTranscription

    companion object {
        const val AUTHORITY = "com.example.audio2text"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/transcriptions")

        var lastTranscriptionFlow: Flow<Transcription?>? = null
        val _lastTranscription = MutableStateFlow<Transcription?>(null)
    }

    init {
        uriMatcher.addURI(AUTHORITY, "transcriptions", TRANSCRIPTION_URI_CODE)
        CoroutineScope(Dispatchers.IO).launch {
            lastTranscriptionFlow?.collect { newTranscription ->
                _lastTranscription.emit(newTranscription)
            }
        }
    }

    override fun onCreate(): Boolean {
        context?.let {
            appDatabase = AppDatabase.getDatabase(it)
            lastTranscriptionFlow = appDatabase.transcriptionDao().getLastTranscription()
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf("transcription"))
        val transcription = _lastTranscription.value
        cursor.addRow(arrayOf(transcription?.content ?: ""))
        return cursor
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        values?.getAsString("transcription")?.let { newContent ->
            CoroutineScope(Dispatchers.IO).launch {
                appDatabase.transcriptionDao().updateLastTranscription(newContent)
                val updatedTranscription = Transcription(content = newContent)
                _cachedTranscription.emit(updatedTranscription)  // Mettre à jour le cache ici
            }
        }
        context?.contentResolver?.notifyChange(uri, null)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        CoroutineScope(Dispatchers.IO).launch {
            _cachedTranscription.emit(null)  // Mettre à jour le cache ici
        }
        return 1
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        values?.getAsString("transcription")?.let { transcriptionText ->
            CoroutineScope(Dispatchers.IO).launch {
                appDatabase.transcriptionDao().deleteAll()  // Supprime toutes les entrées existantes
                val transcription = Transcription(content = transcriptionText)
                appDatabase.transcriptionDao().insertTranscription(transcription)
                _cachedTranscription.emit(transcription)  // Mettre à jour le cache ici
            }
        }
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }
}
