package com.example.audio2text

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class TranscriptionContentProvider : ContentProvider() {
    private var transcription: String? = null
    private val TRANSCRIPTION_URI_CODE = 1
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    init {
        uriMatcher.addURI(AUTHORITY, "transcriptions", TRANSCRIPTION_URI_CODE)
    }

    companion object {
        const val AUTHORITY = "com.example.audio2text"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/transcriptions")
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val cursor = MatrixCursor(arrayOf("transcription"))
        cursor.addRow(arrayOf(transcription))
        return cursor
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        transcription = values?.getAsString("transcription")
        context?.contentResolver?.notifyChange(uri, null)
        return 1
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        transcription = null
        return 1
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Mettre à jour la transcription avec la nouvelle valeur
        values?.getAsString("transcription")?.let {
            transcription = it
        }

        // Notifier les observateurs que les données ont changé
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }
}
