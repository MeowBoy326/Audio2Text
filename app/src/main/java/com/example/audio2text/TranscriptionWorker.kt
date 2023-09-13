package com.example.audio2text

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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
import androidx.lifecycle.lifecycleScope
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.example.audio2text.MyApplication.Companion.CHANNEL_ID
import io.gitlab.rxp90.jsymspell.TokenWithContext
import io.gitlab.rxp90.jsymspell.api.SuggestItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.random.Random


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
    private var count: Int = 0
    private var partialProgress: Int = 0
    private val NOTIFICATION_ID_FINISHED = 2
    private var isServiceBeingDestroyed = false
    private var segments_size: Int = -1
    private var global_progress: Int = 0
    private val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
    private val cancellationSignal = CancellationSignal()
    val fullTranscription = StringBuilder()
    val workId = id
    private var remainingText = ""
    private var paragraphe: String = ""
    private var start: Int = 0
    private var end: Int = 0

    val notification = createNotification(
        applicationContext,
        id, // L'UUID de ce Worker
        "Transcription en cours", // Le titre de la notification
        showProgress = true,
        progress = 0
    )

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private var isReceiverRegistered = false
        private lateinit var job: Job
        private lateinit var scope: CoroutineScope

        var whisperContext: WhisperContext? = null
        private lateinit var notificationManager: NotificationManager

        const val NOTIFICATION_ID = 42
        private var reachInference = false
        suspend fun releaseResources(during: Boolean = false) {
            Log.d("Whisper", "Inside releaseRessources fonction")
            //notificationManager.cancel(NOTIFICATION_ID)
            // Libérer les ressources ici
            if (during) {
                whisperContext?.stopProcess()
            } else {
                whisperContext?.release()
                if (::job.isInitialized) {
                    job.cancel()
                }
            }
        }
    }

    private val inferenceStoppedListener = object : InferenceStoppedListener {
        override fun onInferenceStopped() {
            Log.d("Whisper", "Inference stopped in listener")
            val showText = ""

            HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "La transcription a été arrêtée",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val preferences =
                applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
            preferences.edit().putBoolean("isGoingToStop", false).apply()
            preferences.edit().putBoolean("isFullyStopped", true).apply()

            CoroutineScope(Dispatchers.IO).launch {
                releaseResources()
            }
        }
    }

    //private val _progress = MutableStateFlow(0)
    //val progress: StateFlow<Int> = _progress

    @OptIn(DelicateCoroutinesApi::class)
    val progressListener = object : TranscriptionProgressListener {
        override suspend fun onTranscriptionProgress(progress: Int) {
            if (!isStopped) {
                val startTime = System.currentTimeMillis()

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
            if (segment.isNotEmpty() && !isStopped) {
                // Concaténer le texte restant du segment précédent
                val fullSegment = remainingText.trimStart() + segment.trim()

                // Réinitialiser remainingText
                remainingText = ""

                // Ajouter le segment et un espace à la fin
                fullTranscription.append(fullSegment).append(" ")

                // Mettre à jour count
                count += fullSegment.length + 1  // +1 pour l'espace

                // Vérifier si count dépasse maxSize
                if (count > maxSize) {
                    // Prendre la portion de texte qui dépasse maxSize
                    val overflowText = fullTranscription.takeLast(count - maxSize)
                    Log.d("Whisper", "overflowText: $overflowText")
                    val regex = "[.!?]".toRegex()

                    // Trouver la dernière occurrence de ponctuation dans overflowText
                    val lastPunctuationIndex =
                        regex.findAll(overflowText).lastOrNull()?.range?.first
                    Log.d("Whisper", "lastPunctuationIndex: $lastPunctuationIndex")

                    if (lastPunctuationIndex != null) {
                        // Couper à la dernière ponctuation et ajouter un saut de ligne
                        val cutIndex = count - maxSize - lastPunctuationIndex
                        remainingText = fullTranscription.takeLast(cutIndex - 1)
                            .toString()  // +1 pour ignorer la ponctuation
                        fullTranscription.setLength(fullTranscription.length - remainingText.length)  // Couper après la dernière ponctuation
                        end = fullTranscription.length
                        fullTranscription.append("\n\n")
                        Log.d("Whisper", "Substring: start is $start, end is $end")
                        Log.d("Whisper", "fullTranscription: ${fullTranscription.length}")
                        paragraphe = fullTranscription.substring(start, end)

                        if (SpellCheckerSingleton.isSpellCheckerReady.value == true) {
                            Log.d("Whisper", "Déjà prêt lors de l'inférence")
                            // Traitement habituel
                            CoroutineScope(Dispatchers.Default).launch {
                                gatherContextualSpellingSuggestions(paragraphe)
                            }
                        } else {
                            Log.d("Whisper", "On doit attendre que SpellChecker soit prêt")
                            // Ajoutez à la file d'attente en attendant que le SpellChecker soit prêt
                            HomeViewModelHolder.viewModel.pendingSegments.add(paragraphe)
                        }

                        // Réinitialiser count
                        count = remainingText.length + 1  // +1 pour un éventuel espace
                        start = fullTranscription.length
                    }
                }

                // Sauvegarder la transcription temporaire
                HomeViewModelHolder.viewModel.saveTemporaryTranscription(fullTranscription.toString())
            }
        }
    }

    val spellCheckerReadyListener = object : SpellCheckerReadyListener {
        override fun onSpellCheckerReady(segment: String) {
            CoroutineScope(Dispatchers.Default).launch {
                gatherContextualSpellingSuggestions(segment)
            }
        }
    }

    private fun loadModel() {
        val showText = "Chargement du modèle..."

        HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText)

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

                /*val showText2 =
                    "Le modèle a été chargé avec succès ! La transcription est en cours et va s'afficher bientôt. Veuillez patienter..."

                HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText2)*/

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
        cancellationSignal: CancellationSignal,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
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

        withContext(Dispatchers.Main) {
            val showText = "Conversion de la piste audio en WAV..."
            HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText)
        }

        val deferred = CompletableDeferred<Boolean>()

        FFmpegKit.executeAsync(command) { session: FFmpegSession ->
            deferred.complete(session.returnCode.value == ReturnCode.SUCCESS)
        }

        cancellationSignal.setOnCancelListener {
            deferred.cancel()
        }

        return@withContext deferred.await()
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

    suspend fun initSpellChecker(context: Context) = withContext(Dispatchers.Default) {
        if (SpellCheckerSingleton.spellChecker == null) {
            val preferences =
                context.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
            val selectedDictionaryId = preferences.getString("selectedDictionaryId", "")
            if (selectedDictionaryId?.isNotEmpty() == true) {
                withContext(Dispatchers.Main) {
                    val showText =
                        "Initialisation du correcteur orthographique pour la première fois..."
                    HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText)
                }

                val dictionary = DictionaryManager.getDictionaryItemById(selectedDictionaryId)
                val wordPath = dictionary?.fileRelativePath
                val bigramPath = "big_${dictionary?.id}.txt"

                // Chargement du dictionnaire de fréquence
                val futureSpellChecker = SpellChecker
                    .loadFrequencyDictionary(context, wordPath!!)
                    .loadBigramDictionary(context, bigramPath)
                    .withMaxEditDistance(2)
                    .buildAsync()
                    .await()  // Utilisation de await pour attendre la fin de la tâche

                if (futureSpellChecker != null) {
                    SpellCheckerSingleton.spellChecker = futureSpellChecker
                    SpellCheckerSingleton.isSpellCheckerReady.postValue(true)
                    SpellCheckerSingleton.spellCheckerReadyListener = spellCheckerReadyListener
                } else {
                    Log.e(
                        "SpellChecker",
                        "Erreur lors du chargement du correcteur orthographique"
                    )
                }
            }
        }
    }

    private fun showCompletionNotification() {
        val notificationIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, "transcription_channel")
            .setContentTitle("Transcription terminée")
            .setSmallIcon(R.drawable.notification_icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Ceci permet à l'utilisateur de supprimer la notification
            .build()

        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(43, notification)
    }

    private fun saveTranscription(transcriptionText: String) {
        val values = ContentValues().apply {
            put("transcription", transcriptionText)
        }
        val uri = TranscriptionContentProvider.CONTENT_URI

        // Utiliser ContentResolver pour insérer les données
        applicationContext.contentResolver?.insert(uri, values)
    }

    fun adaptCasingForTokenWithContext(tokenWithContext: TokenWithContext): TokenWithContext {
        val originalToken = tokenWithContext.originalToken
        val adaptedSuggestions = tokenWithContext.suggestions.map { suggestItem ->
            val adaptedSuggestion = adaptCasing(originalToken, suggestItem.suggestion)
            SuggestItem(
                adaptedSuggestion,
                suggestItem.editDistance,
                suggestItem.frequencyOfSuggestionInDict
            )
        }

        val bestSuggestionInContext = tokenWithContext.bestSuggestionInContext
        val adaptedBestSuggestionInContext = if (bestSuggestionInContext != null) {
            SuggestItem(
                adaptCasing(originalToken, bestSuggestionInContext.suggestion),
                bestSuggestionInContext.editDistance,
                bestSuggestionInContext.frequencyOfSuggestionInDict
            )
        } else {
            null
        }

        return TokenWithContext(originalToken, adaptedSuggestions, adaptedBestSuggestionInContext)
    }

    fun adaptCasing(original: String, suggestion: String): String {
        return if (original.all { it.isUpperCase() }) {
            suggestion.uppercase(Locale.ROOT)
        } else if (original.first().isUpperCase()) {
            suggestion.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } else {
            suggestion.lowercase(Locale.ROOT)
        }
    }

    suspend fun gatherContextualSpellingSuggestions(text: String) =
        CoroutineScope(Dispatchers.Default).launch {
            // Ignorer le texte avant l'offset
            val textWithoutFirstLine = text.substringAfter("\n\n")
            Log.d("Whisper", "textWithoutFirstLine: $textWithoutFirstLine")

            // Diviser le texte en parties de phrase en utilisant les signes de ponctuation comme délimiteurs
            val sentenceParts = textWithoutFirstLine.split(Regex("[,.!;?:\"\\\\]"))

            for (sentencePart in sentenceParts) {
                val lowercasedSentencePart = sentencePart.lowercase()
                val tokenWithContextList =
                    SpellCheckerSingleton.spellChecker?.suggest(lowercasedSentencePart)?.await()

                if (tokenWithContextList != null && tokenWithContextList.isNotEmpty()) {

                    // Ici, nous adaptons les suggestions à la casse originale
                    val adaptedTokenWithContextList =
                        tokenWithContextList.map { tokenWithContext ->
                            adaptCasingForTokenWithContext(tokenWithContext)
                        }

                    val originalWords =
                        sentencePart.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    var runningIndex = 0

                    for (i in originalWords.indices) {
                        val original = originalWords[i]
                        val tokenWithContext = adaptedTokenWithContextList[i]
                        Log.d("Whisper", "original: $original")
                        val suggested =
                            tokenWithContext.suggestions.map { it.suggestion } // C'est maintenant une liste
                        for (suggest in suggested) {
                            Log.d("Whisper", "suggest: $suggest")
                        }

                        if (suggested.isNotEmpty() && !suggested.contains(original.trim())) {
                            // Trouvez l'indice de la nouvelle occurrence
                            val start =
                                HomeViewModelHolder.viewModel.transcription.value?.indexOf(
                                    original,
                                    runningIndex
                                )
                            Log.d("Whisper", "start: $start")
                            Log.d("Whisper", "original: $original")

                            // Mettez à jour l'indice de début pour les recherches futures
                            runningIndex = start?.plus(original.length)?.inc()!!
                            Log.d("Whisper", "runningIndex: $runningIndex")

                            HomeViewModelHolder.viewModel.misspelledWords.add(
                                MisspelledWordInfo(
                                    start,
                                    start.plus(original.length),
                                    suggested  // C'est maintenant une liste
                                )
                            )
                        } else {
                            // Si le mot est correct ou si les suggestions contiennent le mot original,
                            // mettre à jour runningIndex pour ignorer ce mot lors de la prochaine recherche
                            runningIndex += original.length + 1 // +1 pour un éventuel espace ou signe de ponctuation
                        }
                    }
                }
            }
        }

    fun gatherSpellingSuggestions(text: String) = CoroutineScope(Dispatchers.IO).launch {
        // Remplacer les sauts de ligne et la ponctuation par des espaces
        val cleanedText = text.replace("\n\n", " ").replace(Regex("[,.!;?:\"\\\\]"), " ")

        // Diviser le texte en mots en utilisant l'espace comme séparateur
        val words = cleanedText.split(" ").filter { it.isNotEmpty() }

        val wordsWithOriginalCasing = words.map {
            val start = cleanedText.indexOf(it)
            WordInfo(it.lowercase(), it, start)
        }

        withContext(Dispatchers.Default) {
            for (wordInfo in wordsWithOriginalCasing) {
                val futureSuggestions =
                    SpellCheckerSingleton.spellChecker?.suggestWord(wordInfo.lowercasedWord)
                        ?.asDeferred()
                val suggestions =
                    futureSuggestions?.await() // Convertit le CompletableFuture en une coroutine suspendue
                if (!suggestions.isNullOrEmpty()) {
                    val adaptedSuggestions =
                        suggestions.map { adaptCasing(wordInfo.originalWord, it) }
                    for (suggestion in adaptedSuggestions) {
                        if (suggestion != wordInfo.originalWord) {
                            Log.d(
                                "Whisper",
                                "Suggestion: $suggestion for word: ${wordInfo.originalWord}"
                            )
                            val end = wordInfo.start + wordInfo.originalWord.length
                            HomeViewModelHolder.viewModel.misspelledWords.add(
                                MisspelledWordInfo(
                                    wordInfo.start,
                                    end,
                                    adaptedSuggestions
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        val preferences =
            applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)

        job = Job()
        scope = CoroutineScope(Dispatchers.IO + job)

        notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.cancel(43)

        val audioFilePath = inputData.getString("audioFilePath") ?: return Result.failure()
        val fileNameWithoutExtension =
            File(audioFilePath).name.replaceFirst("[.][^.]+$".toRegex(), "")
        val outputFileName = "$fileNameWithoutExtension.wav"
        val outputFile = File(applicationContext.filesDir, outputFileName)
        val outputFilePath = outputFile.absolutePath

        //setForeground(createForegroundInfo("Transcription en cours"))

        return try {
            if (!isStopped) {
                setForegroundAsync(getForegroundInfo())
            } else {
                preferences.edit().putBoolean("stoppedBeforeInference", true).apply()
                Result.failure()
            }
            val selectedItem = preferences!!.getString("selectedDictionaryId", null)
            if (SpellCheckerSingleton.spellChecker == null && selectedItem != null) {
                initSpellChecker(applicationContext)
            }
            // Conversion de l'audio en WAV
            if (convertAudioToWav(audioFilePath, outputFilePath, cancellationSignal, applicationContext)) {
                loadModel()

                whisperContext?.setProgressCallback(progressListener)
                whisperContext?.setSegmentCallback(segmentListener)
                whisperContext?.setInferenceStoppedCallback(inferenceStoppedListener)

                val showText2 =
                    "Le modèle a été chargé avec succès ! La transcription est en cours et va s'afficher bientôt. Veuillez patienter..."

                HomeViewModelHolder.viewModel.saveTemporaryTranscription(showText2)
                //scope.launch {
                //    progress.collect { progressValue ->
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

                var previousGeneration: String = initialPrompt
                var previousAudio: FloatArray = floatArrayOf()
                var startOffset = 0  // Position initiale de départ pour le prochain segment
                var seekDelta: Int
                var resultLen: Int

                for (segment in segments) {
                    Log.d("Whisper", "Début du traitement du segment, startOffset: $startOffset")

                    // Ajustez la plage du segment en utilisant startOffset et seekDelta
                    val adjustedSegment = segment.sliceArray(startOffset until segment.size)

                    // Concaténer avec l'audio précédent conservé
                    val combinedSegment = previousAudio + adjustedSegment
                    Log.d("Whisper", "Taille du segment combiné: ${combinedSegment.size}")

                    val textSegment = whisperContext?.transcribeData(
                        combinedSegment,
                        selectedLanguage,
                        selectedLanguageToIgnore,
                        translate,
                        speed,
                        previousGeneration,
                        maxSize,
                        offset_ms,
                        duration_ms
                    )

                    seekDelta =
                        whisperContext?.getSeekDelta() ?: 0  // Mettez à jour à partir de l'API
                    resultLen =
                        whisperContext?.getResultLen() ?: 0  // Mettez à jour à partir de l'API

                    Log.d("Whisper", "seekDelta: $seekDelta")
                    Log.d("Whisper", "resultLen: $resultLen")

                    // Conserver une partie du segment pour le prochain tour
                    val keepIndex = segment.size - seekDelta
                    if (keepIndex > 0 && keepIndex < segment.size) {
                        previousAudio = segment.sliceArray(keepIndex until segment.size)
                    } else {
                        previousAudio = floatArrayOf()
                    }

                    Log.d("Whisper", "Taille de l'audio précédent conservé: ${previousAudio.size}")

                    startOffset += seekDelta  // Mettez à jour startOffset pour le prochain segment
                    previousGeneration = textSegment?.takeLast(resultLen) ?: ""

                    Log.d("Whisper", "previousGeneration: $previousGeneration")

                    currentSegmentIndex++
                }
                reachInference = false

                HomeViewModelHolder.viewModel.saveTemporaryTranscription(fullTranscription.toString())
                //saveTranscription(fullTranscription.toString())

                Log.d("Whisper", "Substring: start is $end")
                paragraphe = fullTranscription.substring(end)

                if (SpellCheckerSingleton.isSpellCheckerReady.value == true) {
                    // Traitement habituel
                    Log.d("Whisper", "Déjà prêt lors de l'inférence")
                    gatherContextualSpellingSuggestions(paragraphe)
                    HomeViewModelHolder.viewModel.isNowReadyToCorrect.postValue(true)
                } else {
                    Log.d("Whisper", "On doit attendre que SpellChecker soit prêt")
                    // Ajoutez à la file d'attente en attendant que le SpellChecker soit prêt
                    HomeViewModelHolder.viewModel.pendingSegments.add(paragraphe)
                }

                showCompletionNotification()

                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("Whisper", e.toString())
            Result.failure()
        } finally {
            Log.d("Whisper", "Call to finally")
            outputFile.delete()
            //releaseResources()
        }
    }
}