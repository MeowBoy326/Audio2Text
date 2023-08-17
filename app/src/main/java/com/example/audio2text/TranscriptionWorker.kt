package com.example.audio2text

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.CancellationSignal
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.Executors
import android.content.Context.RECEIVER_NOT_EXPORTED
import androidx.core.content.ContextCompat

class TranscriptionWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    private var currentSegmentIndex: Int = 0
    private var partialProgress: Int = 0
    private val NOTIFICATION_ID_FINISHED = 2
    private var isServiceBeingDestroyed = false
    private var segments_size: Int = -1
    private var global_progress: Int = 0
    private val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
    private val cancellationSignal = CancellationSignal()
    val fullTranscription = StringBuilder()

    companion object {
        private val job = Job()
        private val scope = CoroutineScope(Dispatchers.Default + job)
        private val stopTranscriptionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    if (intent.action == "STOP_TRANSCRIPTION") {
                        scope.launch {
                            releaseResources(context!!, true)
                        }
                    }
                }
            }
        }
        var whisperContext: WhisperContext? = null
        private lateinit var notificationManager: NotificationManager

        private val NOTIFICATION_ID = 1

        private val CHANNEL_ID = "transcription_channel"
        suspend fun releaseResources(context: Context, stopped: Boolean) {
            // Libérer les ressources ici
            if (stopped) {
                whisperContext?.stopProcess()
            }
            context.unregisterReceiver(stopTranscriptionReceiver)
            // Libérer les ressources ici
            Log.d("Whisper", "Inside releaseRessources fonction")
            notificationManager.cancel(NOTIFICATION_ID)
            whisperContext!!.release()
        }
    }

    val workId = id

    val stopTranscriptionIntent = Intent(applicationContext, StopTranscriptionReceiver::class.java).apply {
        action = "STOP_TRANSCRIPTION"
        putExtra("WORK_ID", workId.toString())
    }
    val stopTranscriptionPendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        0,
        stopTranscriptionIntent,
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    @OptIn(DelicateCoroutinesApi::class)
    val progressListener = object : TranscriptionProgressListener {
        override fun onTranscriptionProgress(progress: Int) {
            /*if (isStopped) {
                Log.d("Whisper", "Call to stop")
                // Le travail a été annulé, libérer les ressources
                /*GlobalScope.launch {
                    releaseResources()
                }*/
                //Log.d("Whisper", "Call to releaseRessources")
                //notificationManager.cancel(NOTIFICATION_ID)
                return
            }*/
            val startTime = System.currentTimeMillis()

            Log.d("Progress update", progress.toString())
            val global_progress = ((currentSegmentIndex + progress / 100.0) / segments_size) * 100
            Log.d("Progress update", global_progress.toString())

            // Mettre à jour la progression dans la notification
            val notificationProgress = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Transcription en cours")
                .setSmallIcon(R.drawable.notification_icon)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(100, global_progress.toInt(), false)
                .addAction(
                    R.drawable.stop_icon, // Icône pour le bouton
                    "Stop",
                    stopTranscriptionPendingIntent // Intent à déclencher lors du clic sur le bouton
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notificationProgress)
            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Temps d'exécution pour le callback: ${endTime - startTime} ms")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val segmentListener = object : TranscriptionSegmentListener {
        override fun onNewSegment(segment: String) {
            /*if (isStopped) {
                Log.d("Whisper", "Call to stop in segment listener")
                whisperContext?.stopProcess()
                Log.d("Whisper", "Call to stop")
                return
            }*/
            /*if (isStopped) {
                Log.d("Whisper", "Call to stop")
                // Le travail a été annulé, libérer les ressources
                GlobalScope.launch {
                    releaseResources()
                }
                Log.d("Whisper", "Call to releaseRessources")
                notificationManager.cancel(NOTIFICATION_ID)
                return
            }*/
            fullTranscription.append(segment.trim() + "\n")
            val values = ContentValues().apply {
                put("transcription", fullTranscription.toString())
            }
            applicationContext.contentResolver.insert(TranscriptionContentProvider.CONTENT_URI, values)
            /*val updateIntent = Intent("com.example.audio2text.TRANSCRIPTION_UPDATE")
            updateIntent.putExtra("transcription", fullTranscription.toString())
            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(updateIntent)*/
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun loadModel() = withContext(Dispatchers.IO) {
        var showText = "Chargement du modèle..."

        // Mettre à jour le ContentProvider avec le nouveau texte
        val values = ContentValues().apply {
            put("transcription", showText)
        }
        applicationContext.contentResolver.insert(TranscriptionContentProvider.CONTENT_URI, values)

        // Récupérer le chemin du fichier à partir des préférences partagées
        val preferences = applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val filePath = preferences.getString(MainActivity.modelPath, null)

        if (filePath != null && File(filePath).exists()) {
            try {
                whisperContext = WhisperContext.createContextFromFile(filePath)
                withContext(Dispatchers.Main) {
                    showText = "Le modèle a été chargé avec succès ! La transcription est en cours et va s'afficher bientôt. Veuillez patienter..."

                    // Mettre à jour le ContentProvider avec le nouveau texte
                    val valuesShown = ContentValues().apply {
                        put("transcription", showText)
                    }
                    applicationContext.contentResolver.insert(TranscriptionContentProvider.CONTENT_URI, valuesShown)

                    Toast.makeText(
                        applicationContext,
                        "Le modèle a été chargé avec succès !",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                // Gérer l'erreur de chargement du modèle ici
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "Erreur lors du chargement du modèle : ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            // Gérer l'erreur si le chemin du fichier est null ou si le fichier n'existe pas
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "Le fichier de modèle est introuvable. Veuillez réessayer.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun convertAudioToWav(
        inputFilePath: String,
        outputFilePath: String,
        cancellationSignal: CancellationSignal
    ): Boolean {
        val outputFile = File(outputFilePath)

        // Delete the output file if it exists
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val command = arrayOf(
            "-y",              // Écrase le fichier de sortie s'il existe
            "-i", "\"$inputFilePath\"", // Fichier d'entrée (peut être vidéo ou audio)
            "-af", "loudnorm=I=-16:LRA=11:TP=-1.5", // Applique la normalisation du volume
            "-vn",             // Supprime la piste vidéo (si le fichier d'entrée est une vidéo)
            "-ar", "16000",    // Définit la fréquence d'échantillonnage à 16 kHz
            "-ac", "1",        // Définit le nombre de canaux audio à 1 (mono)
            "\"$outputFilePath\"" // Fichier de sortie
        ).joinToString(" ")

        Log.d(ContentValues.TAG, "Starting audio conversion with command: $command")

        val result = executeFFmpegCommandAsync(
            command,
            outputFilePath,
            "The Message.wav",
            cancellationSignal,
            applicationContext
        )

        Log.d(ContentValues.TAG, "Audio conversion finished, result: $result")

        return result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun executeFFmpegCommandAsync(
        command: String,
        inputFilePath: String,
        outputFileName: String,
        cancellationSignal: CancellationSignal,
        context: Context
    ): Boolean =
        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeAsync(command) { session: FFmpegSession ->
                val returnCode = session.returnCode.value

                if (returnCode == ReturnCode.SUCCESS) {
                    // Copy the file to external storage
                    //val uri = saveFileToExternalStorage(context, inputFilePath, outputFileName)
                    //Log.d(TAG, "File saved to external storage at $uri")
                    continuation.resume(true) { }
                } else {
                    continuation.resume(false) { }
                }
            }

            cancellationSignal.setOnCancelListener {
                session.cancel()
                continuation.resume(false) { }
            }

            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }
        }


    fun decodeWaveFile(file: File): FloatArray {
        val baos = ByteArrayOutputStream()
        file.inputStream().use { it.copyTo(baos) }
        val buffer = ByteBuffer.wrap(baos.toByteArray())
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val channel = buffer.getShort(22).toInt()
        buffer.position(44)
        val shortBuffer = buffer.asShortBuffer()
        val shortArray = ShortArray(shortBuffer.limit())
        shortBuffer.get(shortArray)
        return FloatArray(shortArray.size / channel) { index ->
            when (channel) {
                1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
                else -> ((shortArray[2 * index] + shortArray[2 * index + 1]) / 32767.0f / 2.0f).coerceIn(
                    -1f..1f
                )
            }
        }
    }

    fun splitAudioIntoSegments(file: File): List<FloatArray> {
        val sampleRate = 16000
        val segmentDurationInSeconds = 30
        val samplesPerSegment = sampleRate * segmentDurationInSeconds
        val segments = mutableListOf<FloatArray>()

        FileInputStream(file).use { fis ->
            val header = ByteArray(44)
            fis.read(header) // Read WAV file header
            val buffer = ByteBuffer.wrap(header)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val channel = buffer.getShort(22).toInt()
            val bytesPerSample = buffer.getShort(34).toInt() / 8
            val chunkSize = 4096 // Chunk size to read at each iteration

            var segment = FloatArray(samplesPerSegment) { 0f }
            var segmentIndex = 0

            val chunk = ByteArray(chunkSize)
            var bytesRead: Int

            while (fis.read(chunk).also { bytesRead = it } != -1) {
                for (i in 0 until bytesRead step bytesPerSample) {
                    val value = ByteBuffer.wrap(chunk, i, bytesPerSample)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .get()
                        .let { it / 32767.0f }
                        .coerceIn(-1f..1f)
                    segment[segmentIndex] = value
                    segmentIndex++

                    if (segmentIndex == samplesPerSegment) {
                        segments.add(segment)
                        segment = FloatArray(samplesPerSegment) { 0f }
                        segmentIndex = 0
                    }
                }
            }

            // Ajouter le dernier segment, même s'il est plus court
            if (segmentIndex > 0) {
                // Si le dernier segment est plus court que la taille requise, le couper à la taille réelle
                segments.add(segment.copyOfRange(0, segmentIndex))
            }
        }

        return segments
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        // Créez votre notification ici
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(message)
            .setSmallIcon(R.drawable.notification_icon)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        // Enregistrer le BroadcastReceiver
        val filter = IntentFilter().apply {
            addAction("STOP_TRANSCRIPTION")
        }
        ContextCompat.registerReceiver(applicationContext, stopTranscriptionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val audioFilePath = inputData.getString("audioFilePath") ?: return Result.failure()
        val fileNameWithoutExtension =
            File(audioFilePath).name.replaceFirst("[.][^.]+$".toRegex(), "")
        val outputFileName = "$fileNameWithoutExtension.wav"
        val outputFile = File(applicationContext.filesDir, outputFileName)
        val outputFilePath = outputFile.absolutePath

        setForeground(createForegroundInfo("Transcription en cours"))

        return try {
            // Conversion de l'audio en WAV
            if (convertAudioToWav(audioFilePath, outputFilePath, cancellationSignal)) {
                loadModel()
                whisperContext?.setProgressCallback(progressListener)
                whisperContext?.setSegmentCallback(segmentListener)
                val segments = splitAudioIntoSegments(outputFile)
                //fullTranscription.append("Résultat de la transcription :\n\n")
                //val fullText = whisperContext?.transcribeData(audioData)
                //Log.d("Final of final", fullText!!)
                segments_size = segments.size
                fullTranscription.append("Résultat de la transcription :\n\n")
                for (segment in segments) {
                    /*if (isStopped) {
                        whisperContext?.stopProcess()
                        Log.d("Whisper", "Call to stop")
                        //outputFile.delete()
                        return Result.failure()
                    }*/
                    val textSegment = whisperContext?.transcribeData(segment)
                    Log.d("Whisper", textSegment.toString())
                    //fullTranscription.append(textSegment)
                    /*val values = ContentValues().apply {
                        put("transcription", fullTranscription.toString())
                    }
                    applicationContext.contentResolver.insert(TranscriptionContentProvider.CONTENT_URI, values)*/
                    /*val updateIntent = Intent("com.example.audio2text.TRANSCRIPTION_UPDATE")
                    updateIntent.putExtra("transcription", fullTranscription.toString())
                    LocalBroadcastManager.getInstance(applicationContext)
                        .sendBroadcast(updateIntent)*/
                    currentSegmentIndex++
                }
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la transcription", e)
            Result.failure()
        } finally {
            Log.d("Whisper","Call to finally")
            outputFile.delete()
            releaseResources(applicationContext, false)
        }
    }
}
