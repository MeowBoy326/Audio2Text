package com.example.audio2text

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionaries")
data class Dictionary(
    @PrimaryKey var id: String,
    var name: String,
    var isDownloaded: Boolean,
    var fileRelativePath: String? = null
)