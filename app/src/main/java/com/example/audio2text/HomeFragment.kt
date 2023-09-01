package com.example.audio2text

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Context.TEXT_SERVICES_MANAGER_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSubtype
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID


class HomeFragment : Fragment() {
    private lateinit var selectAudioFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var transcriptionText: EditText
    private lateinit var header: TextView
    val CHANNEL_ID = "transcription_channel"
    private lateinit var stopTranscriptionButton: FloatingActionButton

    // Global variable to keep track of the work request
    private lateinit var workRequest: OneTimeWorkRequest
    private val NOTIFICATION_ID = 42

    private lateinit var notificationManager: NotificationManager
    private lateinit var tempFile: File
    private val NOT_A_LENGTH = -1
    private var preferences: SharedPreferences? = null

    private lateinit var viewModel: HomeViewModel

    companion object {
        lateinit var selectFileButton: FloatingActionButton
    }

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            Log.d("Whisper", "onChange triggered")
            viewModel.loadTranscription()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        transcriptionText = view.findViewById(R.id.resultEditText)
        header = view.findViewById(R.id.header)
        stopTranscriptionButton = view.findViewById(R.id.stop_transcription_fab)
        selectFileButton = view.findViewById(R.id.select_file_fab)

        preferences = requireContext().getSharedPreferences(
            "MyPreferences",
            Context.MODE_PRIVATE
        )

