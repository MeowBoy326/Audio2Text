package com.example.audio2text

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.reactivex.annotations.Nullable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSink
import okio.appendingSink
import okio.buffer
import okio.sink
import org.w3c.dom.Element
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.xml.parsers.DocumentBuilderFactory


class DownloadService : Service() {
    override fun onCreate() {
        super.onCreate()
        // Initialize download variables
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Start download
        if (intent?.getBooleanExtra("isDownloadingDictionary", false) != true) {
            deleteExistingModels()
            startForeground(NOTIFICATION_ID, createNotification())
            intent?.getStringExtra("modelName")?.let {
                downloadModel(it)
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                manageDownloads()
                //downloadDictionary(intent.getStringExtra("dictionaryId")!!)
            }
        }

        return START_NOT_STICKY
    }

    private var myDisposableGlobal: Disposable? = null
    val activeDownloads: BehaviorSubject<Int> = BehaviorSubject.createDefault(0)

    fun manageDownloads() {
        // Convertir la file d'attente en Observable
        val downloadQueueObservable = Observable.fromIterable(DownloadQueueManager.downloadQueue)

        myDisposableGlobal = downloadQueueObservable
            .flatMap({ id ->
                downloadDictionaryObservable(id)
                    .doOnSubscribe {
                        activeDownloads.onNext(activeDownloads.value?.plus(1)!!)
                    }
                    .doOnTerminate {
                        activeDownloads.onNext(activeDownloads.value?.minus(1)!!)
                    }
            }, DownloadQueueManager.MAX_SIMULTANEOUS_DOWNLOADS) // Limite de concurrence
            .subscribeOn(Schedulers.computation())  // Exécute le code de création sur le thread IO
            .observeOn(Schedulers.computation())  // Observe les résultats sur le thread principal (UI)
            .subscribe(
                { response -> /* traitement de la réponse réussie */ },
                { error -> /* traitement de l'erreur */ }
            )
    }

    override fun onDestroy() {
        Log.d("Whisper", "Appel on Destroy")
        notificationManager.cancel(1)
        // Se désinscrire pour éviter les fuites de mémoire
        myDisposable?.dispose()
        myDisposableGlobal?.dispose()
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private var totalContentLength: Long = 0
    private var totalBytesReadGlobal: Long = 0L
    private var totalBytesReadDictionaries: Long = 0L
    private lateinit var notificationManager: NotificationManager
    val viewModel = DownloadViewModelHolder.viewModel
    val dictionaryViewModel = DictionaryViewModelHolder.viewModel
    private var myDisposable: Disposable? = null
    var globalProgress: MutableMap<String, Int> = mutableMapOf()
    var downloadStates: MutableMap<String, Boolean> = mutableMapOf()
    var sha1ForDictionaries: MutableMap<String, String> = mutableMapOf()

    private fun updateDownloadUI(
        progress: Int,
        status: String,
        isDownloading: Boolean,
        isDownloadComplete: Boolean = false,
        isFailed: Boolean = false
    ) {
        //val intent = Intent("DOWNLOAD_UPDATE")
        viewModel.downloadProgress.postValue(progress)
        viewModel.downloadStatus.postValue(status)
        viewModel.isDownloading.postValue(isDownloading)
        viewModel.isDownloadComplete.postValue(isDownloadComplete)
        viewModel.isFailed.postValue(isFailed)
    }

    fun deleteExistingModels() {
        // Liste des noms de fichiers de modèles
        val modelFileNames = listOf("ggml-base.bin", "ggml-medium.bin", "ggml-small.bin")

        // Parcourir la liste et supprimer les fichiers existants
        modelFileNames.forEach { fileName ->
            val fileToDelete = File(applicationContext.filesDir, fileName)
            if (fileToDelete.exists()) {
                if (fileToDelete.delete()) {
                    Log.d(ContentValues.TAG, "Modèle $fileName supprimé avec succès")
                } else {
                    Log.e(ContentValues.TAG, "Échec de la suppression du modèle $fileName")
                }
            }
        }
    }

    fun calculateSHA1(file: File): String {
        val buffer = ByteArray(8192)
        val sha1 = MessageDigest.getInstance("SHA-1")

        val input = file.inputStream()
        var read: Int

        while (input.read(buffer).also { read = it } > 0) {
            sha1.update(buffer, 0, read)
        }

        val bytes = sha1.digest()
        val sb = StringBuilder()

        for (b in bytes) {
            sb.append(String.format("%02X", b))
        }

        return sb.toString().lowercase(Locale.ROOT)
    }

    data class FileMetadata(val sha1: String, val size: Long)

    fun readFileMetadataFromXML(
        xmlFile: String,
        context: Context,
        fileName: String
    ): FileMetadata? {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val assetManager = context.assets
        val inputStream = assetManager.open(xmlFile)
        val document = builder.parse(inputStream)

        val nodeList = document.getElementsByTagName("file")
        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            val attributes = node.attributes
            val nameAttr = attributes.getNamedItem("name")

            if (nameAttr != null && nameAttr.textContent == fileName) {
                val element = node as Element
                val sha1List = element.getElementsByTagName("sha1")
                val sizeList = element.getElementsByTagName("size")

                val sha1Node = sha1List.item(0)?.textContent ?: ""
                val sizeNode = sizeList.item(0)?.textContent?.toLong() ?: 0L

                return FileMetadata(sha1 = sha1Node, size = sizeNode)
            }
        }
        return null
    }

