package com.example.audio2text

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

private const val LOG_TAG = "WhisperLib"

class WhisperContext private constructor(private var ptr: Long) {
    var inferenceStoppedListener: InferenceStoppedListener? = null
    // Meet Whisper C++ constraint: Don't access from more than one thread at a time.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    //private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"

    suspend fun transcribeData(data: FloatArray): String = withContext(scope.coroutineContext) {
        Log.d(LOG_TAG, "Transcription started for data segment") // Log when transcription starts
        require(ptr != 0L)
        val startTime = System.currentTimeMillis()
        WhisperLib.fullTranscribe(ptr, data)
        val endTime = System.currentTimeMillis()
        Log.d(TAG, "Temps d'exécution pour la méthode fullTranscribe : ${endTime - startTime} ms")
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        Log.d("textCount", textCount.toString())
        Log.d(LOG_TAG, "Transcription finished for data segment") // Log when transcription finishes
        return@withContext buildString {
            for (i in 0 until textCount) {
                Log.d("Whisper", "Running text segment $i")
                append(WhisperLib.getTextSegment(ptr, i))
            }
        }
    }

    fun stopProcess() {
        WhisperLib.setStopped(ptr)
    }

    suspend fun benchMemory(nthreads: Int): String = withContext(scope.coroutineContext) {
        return@withContext WhisperLib.benchMemcpy(nthreads)
    }

    suspend fun benchGgmlMulMat(nthreads: Int): String = withContext(scope.coroutineContext) {
        return@withContext WhisperLib.benchGgmlMulMat(nthreads)
    }

    fun setProgressCallback (callback: TranscriptionProgressListener) {
        Log.d("Whisper", "Call to set Progress Callback")
        WhisperLib.setTranscriptionProgressListener(callback)
    }

    fun setSegmentCallback (callback: TranscriptionSegmentListener) {
        Log.d("Whisper", "Call to set Segment Callback")
        WhisperLib.setTranscriptionSegmentListener(callback)
    }

    fun setTranscriptionSegmentListener(callback: TranscriptionSegmentListener) {
        Log.d("Whisper", "Call to set Transcription Callback")
        WhisperLib.setTranscriptionSegmentListener(callback)
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        Log.d("Whisper", "Call to release in WhisperLib with ${ptr}")
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        runBlocking {
            release()
        }
    }

    companion object {
        /*@JvmStatic
        fun sendProgressBroadcast(context: Context, progress: Int) {
            WhisperLib.sendProgressBroadcast(context, progress)
        }*/
        fun createContextFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context with path $filePath")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromInputStream(stream: InputStream): WhisperContext {
            val ptr = WhisperLib.initContextFromInputStream(stream)

            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from input stream")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)

            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from asset $assetPath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String {
            return WhisperLib.getSystemInfo()
        }
    }
}

private class WhisperLib {
    companion object {
        init {
            Log.d(LOG_TAG, "Primary ABI: ${Build.SUPPORTED_ABIS[0]}")
            var loadVfpv4 = false
            var loadV8fp16 = false
            if (isArmEabiV7a()) {
                // armeabi-v7a needs runtime detection support
                val cpuInfo = cpuInfo()
                cpuInfo?.let {
                    Log.d(LOG_TAG, "CPU info: $cpuInfo")
                    if (cpuInfo.contains("vfpv4")) {
                        Log.d(LOG_TAG, "CPU supports vfpv4")
                        loadVfpv4 = true
                    }
                }
            } else if (isArmEabiV8a()) {
                // ARMv8.2a needs runtime detection support
                val cpuInfo = cpuInfo()
                cpuInfo?.let {
                    Log.d(LOG_TAG, "CPU info: $cpuInfo")
                    if (cpuInfo.contains("fphp")) {
                        Log.d(LOG_TAG, "CPU supports fp16 arithmetic")
                        loadV8fp16 = true
                    }
                }
            }

            if (loadVfpv4) {
                Log.d(LOG_TAG, "Loading libwhisper_vfpv4.so")
                System.loadLibrary("whisper_vfpv4")
            } else if (loadV8fp16) {
                Log.d(LOG_TAG, "Loading libwhisper_v8fp16_va.so")
                System.loadLibrary("whisper_v8fp16_va")
            } else {
                Log.d(LOG_TAG, "Loading libwhisper.so")
                System.loadLibrary("whisper")
            }
        }

        //external fun sendProgressBroadcast(context: Context, progress: Int)
        // JNI methods
        external fun setStopped(contextPtr: Long)
        external fun setTranscriptionSegmentListener(callback: TranscriptionSegmentListener)
        external fun setTranscriptionProgressListener(callback: TranscriptionProgressListener)
        external fun initContextFromInputStream(inputStream: InputStream): Long
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, audioData: FloatArray)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getSystemInfo(): String
        external fun benchMemcpy(nthread: Int): String
        external fun benchGgmlMulMat(nthread: Int): String
    }
}

private fun isArmEabiV7a(): Boolean {
    return Build.SUPPORTED_ABIS[0].equals("armeabi-v7a")
}

private fun isArmEabiV8a(): Boolean {
    return Build.SUPPORTED_ABIS[0].equals("arm64-v8a")
}

private fun cpuInfo(): String? {
    return try {
        File("/proc/cpuinfo").inputStream().bufferedReader().use {
            it.readText()
        }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e)
        null
    }
}