        return view
    }

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "isRunning" -> {
                    val value = preferences?.getBoolean(key, false)!!
                    viewModel.updateLiveData(key, value)
                }

                "isFullyStopped" -> {
                    val value = preferences?.getBoolean(key, false)!!
                    viewModel.updateLiveData(key, value)
                }

                "isFullySuccessful" -> {
                    val value = preferences?.getBoolean(key, false)!!
                    viewModel.updateLiveData(key, value)
                }

                "isGoingToStop" -> {
                    val value = preferences?.getBoolean(key, false)!!
                    viewModel.updateLiveData(key, value)
                }
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialisez le ViewModel
        viewModel = ViewModelProvider(
            requireActivity()
        )[HomeViewModel::class.java]

        viewModel.spellCheckerReady.observe(viewLifecycleOwner) { isReady ->
            if (isReady) {
                // Utilisez votre SpellChecker
                if (viewModel.spellChecker != null) {
                    checkSpelling(transcriptionText.text.toString())
                }
            }
        }

        viewModel.viewModelScope.launch {
            TranscriptionContentProvider._lastTranscription.collect { newValue ->
                // Faire quelque chose avec newValue
                transcriptionText.setText(newValue?.content)
            }
        }

        // Utilisez les données du ViewModel pour restaurer l'état de vos éléments de vue
        viewModel.transcription.observe(viewLifecycleOwner) { transcription ->
            //transcriptionText.isVisible = true
            transcriptionText.setText(transcription)
        }

        viewModel.isSelectFileButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
            selectFileButton.isEnabled = isEnabled
        }

        viewModel.isTranscriptionTextEnabled.observe(viewLifecycleOwner) { isEnabled ->
            transcriptionText.isEnabled = isEnabled
        }

        viewModel.isTranscriptionTextVisible.observe(viewLifecycleOwner) { isVisible ->
            transcriptionText.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        viewModel.isStopTranscriptionButtonVisible.observe(viewLifecycleOwner) { isVisible ->
            stopTranscriptionButton.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        viewModel.isHeaderVisible.observe(viewLifecycleOwner) { isVisible ->
            header.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        viewModel.isSelectFileButtonVisible.observe(viewLifecycleOwner) { isVisible ->
            selectFileButton.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        requireActivity().contentResolver.registerContentObserver(
            TranscriptionContentProvider.CONTENT_URI,
            true,
            contentObserver
        )

        // Écoutez les changements dans isRunningLiveData
        viewModel.isRunningLiveData.observe(viewLifecycleOwner) { isRunning ->
            Log.d("isRunning state:", isRunning.toString())
            if (isRunning) {
                viewModel.isSelectFileButtonVisible.postValue(false)
                viewModel.isStopTranscriptionButtonVisible.postValue(true)
                viewModel.isHeaderVisible.postValue(false)
                viewModel.isTranscriptionTextEnabled.postValue(false)
                viewModel.isTranscriptionTextVisible.postValue(true)
            }
            //app.resetValues("isRunning")
            //app.resetValues(app.isRunningLiveData)
        }

        viewModel.isGoingToStopLiveData.observe(viewLifecycleOwner) { isGoingToStop ->
            Log.d("HomeFragment", "isGoingToStop : $isGoingToStop")
            if (isGoingToStop) {
                Log.d("isGoingToStop", "ENTERED!!!!")
                viewModel.isStopTranscriptionButtonVisible.postValue(false)
                viewModel.isSelectFileButtonVisible.postValue(true)
                viewModel.isSelectFileButtonEnabled.postValue(false)
            }
            //app.resetValues("isGoingToStop")
            //app.resetValues(app.isGoingToStopLiveData)
        }

        viewModel.isFullyStoppedLiveData.observe(viewLifecycleOwner) { isFullyStopped ->
            Log.d("HomeFragment", "isFullyStopped : $isFullyStopped")
            if (isFullyStopped) {
                viewModel.isHeaderVisible.postValue(true)
                viewModel.isTranscriptionTextVisible.postValue(false)
                viewModel.isStopTranscriptionButtonVisible.postValue(false)
                viewModel.isSelectFileButtonVisible.postValue(true)
                viewModel.isSelectFileButtonEnabled.postValue(true)
            }
            //app.resetValues("isFullyStopped")
            //app.resetValues(app.isFullyStoppedLiveData)
        }

        viewModel.isFullySuccessfulLiveData.observe(viewLifecycleOwner) { isFullySuccessful ->
            if (isFullySuccessful) {
                viewModel.isTranscriptionTextEnabled.postValue(true)
                viewModel.isStopTranscriptionButtonVisible.postValue(false)
                viewModel.isSelectFileButtonVisible.postValue(true)
            }
            //app.resetValues("isFullySuccessful")
            //app.resetValues(app.isFullySuccessfulLiveData)
        }

        stopTranscriptionButton.setOnClickListener {
            stopTranscription()
        }

        notificationManager =
            requireActivity().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Set up the ActivityResultLauncher
        selectAudioFileLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == AppCompatActivity.RESULT_OK && result.data != null) {
                    //viewModel.isTranscriptionTextVisible.value = true
                    //viewModel.isStopTranscriptionButtonVisible.value = true
                    //viewModel.isSelectFileButtonVisible.value = false
                    //viewModel.isTranscriptionTextEnabled.value = false
                    val selectedUri: Uri = result.data!!.data!!

                    // Lancez une coroutine pour exécuter le code bloquant
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Ouvrez un flux d'entrée à partir de l'URI
                        val inputStream =
                            requireActivity().contentResolver.openInputStream(selectedUri)

                        // Créez un fichier temporaire dans le répertoire cache de votre application
                        val tempFile =
                            File.createTempFile("media", null, requireActivity().cacheDir)

                        // Copiez le contenu du flux d'entrée dans le fichier temporaire
                        inputStream?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }

                        // Utilisez le chemin du fichier temporaire pour vos besoins
                        val tempFilePath = tempFile.absolutePath

                        preferences?.edit()?.putString("tempFilePath", tempFilePath)?.apply()

                        // Revenez au thread principal pour mettre à jour l'interface utilisateur
                        withContext(Dispatchers.Main) {
                            // Démarrez la transcription
                            startTranscription(tempFilePath)
                        }
                    }
                }
            }

        // Add a click listener to the select file button
        selectFileButton.setOnClickListener {
            // Prompt the user to select an audio file
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
            }
            // Make the select file button invisible
            //viewModel.isHeaderVisible.value = false
            selectAudioFileLauncher.launch(intent)
        }

        // Enregistrez le listener pour SharedPreferences
        preferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)

        val workId = preferences?.getString("WorkRequestId", null)?.let {
            UUID.fromString(it)
        }

        Log.d("WorkId", workId.toString())

        if (workId != null) {
            observeWorkStatus(workId)
        }
    }

    private fun startTranscription(inputFilePath: String) {
        val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf("audioFilePath" to inputFilePath))
            .build()

        preferences?.edit()?.putString("WorkRequestId", workRequest.id.toString())?.apply()

        WorkManager.getInstance(requireContext()).enqueue(workRequest)

        observeWorkStatus(workRequest.id)
    }

    private fun observeWorkStatus(workId: UUID) {
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(workId)
            .observe(requireActivity()) { workInfo ->
                Log.d("ENTERREEDDD:::", workInfo.toString())
                if (workInfo != null && workInfo.state.isFinished) {
                    // Update UI with progress here
                    if (workInfo.state == WorkInfo.State.CANCELLED || workInfo.state == WorkInfo.State.FAILED) {
                        // Handle cancellation here
                        Log.d("Whisper", "Job was cancelled")
                        val isStoppedBeforeInference =
                            preferences?.getBoolean("stoppedBeforeInference", false)!!
                        if (isStoppedBeforeInference) {
                            preferences?.edit()?.putBoolean("isRunning", false)?.apply()
                            preferences?.edit()?.putBoolean("isFullyStopped", true)?.apply()
                        } else {
                            preferences?.edit()?.putBoolean("isRunning", false)?.apply()
                            preferences?.edit()?.putBoolean("isGoingToStop", true)?.apply()
                        }

                        //viewModel.isStopTranscriptionButtonVisible.value = false
                        //viewModel.isSelectFileButtonVisible.value = true
                        //viewModel.isSelectFileButtonEnabled.value = false
                        lifecycleScope.launch(Dispatchers.Default) {
                            Log.d("Whisper", "Call to release on purpose")
                            TranscriptionWorker.releaseResources(requireContext(), true)
                        }


                        val notificationManager =
                            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(NOTIFICATION_ID)

                        Toast.makeText(
                            requireContext(),
                            "La transcription a été arrêtée",
                            Toast.LENGTH_SHORT
                        ).show()

                        preferences?.edit()?.putString("WorkRequestId", null)?.apply()
                    }

                    if (workInfo.state == WorkInfo.State.BLOCKED) {
                        // Handle cancellation here
                        Log.d("Whisper", "Job is blocked")
                    }

                    /*if (workInfo.state == WorkInfo.State.FAILED) {
                        // Handle cancellation here
                        Log.d("Whisper", "Job is failed")
                        lifecycleScope.launch(Dispatchers.Default) {
                            Log.d("Whisper", "Call to release on purpose")
                            TranscriptionWorker.releaseResources(requireContext(), true)
                        }

                        Toast.makeText(
                            requireContext(),
                            "Arrêt de tous les services...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }*/

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        // Handle finished state
                        Log.d(ContentValues.TAG, "Reach end of transcription")

                        preferences?.edit()?.putBoolean("isRunning", false)?.apply()
                        preferences?.edit()?.putBoolean("isFullySuccessful", true)?.apply()

                        //viewModel.isTranscriptionTextEnabled.value = true
                        //viewModel.isStopTranscriptionButtonVisible.value = false
                        //viewModel.isSelectFileButtonVisible.value = true

                        Log.d("END", "code reached at the end")
                        val intent = Intent(requireContext(), MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            requireContext(),
                            0,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE
                        )

                        // Logic to show "Transcription finished" notification
                        val finishedNotification =
                            NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                                .setContentTitle("Transcription terminée")
                                .setSmallIcon(R.drawable.notification_icon)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setContentIntent(pendingIntent) // Définissez l'intention pendante ici
                                .setAutoCancel(true) // Ferme automatiquement la notification après le clic
                                .build()

                        notificationManager.notify(NOTIFICATION_ID, finishedNotification)
                        preferences?.edit()?.putString("WorkRequestId", null)?.apply()
                        viewModel.initSpellChecker(requireContext())
                    }
                }
            }
    }

    fun checkSpelling(text: String) = lifecycleScope.launch {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val ssb = SpannableStringBuilder(text)
        var currentIndex = 0
        val modifications = mutableListOf<() -> Unit>()

        withContext(Dispatchers.Default) {
            for (word in words) {
                val start = currentIndex
                val end = start + word.length
                currentIndex = end + 1 // +1 pour l'espace

                val suggestions = viewModel.spellChecker?.suggest(word, 3)
                if (!suggestions.isNullOrEmpty() && suggestions[0] != word) {
                    modifications.add {
                        ssb.setSpan(
                            ForegroundColorSpan(Color.RED),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        ssb.setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                val layout = transcriptionText.layout
                                val line = layout.getLineForOffset(start)
                                val lineTop = layout.getLineTop(line)
                                showSuggestionsMenu(
                                    widget,
                                    lineTop,
                                    start,
                                    end,
                                    suggestions.toTypedArray()
                                )
                            }
                        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            modifications.forEach { it() }
            transcriptionText.text = ssb
            transcriptionText.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun stopTranscription() {
        // Annuler le travail de transcription
        Log.d("Whisper", "Annuler le travail de transcription")

        val workId = preferences?.getString("WorkRequestId", null)
        if (workId != null) {
            val workRequestId = UUID.fromString(workId)

            WorkManager.getInstance(requireContext()).cancelWorkById(workRequestId)
        }

        val tempFilePath = preferences?.getString("tempFilePath", "")
        val tempFile = tempFilePath?.let { File(it) }
        if (tempFile?.exists() == true) {
            tempFile.delete()
        }

        // Nettoyer la textview et les SharedPreferences
        requireActivity().contentResolver.delete(
            TranscriptionContentProvider.CONTENT_URI,
            null,
            null
        )
        viewModel.transcription.value = ""
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d("Whisper", "Appel on Destroy Main")
        requireActivity().contentResolver.unregisterContentObserver(contentObserver)

        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun replaceWord(start: Int, end: Int, replacement: String) {
        val editable: Editable = transcriptionText.editableText
        editable.replace(start, end, replacement)
    }

    private fun showSuggestionsMenu(
        view: View,
        lineTop: Int,
        start: Int,
        end: Int,
        suggestions: Array<String>
    ) {
        val listPopupWindow = ListPopupWindow(requireContext())
        listPopupWindow.anchorView = view
        listPopupWindow.width = 600
        listPopupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        listPopupWindow.verticalOffset =
            lineTop // Utilisation de lineTop pour le positionnement vertical
        listPopupWindow.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                suggestions
            )
        )

        listPopupWindow.setOnItemClickListener { _, _, position, _ ->
            replaceWord(start, end, suggestions[position])
            listPopupWindow.dismiss()
        }

        listPopupWindow.show()
    }
}