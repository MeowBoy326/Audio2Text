package com.example.audio2text

import androidx.lifecycle.LiveData

import androidx.lifecycle.MutableLiveData

import androidx.lifecycle.ViewModel


class DownloadViewModel : ViewModel() {
    val downloadProgress = MutableLiveData<Int>(0)
    val downloadStatus = MutableLiveData<String>("")
    val isDownloading = MutableLiveData<Boolean>(false)
    val isDownloadComplete = MutableLiveData<Boolean>(false)
    val isFailed = MutableLiveData<Boolean>(false)
}