    fun updateDictionaryStateFailure(id: String, url: String? = null, error: String? = null) {
        val destinationPath = if (url != null && url.contains("dictionaries")) {
            File(
                applicationContext.filesDir,
                DictionaryManager.getDictionaryItemById(id)?.fileRelativePath!!
            ).absolutePath
        } else {
            File(
                applicationContext.filesDir,
                "big_${DictionaryManager.getDictionaryItemById(id)?.id}.txt"
            ).absolutePath
        }
        if (File(destinationPath).exists()) {
            File(destinationPath).delete()
        }
        dictionaryViewModel.updateDictionaryState(id) { state ->
            state.isDownloading = false
            state.isDownloadComplete = false
            state.isFailed = true
            state.progress = 0
        }
        DownloadQueueManager.downloadQueue.remove(id)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                applicationContext,
                error ?: "Échec du téléchargement après plusieurs tentatives, veuillez réessayer.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    fun downloadDictionaryObservable(id: String, retryCount: Int = 0): Observable<DownloadResult> {
        return Observable.create { emitter ->
            if (!isInternetAvailable()) {
                emitter.onError(Exception("Veuillez activer Internet pour télécharger le modèle"))
                return@create
            }

            val file = File(applicationContext.filesDir, "dic_$id.txt")
            val file2 = File(applicationContext.filesDir, "big_$id.txt")
            if (file.exists() && file2.exists()) {
                emitter.onComplete()
                return@create
            }

            val maxRetry = 3

            if (retryCount >= maxRetry) {
                emitter.onError(Exception("Échec du téléchargement après plusieurs tentatives"))
                return@create
            }

            val dictionaryItem = DictionaryManager.getDictionaryItemById(id)
            val urls = listOf(
                "https://huggingface.co/datasets/rahalmichel/dictionaries/resolve/main/${dictionaryItem?.fileRelativePath}",
                "https://huggingface.co/datasets/rahalmichel/bigrams/resolve/main/big_${dictionaryItem?.id}.txt"
            )

            downloadStates = urls.associateBy({ it }, { false }).toMutableMap()
            globalProgress = urls.associateBy({ it }, { 0 }).toMutableMap()

            myDisposable = Observable.fromIterable(urls)
                .flatMap { url ->
                    val timeoutSignal = Observable.timer(30, TimeUnit.SECONDS)
                        .flatMap { Observable.error<Response>(DownloadTimeoutException(url)) }
                    downloadFileObservable(url)
                        .takeUntil(timeoutSignal)
                        .map<DownloadResult> { response ->
                            DownloadResult.Success(Triple(url, response, id))
                        }
                        .onErrorResumeNext { throwable: Throwable ->
                            Observable.just(DownloadResult.Failure(Triple(url, throwable, id)))
                        }
                }
                .subscribeOn(Schedulers.computation())  // Exécute le code de création sur le thread IO
                .observeOn(Schedulers.computation())  // Observe les résultats sur le thread principal (UI)
                .timeout(30, TimeUnit.SECONDS)
                .subscribe({ result ->
                    when(result) {
                        is DownloadResult.Success -> {
                            processDownloadedFile(Pair(result.triple.first, result.triple.second), result.triple.third)
                        }
                        is DownloadResult.Failure -> {
                            updateDictionaryStateFailure(
                                result.triple.third,
                                result.triple.first,
                                "Erreur de téléchargement !"
                            )
                        }
                    }
                }, { error ->
                    if (error is DownloadTimeoutException) {
                        emitter.onError(Exception("Le téléchargement a expiré. Veuillez recommencer !"))
                        dictionaryItem?.id?.let { updateDictionaryStateFailure(it, error.url,"Le téléchargement a expiré. Veuillez recommencer !") }
                    } else {
                        emitter.onError(error)
                    }
                }, {
                    emitter.onComplete()
                })
        }
    }

