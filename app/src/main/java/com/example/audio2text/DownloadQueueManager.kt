package com.example.audio2text

import java.util.LinkedList
import java.util.Queue

object DownloadQueueManager {
    val downloadQueue: Queue<String> = LinkedList()
    val MAX_SIMULTANEOUS_DOWNLOADS = 3
}