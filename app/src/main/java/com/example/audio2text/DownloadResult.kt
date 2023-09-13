package com.example.audio2text

import okhttp3.Response

sealed class DownloadResult {
    data class Success(val triple: Triple<String, Response, String>): DownloadResult()
    data class Failure(val triple: Triple<String, Throwable?, String>): DownloadResult()
}