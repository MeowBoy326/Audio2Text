package com.example.audio2text

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Dao
interface TranscriptionDao {

    @Insert
    suspend fun insertTranscription(transcription: Transcription)

    @Query("SELECT * FROM transcriptions ORDER BY id DESC LIMIT 1")
    fun getLastTranscription(): Flow<Transcription?>

    @Query("UPDATE transcriptions SET content = :newContent WHERE id = (SELECT MAX(id) FROM transcriptions)")
    suspend fun updateLastTranscription(newContent: String)

    @Query("DELETE FROM transcriptions")
    suspend fun deleteAll()
}