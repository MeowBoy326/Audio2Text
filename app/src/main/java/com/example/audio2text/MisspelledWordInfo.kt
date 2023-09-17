package com.example.audio2text

data class MisspelledWordInfo(var start: Int, var end: Int, val suggestions: List<String>)