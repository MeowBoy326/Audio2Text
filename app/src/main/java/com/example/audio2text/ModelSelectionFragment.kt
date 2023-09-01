package com.example.audio2text

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSink
import okio.appendingSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelSelectionFragment : Fragment() {

    private var selectedModel: String = "Moyen"
    private lateinit var progressBar: ProgressBar
    private lateinit var textModelSelection: TextView
    private lateinit var radioGroup: RadioGroup
    private lateinit var downloadModelButton: Button
    private lateinit var nextArrow: FloatingActionButton
    private lateinit var notificationManager: NotificationManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Créer l'instance de ViewModel
        //viewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        DownloadViewModelHolder.viewModel =
            ViewModelProvider(requireActivity())[DownloadViewModel::class.java]
        return inflater.inflate(R.layout.fragment_model_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBar = view.findViewById(R.id.progressBar)
        downloadModelButton = view.findViewById(R.id.downloadModelButton)
        nextArrow = view.findViewById(R.id.nextArrow3)
        radioGroup = view.findViewById(R.id.radioGroup)
        textModelSelection = view.findViewById(R.id.textModelSelection)

        DownloadViewModelHolder.viewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            progressBar.progress = progress
        }

        DownloadViewModelHolder.viewModel.isDownloading.observe(viewLifecycleOwner) { isDownloading ->
            if (isDownloading) {
                progressBar.visibility = View.VISIBLE
                radioGroup.visibility = View.INVISIBLE
                downloadModelButton.isEnabled = false
                downloadModelButton.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.gray
                    )
                )
                val thunderLogo = view.findViewById<ImageView>(R.id.thunder_logo)
                val fadeOutAnimation =
                    AnimationUtils.loadAnimation(requireContext(), R.anim.fade_out)
                thunderLogo?.startAnimation(fadeOutAnimation)
                thunderLogo?.visibility = View.INVISIBLE
            }
        }

        DownloadViewModelHolder.viewModel.isFailed.observe(viewLifecycleOwner) { isFailed ->
            if (isFailed) {
                // Réactiver le bouton de téléchargement
                downloadModelButton.isEnabled = true
                downloadModelButton.setBackgroundColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.colorPrimary // Remplacer par la couleur souhaitée
                    )
                )

                progressBar.visibility = View.INVISIBLE

                // Réafficher les radioButton
                radioGroup.visibility = View.VISIBLE

                // Réinitialiser le message initial
                textModelSelection.text =
                    "Erreur de connexion ! Veuillez télécharger le modèle à nouveau."

                // Afficher un Toast ou une autre indication pour informer l'utilisateur de l'échec
                Toast.makeText(
                    context,
                    "Échec du téléchargement. Veuillez réessayer.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        DownloadViewModelHolder.viewModel.downloadStatus.observe(viewLifecycleOwner) { status ->
            textModelSelection.text = status
        }

        DownloadViewModelHolder.viewModel.isDownloadComplete.observe(viewLifecycleOwner) { isDownloadComplete ->
            if (isDownloadComplete) {
                // Cachez la barre de progression, le bouton de téléchargement, etc.
                progressBar.visibility = View.GONE
                radioGroup.visibility = View.GONE

                textModelSelection.text = "Modèle chargé correctement !"

                val thunderLogo = view.findViewById<ImageView>(R.id.thunder_logo)
                val fadeInAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_in)
                thunderLogo?.startAnimation(fadeInAnimation)
                thunderLogo?.visibility = View.VISIBLE
                val preferences =
                    requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
                val lastPosition = preferences.getInt("LastPosition", 0)

                if (lastPosition >= 0) {
                    // Charger l'animation de translation
                    val animation =
                        AnimationUtils.loadAnimation(requireContext(), R.anim.transition_animation)

                    downloadModelButton.visibility = View.GONE

                    // Positionner la flèche au même endroit que le bouton, puis l'animer vers la droite
                    nextArrow.visibility = View.VISIBLE
                    nextArrow.startAnimation(animation)

                    // Définir un nouveau OnClickListener pour passer au fragment suivant
                    nextArrow.setOnClickListener {
                        // Appellez moveToPage() sur l'activité pour changer de page
                        (activity as? OnboardingPageChangeListener)?.moveToPage(4)
                    }
                } else {
                    radioGroup.visibility = View.VISIBLE

                    // Désactiver le bouton de confirmation
                    downloadModelButton.isEnabled = true
                    downloadModelButton.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.colorPrimary // Remplacer par la couleur souhaitée
                        )
                    )
                }
            }
        }

        DownloadViewModelHolder.viewModel.downloadStatus.postValue("Veuillez choisir un modèle pour continuer.")

        radioGroup = view.findViewById(R.id.radioGroup)

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedModel = when (checkedId) {
                R.id.rapidModel -> "Rapide"
                R.id.mediumModel -> "Moyen"
                R.id.preciseModel -> "Précis"
                else -> "Moyen" // Default selection
            }
        }

        downloadModelButton.setOnClickListener {
            val downloadIntent = Intent(requireContext(), DownloadService::class.java)
            Log.d(TAG, "Starting download for $selectedModel")
            downloadIntent.putExtra("modelName", selectedModel)
            requireContext().startForegroundService(downloadIntent)
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
            //textModelSelection.text = "Téléchargement de $selectedModel en cours..."
            //downloadModel(selectedModel)
        }

        val preferences = requireContext().getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        val isRunning = preferences.getBoolean("isRunning", false)

        downloadModelButton.isEnabled = !isRunning
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        val intent = Intent(requireContext(), DownloadService::class.java)
        requireContext().stopService(intent)
    }
}