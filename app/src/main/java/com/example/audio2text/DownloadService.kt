package com.example.audio2text

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.Service.START_NOT_STICKY
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.reactivex.annotations.Nullable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.absoluteValue
import kotlin.math.roundToInt


class DownloadService : Service() {
    override fun onCreate() {
        super.onCreate()
        // Initialize download variables
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start download
        if (intent?.getStringExtra("dictionaryId") == null) {
            deleteExistingModels()
            startForeground(NOTIFICATION_ID, createNotification())
            notificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            intent?.getStringExtra("modelName")?.let {
                downloadModel(it)
            }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                downloadDictionary(intent.getStringExtra("dictionaryId")!!)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d("Whisper", "Appel on Destroy")
        notificationManager.cancel(1)
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private var totalBytesReadGlobal: Long = 0L
    private lateinit var notificationManager: NotificationManager
    val viewModel = DownloadViewModelHolder.viewModel
    val dictionaryViewModel = DictionaryViewModelHolder.viewModel

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

    fun readFileMetadataFromXML(context: Context, fileName: String): FileMetadata? {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val assetManager = context.assets
        val inputStream = assetManager.open("dictionaries_202308_files.xml")
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

    fun downloadDictionary(id: String, retryCount: Int = 0) {
        if (!isInternetAvailable()) {
            Toast.makeText(
                applicationContext,
                "Veuillez activer Internet pour télécharger le modèle",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val maxRetry = 3

        if (retryCount >= maxRetry) {
            // Arrêter les tentatives et notifier l'utilisateur ou le journal
            return
        }

        // Initialisation du client OkHttp
        val client = OkHttpClient()

        val dictionaryItem: DictionaryItem? =
            DictionaryManager.getDictionaryItemById(id) // Supposons que cette fonction existe et retourne un DictionaryItem
        val baseURL = "https://archive.org/download/dictionaries_202308/"
        val url = "$baseURL${dictionaryItem?.fileRelativePath}"

        val destinationPath =
            dictionaryItem?.fileRelativePath?.let {
                File(
                    applicationContext.filesDir,
                    it
                ).absolutePath
            }

        if (isInternetAvailable()) {
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(
                        ContentValues.TAG,
                        "Erreur lors du téléchargement du dictionnaire: ${e.message}"
                    )
                    dictionaryViewModel.updateDictionaryState(id) { state ->
                        state.isDownloading = false
                        state.isDownloadComplete = false
                        state.isFailed = true
                        state.progress = 0
                    }
                    downloadDictionary(id, retryCount + 1)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val metadata = readFileMetadataFromXML(applicationContext, dictionaryItem?.fileRelativePath!!)
                        if (metadata != null) {
                            println("SHA1: ${metadata.sha1}, Size: ${metadata.size}")
                        } else {
                            println("File not found")
                            throw IOException("File not found")
                        }
                        val body = response.body
                        val contentLength = metadata.size
                        Log.d("Whisper", "Content-Length: $contentLength")
                        val source = body?.source()

                        val sink = destinationPath?.let { File(it).sink().buffer() }

                        var bytesRead = 0
                        var totalBytesRead = 0L
                        val buffer = ByteArray(8 * 1024) // Taille du tampon: 8KB


                        while (source?.read(buffer).also { bytesRead = it ?: -1 } != -1) {
                            sink?.write(buffer, 0, bytesRead)
                            // digest.update(buffer, 0, bytesRead) // Si vous avez besoin de mettre à jour un MessageDigest
                            totalBytesRead += bytesRead
                            val progressPercent = (totalBytesRead * 100 / contentLength).toInt()
                            Log.d("Whisper", "Progress: $progressPercent")

                            // Mettre à jour l'état de téléchargement (vous pouvez utiliser votre ViewModel ou autre mécanisme)
                            dictionaryViewModel.updateDictionaryState(id) { state ->
                                state.isDownloading = true
                                state.isDownloadComplete = false
                                state.isFailed = false
                                state.progress = progressPercent
                            }
                        }

                        sink?.flush()
                        sink?.close()
                        source?.close()

                        val downloadedFile = destinationPath?.let { File(it) }
                        val calculatedSHA1 = calculateSHA1(downloadedFile!!)
                        Log.d("Whisper", "Calculated SHA1: $calculatedSHA1")
                        Log.d("Whisper", "Destination path: ${dictionaryItem.fileRelativePath}")
                        val expectedSHA1 = metadata?.sha1
                        Log.d("Whisper", "Expected SHA1: $expectedSHA1")

                        // Votre logique pour vérifier SHA1
                        if (calculatedSHA1 == expectedSHA1) {
                            Log.d("Whisper", "SHA1 OK")
                            dictionaryViewModel.updateDictionaryState(id) { state ->
                                state.isDownloading = false
                                state.isDownloadComplete = true
                                state.isFailed = false
                                state.progress = 100
                            }
                        } else {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (retryCount < maxRetry && isInternetAvailable()) {
                                    downloadDictionary(id, retryCount + 1)
                                } else {
                                    // Si la reconnexion échoue, réinitialiser l'interface utilisateur
                                    dictionaryViewModel.updateDictionaryState(id) { state ->
                                        state.isDownloading = false
                                        state.isDownloadComplete = false
                                        state.isFailed = true
                                        state.progress = 0
                                    }
                                }
                            }, 5000)
                        }
                    } catch (e: Exception) {
                        Log.d("Whisper", "Exception: ${e.message}")
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (retryCount < maxRetry && isInternetAvailable()) {
                                downloadDictionary(id, retryCount + 1)
                            } else {
                                // Si la reconnexion échoue, réinitialiser l'interface utilisateur
                                dictionaryViewModel.updateDictionaryState(id) { state ->
                                    state.isDownloading = false
                                    state.isDownloadComplete = false
                                    state.isFailed = true
                                    state.progress = 0
                                }
                            }
                        }, 5000)
                    }
                }
            })
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

                    // Créer une notification de progression
                    val notificationBuilder =
                        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                            .setContentTitle("Téléchargement du modèle en cours")
                            .setSmallIcon(com.example.audio2text.R.drawable.download_icon) // Votre icône de téléchargement
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setProgress(100, 0, false)

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
                            notificationBuilder.setProgress(100, progressPercent, false)
                            notificationManager.notify(1, notificationBuilder.build())
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
                    notificationBuilder.setContentTitle("Téléchargement du modèle terminé")
                        .setContentText("Appuyez pour ouvrir l'application")
                        .setContentIntent(pendingIntent)
                        .setProgress(0, 0, false)
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
                .setSmallIcon(com.example.audio2text.R.drawable.download_icon) // Votre icône de téléchargement
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setProgress(100, 0, false)
        return notificationBuilder.build()
    }

    companion object {
        // Constants for notification
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_channel"
    }
}