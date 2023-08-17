package com.example.audio2text

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Okio
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

class ModelSelectionFragment : Fragment() {

    private var selectedModel: String = "Moyen"
    private lateinit var progressBar: ProgressBar
    private lateinit var textModelSelection: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var downloadModelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_model_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = view.findViewById(R.id.progressBar)
        downloadModelButton = view.findViewById(R.id.downloadModelButton)
        radioGroup = view.findViewById(R.id.radioGroup)
        textModelSelection = view.findViewById(R.id.textModelSelection)

        val radioGroup: RadioGroup = view.findViewById(R.id.radioGroup)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedModel = when (checkedId) {
                R.id.rapidModel -> "Rapide"
                R.id.mediumModel -> "Moyen"
                R.id.preciseModel -> "Précis"
                else -> "Moyen" // Default selection
            }
        }

        downloadModelButton.setOnClickListener {
            downloadModel(selectedModel)
        }
    }

    private fun downloadModel(modelName: String) {
        // Récupérer le chemin du fichier à partir des préférences partagées
        val preferences =
            requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val existingFilePath = preferences.getString(modelName, null)

        if (existingFilePath != null) {
            val file = File(existingFilePath)
            if (file.exists()) {
                setModelPath(existingFilePath)
                Toast.makeText(context, "Modèle trouvé et prêt à être utilisé", Toast.LENGTH_SHORT)
                    .show()
                return
            }
        }

        if (!isInternetAvailable()) {
            Toast.makeText(
                context,
                "Veuillez activer Internet pour télécharger le modèle",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val modelFileName = when (modelName) {
            "Rapide" -> "ggml-base.bin"
            "Moyen" -> "ggml-small.bin"
            "Précis" -> "ggml-medium.bin"
            else -> return
        }

        val url = "https://archive.org/download/whisper-models/$modelFileName"
        Log.d(TAG, "URL : $url")
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        // Afficher la barre de progression
        progressBar.visibility = View.VISIBLE
        radioGroup.visibility = View.INVISIBLE
        downloadModelButton.isEnabled = false
        downloadModelButton.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                R.color.gray
            )
        )
        textModelSelection.text = "Téléchargement de $modelFileName en cours..."

        Log.d(TAG, "Démarrage du téléchargement du modèle: $modelName") // Log pour le début

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                call.cancel()
                Log.e(TAG, "Échec du téléchargement du modèle: $e") // Log pour les erreurs
                activity?.runOnUiThread {
                    Toast.makeText(
                        context,
                        "Échec du téléchargement : ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val file = File(requireContext().filesDir, modelFileName)
                val sink = file.sink().buffer()
                val responseBody = response.body
                val contentLength = responseBody?.contentLength() ?: 0L
                val source = responseBody?.source()

                var totalBytesRead = 0L
                val bufferSize = 8 * 1024L
                var bytesRead = source?.read(sink.buffer, bufferSize)
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime
                var lastBytesRead = 0L

                // Créer un canal de notification si nécessaire (Android O et versions ultérieures)
                val channel = NotificationChannel(
                    "download_channel",
                    "Téléchargements",
                    NotificationManager.IMPORTANCE_LOW
                )
                val notificationManager =
                    requireContext().getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)

                // Créer une notification de progression
                val notificationBuilder =
                    NotificationCompat.Builder(requireContext(), "download_channel")
                        .setContentTitle("Téléchargement du modèle en cours")
                        .setSmallIcon(R.drawable.download_icon) // Votre icône de téléchargement
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setProgress(100, 0, false)

                while (bytesRead != -1L) {
                    sink.emit()
                    totalBytesRead += bytesRead!!
                    val progressPercent = (totalBytesRead * 100 / contentLength).toInt()
                    val currentTime = System.currentTimeMillis()
                    val elapsedTimeInSeconds = (currentTime - startTime) / 1000
                    val timeSinceLastUpdateInSeconds = (currentTime - lastUpdateTime) / 1000

                    // Mettre à jour la TextView toutes les 2 secondes (ou autre intervalle de votre choix)
                    if (timeSinceLastUpdateInSeconds >= 2) {
                        val bytesSinceLastUpdate = totalBytesRead - lastBytesRead
                        val downloadSpeed = bytesSinceLastUpdate / timeSinceLastUpdateInSeconds
                        val remainingBytes = contentLength - totalBytesRead
                        val estimatedTimeRemainingInSeconds = remainingBytes / downloadSpeed
                        val estimatedTimeRemainingInMinutes = estimatedTimeRemainingInSeconds / 60

                        activity?.runOnUiThread {
                            progressBar.progress = progressPercent
                            textModelSelection.text =
                                "Téléchargement de $modelFileName en cours : ${estimatedTimeRemainingInMinutes.toInt()} minutes restantes.."
                        }
                        notificationBuilder.setProgress(100, progressPercent, false)
                        notificationManager.notify(1, notificationBuilder.build())
                        lastUpdateTime = currentTime
                        lastBytesRead = totalBytesRead
                    }

                    bytesRead = source?.read(sink.buffer, bufferSize)
                }

                textModelSelection.text = "Téléchargement de $modelFileName terminé"

                // Modifier la notification lorsque le téléchargement est terminé
                val intent = Intent(requireContext(), MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                notificationBuilder.setContentTitle("Téléchargement du modèle terminé")
                    .setContentText("Appuyez pour ouvrir l'application")
                    .setContentIntent(pendingIntent)
                    .setProgress(0, 0, false)
                    .setAutoCancel(true) // Fermer la notification lorsqu'elle est cliquée

                notificationManager.notify(1, notificationBuilder.build())

                Log.d(TAG, "Téléchargement du modèle terminé") // Log pour la fin

                sink.flush()
                sink.close()

                preferences.edit().putString(modelName, file.absolutePath).apply()
                setModelPath(file.absolutePath)

                activity?.runOnUiThread {
                    Toast.makeText(context, "Modèle téléchargé avec succès", Toast.LENGTH_SHORT)
                        .show()
                    progressBar.visibility = View.GONE // Cachez la barre de progression
                    downloadModelButton.visibility = View.GONE

                    // Trouvez le bouton suivant et définissez son OnClickListener
                    val nextButton: FloatingActionButton = view?.findViewById(R.id.nextArrow)!!
                    nextButton.setOnClickListener {
                        val intentStart = Intent(activity, MainActivity::class.java)
                        startActivity(intentStart)
                    }

                    // Afficher le bouton suivant
                    nextButton.visibility = View.VISIBLE

                    // Charger et lancer l'animation
                    val animation =
                        AnimationUtils.loadAnimation(requireContext(), R.anim.transition_animation)
                    nextButton.startAnimation(animation)
                }
            }
        })
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || networkCapabilities.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        )
    }

    private fun setModelPath(filePath: String) {
        MainActivity.modelPath = filePath
    }
}