    fun processDownloadedFile(pair: Pair<String, Response>, id: String) {
        val url = pair.first
        val response = pair.second

        val dictionaryItem = DictionaryManager.getDictionaryItemById(id)

        val destinationPath = if (url.contains("dictionaries")) {
            File(applicationContext.filesDir, dictionaryItem?.fileRelativePath!!).absolutePath
        } else {
            File(applicationContext.filesDir, "big_${dictionaryItem?.id}.txt").absolutePath
        }

        val sink = File(destinationPath).sink().buffer()
        val source = response.body?.source()

        //startTime = System.currentTimeMillis()
        //var lastUpdateTime = startTime

        var bytesRead = 0
        var totalBytesRead = 0L
        val buffer = ByteArray(8 * 1024)
        var startTimeDictionary = System.currentTimeMillis()

        try {

            while (source?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                sink.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                //totalBytesReadDictionaries += bytesRead
                //val progressPercent = (totalBytesReadDictionaries * 100 / totalContentLength).toInt()
                //globalProgress[url] = progressPercent
                //val totalProgressDictionary =
                //    globalProgress.values.sum() / globalProgress.values.size

                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - startTimeDictionary

                // Mettre à jour plus fréquemment, par exemple tous les 500 ms
                if (timeSinceLastUpdate >= 200) {
                    startTimeDictionary = currentTime
                    dictionaryViewModel.updateDictionaryState(id) { state ->
                        state.isDownloading = true
                        state.isDownloadComplete = false
                        state.isFailed = false
                    }
                }
            }

            sink.flush()
            sink.close()
            source?.close()

            val downloadedFile = File(destinationPath)
            /*val calculatedSHA1 = calculateSHA1(downloadedFile)

            if (calculatedSHA1 == sha1ForDictionaries[url]) {
                Log.d("Whisper", "SHA1 OK")
                downloadStates[url] = true
            } else {
                downloadStates[url] = false
                updateDictionaryStateFailure(id, url)
            }*/

            downloadStates[url] = true

            val allSuccess = downloadStates.values.all { it }
            Log.d("DownloadService", "allSuccess: $allSuccess")
            if (allSuccess) {
                dictionaryViewModel.updateDictionaryState(id) { state ->
                    state.isDownloading = false
                    state.isDownloadComplete = true
                    state.isFailed = false
                }
            }
        } catch (e: IOException) {
            updateDictionaryStateFailure(id, url)
        }
    }

