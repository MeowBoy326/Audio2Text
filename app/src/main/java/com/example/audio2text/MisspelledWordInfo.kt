package com.example.audio2text

data class MisspelledWordInfo(val start: Int, val end: Int, val suggestions: List<String>)