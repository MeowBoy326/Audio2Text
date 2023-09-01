package com.example.audio2text

data class DictionaryItem(
    var id: String,
    var name: String,
    var fileRelativePath: String? = null,
    var isDownloadComplete: Boolean = false,
    var isDownloading: Boolean = false,
    var isFailed: Boolean = false,
    var progress: Int = 0,
    var isSelected: Boolean = false
)