    fun downloadFileObservable(url: String): Observable<Response> {
        return Observable.create { emitter ->
            if (emitter.isDisposed) return@create

            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                if (!emitter.isDisposed) {
                    emitter.onNext(response)
                    emitter.onComplete()
                }
            } else {
                if (!emitter.isDisposed) {
                    emitter.onError(Exception("Failed to download"))
                }
            }
        }
    }

    private fun downloadModel(modelName: String, startByte: Long = 0, retryCount: Int = 0) {
        // Récupérer le chemin du fichier à partir des préférences partagées
        val preferences =
            applicationContext.getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val existingFilePath = preferences.getString("whisper_model", null)

        if (existingFilePath != null) {
            val file = File(existingFilePath)
            if (file.exists()) {
                //setModelPath(existingFilePath)
                Toast.makeText(
                    applicationContext,
                    "Modèle trouvé et prêt à être utilisé",
                    Toast.LENGTH_SHORT
                )
                    .show()
                return
            }
        }

        if (!isInternetAvailable()) {
            Toast.makeText(
                applicationContext,
                "Veuillez activer Internet pour télécharger le modèle",
                Toast.LENGTH_SHORT
            ).show()
            updateDownloadUI(
                0,
                "Erreur de téléchargement, veuillez réessayer !",
                false,
                false,
                true
            )
            return
        }

        // Valeurs attendues pour chaque modèle
        val modelDetails = when (modelName) {
            "Rapide" -> Triple(
                "ggml-base.bin",
                "a4f49b415dc06a8c27c1e6006dffcf63da1e6ec35c39e45ed8401dd0eeedaecb",
                147951482L
            )

            "Moyen" -> Triple(
                "ggml-small.bin",
                "a46a837a38c3847b469270db9a5f1d737625928d0e487e97e0665941f49efc00",
                487601984L
            )

            "Précis" -> Triple(
                "ggml-medium.bin",
                "84b573e702358589b87257d145638f5e6383ac0fe474be723b0cf3977c64ce0b",
                1533763076L
            )

            else -> return
        }

        val modelFileName = modelDetails.first
        val expectedSha256 = modelDetails.second
        val expectedFileSize = modelDetails.third

        val url = "https://huggingface.co/rahalmichel/whisper-ggml/resolve/main/$modelFileName"
        val requestBuilder = Request.Builder().url(url)
        if (startByte > 0) {
            requestBuilder.header("Range", "bytes=$startByte-")
        }
        val request = requestBuilder.build()
        val client = OkHttpClient()

        updateDownloadUI(0, "Téléchargement de $modelFileName en cours...", true)

        Log.d(ContentValues.TAG, "Démarrage du téléchargement du modèle: $url") // Log pour le début

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d("Whisper", "Call to onFailure while download: $e")
                val notificationManager =
                    applicationContext.getSystemService(NotificationManager::class.java)
                notificationManager?.cancel(1)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val file = File(applicationContext.filesDir, modelFileName)
                    val sink: BufferedSink =
                        if (startByte > 0 && (response.code == 206 || response.header("Accept-Ranges") != null)) {
                            file.appendingSink().buffer()
                        } else {
                            file.sink().buffer()
                        }

                    val responseBody = response.body
                    val contentLength = responseBody?.contentLength() ?: 0L
                    val source = responseBody?.source()
                    val digest = MessageDigest.getInstance("SHA-256")

                    var totalBytesRead = startByte
                    var bytesRead: Int
                    val startTime = System.currentTimeMillis()
                    var lastUpdateTime = startTime
                    var lastBytesRead = 0L
                    val buffer = ByteArray(8 * 1024)

                    // Reste du code pour gérer le téléchargement (identique à ce que vous avez déjà)

                    while (source?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                        sink.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        totalBytesReadGlobal = totalBytesRead // Mettre à jour la variable globale
                        sink.emit()
                        totalBytesRead += bytesRead
                        val progressPercent = (totalBytesRead * 100 / contentLength).toInt()
                        val currentTime = System.currentTimeMillis()
                        //val elapsedTimeInSeconds = (currentTime - startTime) / 1000
                        val timeSinceLastUpdateInSeconds = (currentTime - lastUpdateTime) / 1000

                        // Mettre à jour la TextView toutes les 2 secondes (ou autre intervalle de votre choix)
                        if (timeSinceLastUpdateInSeconds >= 2) {
                            //val bytesSinceLastUpdate = totalBytesRead - lastBytesRead
                            //val downloadSpeed = bytesSinceLastUpdate / timeSinceLastUpdateInSeconds
                            //val remainingBytes = contentLength - totalBytesRead
                            //val estimatedTimeRemainingInSeconds = remainingBytes.toDouble() / downloadSpeed
                            //val estimatedTimeRemainingInMinutes = (estimatedTimeRemainingInSeconds / 60.0).roundToInt().absoluteValue

                            updateDownloadUI(
                                progressPercent,
                                "Téléchargement de $modelFileName en cours...",
                                true
                            )
                            //notificationBuilder.setProgress(100, progressPercent, false)
                            lastUpdateTime = currentTime
                            //lastBytesRead = totalBytesRead
                        }
                    }

                    sink.flush()
                    sink.close()

                    // Vérification de la somme de contrôle SHA-1
                    val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    Log.d("SHA-1", "Calculated SHA-1: $sha256, Expected SHA-1: $expectedSha256")
                    if (sha256 != expectedSha256) {
                        throw IOException("La somme de contrôle SHA-256 du fichier ne correspond pas !")
                    }

                    // Vérification de la taille du fichier
                    /*if (file.length() != expectedFileSize) {
                        throw IOException("La taille du fichier ne correspond pas !")
                    }*/

                    updateDownloadUI(100, "Téléchargement de $modelFileName terminé", false, true)

                    // Modifier la notification lorsque le téléchargement est terminé
                    val intent = Intent(applicationContext, MainActivity::class.java)
                    val pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val notificationBuilder =
                        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                            .setContentTitle("Téléchargement du modèle terminé")
                            .setContentText("Appuyez pour ouvrir l'application")
                            .setContentIntent(pendingIntent)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setSmallIcon(R.drawable.download_icon) // Votre icône de téléchargement
                            .setAutoCancel(true) // Fermer la notification lorsqu'elle est cliquée

                    notificationManager.notify(1, notificationBuilder.build())

                    Log.d(ContentValues.TAG, "Téléchargement du modèle terminé") // Log pour la fin

                    preferences.edit().putString("whisper_model", file.absolutePath).apply()

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            applicationContext,
                            "Modèle téléchargé avec succès",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                    //setModelPath(file.absolutePath)
                } catch (e: Exception) {
                    Log.e(ContentValues.TAG, "Échec du téléchargement du modèle: $e")
                    // Supprimer le fichier partiellement téléchargé
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (retryCount < 3 && isInternetAvailable()) {
                            totalBytesReadGlobal = 0L
                            deleteExistingModels()
                            // Si Internet est disponible, réessayer le téléchargement
                            downloadModel(modelName, totalBytesReadGlobal)
                        } else {
                            notificationManager.cancel(1)
                            // Si la reconnexion échoue, réinitialiser l'interface utilisateur
                            updateDownloadUI(0, "Échec du téléchargement", false, false, true)
                        }
                    }, 5000) // Réessayer après 10 secondes ou ajuster selon vos besoins
                }
            }
        })
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || networkCapabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        )
    }

    @Nullable
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotification(): Notification {
        // Create a notification for the foreground service
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setContentTitle("Téléchargement du modèle en cours")
                .setSmallIcon(R.drawable.download_icon) // Votre icône de téléchargement
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return notificationBuilder.build()
    }

    companion object {
        // Constants for notification
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_channel"
    }
}