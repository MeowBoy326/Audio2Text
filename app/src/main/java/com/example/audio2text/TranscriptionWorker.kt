package com.example.audio2text

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder


class TranscriptionWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    private var totalDurationInSeconds: Long = 0
    private var selectedLanguage: String = "auto"
    private var selectedLanguageToIgnore: String = ""
    private var segmentLength: Int = 30
    private var translate: Boolean = false
    private var speed: Boolean = false
    private var initialPrompt: String = ""
    private var currentSegmentIndex: Int = 0
    private var startTimeInSeconds: Int = 0
    private var endTimeInSeconds: Int = 0
    private var maxSize: Int = 1000
    private var partialProgress: Int = 0
    private val NOTIFICATION_ID_FINISHED = 2
    private var isServiceBeingDestroyed = false
    private var segments_size: Int = -1
    private var global_progress: Int = 0
    private val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
    private val cancellationSignal = CancellationSignal()
    val fullTranscription = StringBuilder()
    val workId = id

    val stopTranscriptionIntent =
        Intent(applicationContext, StopTranscriptionReceiver::class.java).apply {
            action = "STOP_TRANSCRIPTION"
            putExtra("WORK_ID", workId.toString())
        }

    val stopTranscriptionPendingIntent = PendingIntent.getBroadcast(
        applicationContext,
        0,
        stopTranscriptionIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = createNotification(
            applicationContext,
            id, // L'UUID de ce Worker
            "Transcription en cours", // Le titre de la notification
            showProgress = true,
            progress = 0, // La valeur initiale de la progression
            stopTranscriptionPendingIntent = stopTranscriptionPendingIntent
        )

        return ForegroundInfo(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        //private lateinit var wakeLock: PowerManager.WakeLock
        private var isReceiverRegistered = false
        private lateinit var job: Job
        private lateinit var scope: CoroutineScope
        private val stopTranscriptionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    if (intent.action == "STOP_TRANSCRIPTION") {
                        Log.d("Whisper", "Call to stop")
                        scope.launch {
                            releaseResources(context!!, true)
                        }
                    }
                }
            }
        }


        private fun saveTranscription(context: Context, transcriptionText: String) {
            val values = ContentValues()
            values.put("transcription", transcriptionText)
            val uri = TranscriptionContentProvider.CONTENT_URI
            context.contentResolver.insert(uri, values)
        }

        var whisperContext: WhisperContext? = null
        private lateinit var notificationManager: NotificationManager

        const val NOTIFICATION_ID = 42
        private var reachInference = false
        suspend fun releaseResources(context: Context, stopped: Boolean) {
            Log.d("Whisper", "Inside releaseRessources fonction : $reachInference")
            notificationManager.cancel(NOTIFICATION_ID)
            // Libérer les ressources ici
            if (stopped && reachInference) {
                val showText =
                    "Arrêt en cours. Veuillez patienter..."
                Log.d("Whisper", "Call to stop during inference")
                saveTranscription(context, showText)
            } else if (!reachInference) {
                val preferences =
                    context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
                preferences.edit().putBoolean("stoppedBeforeInference", true).apply()
            }
            whisperContext?.stopProcess()
            whisperContext?.release()
            // Lors de la désinscription
            if (isReceiverRegistered) {
                try {
                    context.unregisterReceiver(stopTranscriptionReceiver)
                } catch (e: IllegalArgumentException) {
                    // Le récepteur n'était pas enregistré, ignorez l'exception
                }
                isReceiverRegistered = false
            }
            job.cancel()

            //wakeLock.release()
            // Libérer les ressources ici
            Log.d("Whisper", "Inside releaseRessources fonction")
        }
    }

    private val inferenceStoppedListener = object : InferenceStoppedListener {
        override fun onInferenceStopped() {
            Log.d("Whisper", "Inference stopped in listener")
            val showText = ""

            saveTranscription(applicationContext, showText)
            // Réactivez le bouton ici
            /*CoroutineScope(Dispatchers.Main).launch {
                ButtonState.enableButton()
            }*/
            val preferences =
                applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
            preferences.edit().putBoolean("isGoingToStop", false).apply()
            preferences.edit().putBoolean("isFullyStopped", true).apply()
        }
    }

    //private val _progress = MutableStateFlow(0)
    //val progress: StateFlow<Int> = _progress

    @OptIn(DelicateCoroutinesApi::class)
    val progressListener = object : TranscriptionProgressListener {
        override suspend fun onTranscriptionProgress(progress: Int) {
            val startTime = System.currentTimeMillis()

            Log.d("Progress update", progress.toString())
            global_progress =
                (((currentSegmentIndex + progress / 100.0) / segments_size) * 100).toInt()
            Log.d("Progress update", global_progress.toString())

            Log.d("Whisper", "Call to setForeground")
            if (global_progress == 99) {
                global_progress = 100
            }

            /*val notificationProgress = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Transcription en cours")
                .setSmallIcon(R.drawable.notification_icon)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(100, global_progress, false)
                .addAction(
                    R.drawable.stop_icon, // Icône pour le bouton
                    "Stop",
                    stopTranscriptionPendingIntent // Intent à déclencher lors du clic sur le bouton
                )
                .build()*/

            notificationManager.notify(
                NOTIFICATION_ID,
                createNotification(
                    applicationContext,
                    workId,
                    "Transcription en cours",
                    showProgress = true,
                    progress = global_progress
                )
            )

            val endTime = System.currentTimeMillis()
            Log.d(TAG, "Temps d'exécution pour le callback: ${endTime - startTime} ms")
        }

        override fun onTranscriptionProgressNonSuspend(progress: Int) {
            scope.launch {
                onTranscriptionProgress(progress)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    val segmentListener = object : TranscriptionSegmentListener {
        override fun onNewSegment(segment: String) {
            fullTranscription.append(segment.trim() + " ")
            saveTranscription(applicationContext, fullTranscription.toString())
        }
    }

    private fun loadModel() {
        val showText = "Chargement du modèle..."

        saveTranscription(applicationContext, showText)

        // Récupérer le chemin du fichier à partir des préférences partagées
        val preferences =
            applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val filePath = preferences.getString("whisper_model", null)
        selectedLanguage = preferences.getString("languageCode", null)!!
        selectedLanguageToIgnore = preferences.getString("languageIgnored", null)!!
        translate = preferences.getBoolean("translate", false)
        speed = preferences.getBoolean("speed", false)
        segmentLength = preferences.getInt("segmentLength", 30)
        initialPrompt = preferences.getString("initialPrompt", "")!!
        startTimeInSeconds = preferences.getInt("startTime", 0)
        endTimeInSeconds = preferences.getInt("endTime", 0)
        maxSize = preferences.getInt("maxTextSize", 1000)

        Log.d("Whisper", "Path to model: $filePath")

        if (filePath != null && File(filePath).exists()) {
            try {
                Log.d("Whisper", "Loading model from $filePath")
                whisperContext = filePath.let {
                    WhisperContext.createContextFromFile(
                        it
                    )
                }

                val showText2 =
                    "Le modèle a été chargé avec succès ! La transcription est en cours et va s'afficher bientôt. Veuillez patienter..."

                saveTranscription(applicationContext, showText2)

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        "Le modèle a été chargé avec succès !",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Log.d("Whisper", "Error loading model: ${e.message}")
                // Gérer l'erreur de chargement du modèle ici
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        "Erreur lors du chargement du modèle : ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Log.d("Whisper", "Model file not found at $filePath")
            // Gérer l'erreur si le chemin du fichier est null ou si le fichier n'existe pas
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "Le fichier de modèle est introuvable. Veuillez réessayer.",
                    Toast.LENGTH_SHORT
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
            "-y",
            "-i", "\"$inputFilePath\"",
            "-af", "loudnorm=I=-16:LRA=11:TP=-1.5",
            "-vn",
            "-ar", "16000",
            "-ac", "1",
            "-c:a", "pcm_s16le",
            "\"$outputFilePath\""
        ).filter { it.isNotEmpty() }.joinToString(" ")

        Log.d(ContentValues.TAG, "Starting audio conversion with command: $command")

        val showText = "Conversion de la piste audio en WAV..."
        saveTranscription(applicationContext, showText)

        val result = executeFFmpegCommandAsync(
            command,
            outputFilePath,
            outputFile.name,
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
    ): Boolean = suspendCancellableCoroutine { continuation ->
        if (continuation.isActive) {
            val session = FFmpegKit.executeAsync(command) { session: FFmpegSession ->
                val returnCode = session.returnCode.value
                if (returnCode == ReturnCode.SUCCESS) {
                    continuation.resume(true) {}
                } else {
                    continuation.resume(false) {}
                }
            }

            cancellationSignal.setOnCancelListener {
                session.cancel()
            }

            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
            }
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

    fun splitAudioIntoSegments(
        file: File,
        originalSegmentDurationInSeconds: Int
    ): List<FloatArray> {
        val sampleRate = 16000
        var segmentDurationInSeconds = originalSegmentDurationInSeconds
        val segments = mutableListOf<FloatArray>()

        FileInputStream(file).use { fis ->
            val header = ByteArray(44)
            fis.read(header) // Lire l'en-tête du fichier WAV
            val buffer = ByteBuffer.wrap(header)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val channel = buffer.getShort(22).toInt()
            val bytesPerSample = buffer.getShort(34).toInt() / 8
            val fileSize = fis.channel.size()
            val audioSize = fileSize - 44
            val totalSamples = audioSize / (bytesPerSample * channel)
            totalDurationInSeconds = totalSamples / sampleRate

            if (segmentDurationInSeconds > totalDurationInSeconds) {
                segmentDurationInSeconds = totalDurationInSeconds.toInt()
            }

            val samplesPerSegment = sampleRate * segmentDurationInSeconds
            val chunkSize = 4096

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

            if (segmentIndex > 0) {
                segments.add(segment.copyOfRange(0, segmentIndex))
            }
        }

        return segments
    }

    override suspend fun doWork(): Result {
        //val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager?
        //wakeLock = pm!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
        //wakeLock.acquire()
        val preferences =
            applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("isFullyStopped", false).apply()
        preferences.edit().putBoolean("isFullySuccessful", false).apply()
        preferences.edit().putBoolean("isRunning", true).apply()

        job = Job()
        scope = CoroutineScope(Dispatchers.IO + job)

        notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Enregistrer le BroadcastReceiver
        val filter = IntentFilter().apply {
            addAction("STOP_TRANSCRIPTION")
        }
        if (!isReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationContext.registerReceiver(
                    stopTranscriptionReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                applicationContext.registerReceiver(stopTranscriptionReceiver, filter)
            }
            isReceiverRegistered = true
        }

        val audioFilePath = inputData.getString("audioFilePath") ?: return Result.failure()
        val fileNameWithoutExtension =
            File(audioFilePath).name.replaceFirst("[.][^.]+$".toRegex(), "")
        val outputFileName = "$fileNameWithoutExtension.wav"
        val outputFile = File(applicationContext.filesDir, outputFileName)
        val outputFilePath = outputFile.absolutePath

        //setForeground(createForegroundInfo("Transcription en cours"))

        return try {
            // Conversion de l'audio en WAV
            if (convertAudioToWav(audioFilePath, outputFilePath, cancellationSignal)) {
                loadModel()
                whisperContext?.setProgressCallback(progressListener)
                whisperContext?.setSegmentCallback(segmentListener)
                whisperContext?.setInferenceStoppedCallback(inferenceStoppedListener)
                //scope.launch {
                //    progress.collect { progressValue ->
                if (!isStopped) {
                    setForegroundAsync(getForegroundInfo())
                } else {
                    Result.failure()
                }
                //    }
                //}
                val segments = splitAudioIntoSegments(outputFile, segmentLength)
                segments_size = segments.size
                fullTranscription.append("Résultat de la transcription :\n\n")
                reachInference = true
                //var contextPrompt = initialPrompt

                var offset_ms = 0
                var duration_ms = 0

                if (startTimeInSeconds >= 0 && endTimeInSeconds <= totalDurationInSeconds && startTimeInSeconds < endTimeInSeconds) {
                    offset_ms = startTimeInSeconds * 1000  // Convertir en millisecondes
                    duration_ms =
                        (endTimeInSeconds - startTimeInSeconds) * 1000  // Convertir en millisecondes
                } else if (startTimeInSeconds >= 0 && startTimeInSeconds < totalDurationInSeconds && endTimeInSeconds > totalDurationInSeconds) {
                    offset_ms = startTimeInSeconds * 1000  // Convertir en millisecondes
                    duration_ms =
                        (totalDurationInSeconds.toInt() - startTimeInSeconds) * 1000  // Convertir en millisecondes
                } else if (startTimeInSeconds == endTimeInSeconds) {
                    // Si les deux sont égaux, on mettra offset et duration à 0
                    offset_ms = 0
                    duration_ms = 0
                }

                Log.d("Whisper", "offset_ms: $offset_ms")
                Log.d("Whisper", "duration_ms: $duration_ms")

                for (segment in segments) {
                    val textSegment = whisperContext?.transcribeData(
                        segment,
                        selectedLanguage,
                        selectedLanguageToIgnore,
                        translate,
                        speed,
                        initialPrompt,  // Utilisation de initialPrompt ici
                        maxSize,
                        offset_ms,
                        duration_ms
                    )

                    Log.d("Whisper", textSegment.toString())
                    currentSegmentIndex++

                    // Mettre à jour initialPrompt pour le prochain segment
                    /*if (textSegment != null) {
                        contextPrompt = textSegment.trim()
                    }*/
                    fullTranscription.append("\n\n")
                }
                reachInference = false
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la transcription", e)
            Result.failure()
        } finally {
            Log.d("Whisper", "Call to finally")
            outputFile.delete()
            releaseResources(applicationContext, false)
        }
    }
}