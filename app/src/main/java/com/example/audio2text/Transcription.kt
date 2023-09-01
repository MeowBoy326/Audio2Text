package com.example.audio2text

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcriptions")
data class Transcription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String
)