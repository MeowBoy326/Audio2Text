package com.example.audio2text

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.KeyListener
import android.text.method.LinkMovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.gitlab.rxp90.jsymspell.TokenWithContext
import io.gitlab.rxp90.jsymspell.api.SuggestItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList


class HomeFragment : Fragment() {

    //private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private lateinit var selectAudioFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var header: TextView
    val CHANNEL_ID = "transcription_channel"
    private lateinit var stopTranscriptionButton: FloatingActionButton

    // Global variable to keep track of the work request
    private lateinit var workRequest: OneTimeWorkRequest
    private val NOTIFICATION_ID = 42

    private lateinit var notificationManager: NotificationManager
    private lateinit var tempFile: File
    private val NOT_A_LENGTH = -1
    private lateinit var preferences: SharedPreferences
    lateinit var selectFileButton: FloatingActionButton
    lateinit var transcriptionText: EditText
    private var lastClickedInfo: MisspelledWordInfo? = null
    lateinit var mAdView : AdView

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

        mAdView = view.findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

        mAdView.adListener = object: AdListener() {
            override fun onAdClicked() {
                // Code to be executed when the user clicks on an ad.
            }

            override fun onAdClosed() {
                // Code to be executed when the user is about to return
                // to the app after tapping on an ad.
            }

            override fun onAdFailedToLoad(adError : LoadAdError) {
                // Code to be executed when an ad request fails.
                Log.d("HomeFragment", "Ad failed to load with : ${adError.message}")
            }

            override fun onAdImpression() {
                // Code to be executed when an impression is recorded
                // for an ad.
            }

            override fun onAdLoaded() {
                // Code to be executed when an ad finishes loading.
                Log.d("HomeFragment", "Ad Loaded successfully")
            }

            override fun onAdOpened() {
                // Code to be executed when an ad opens an overlay that
                // covers the screen.
            }
        }

        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = requireContext().applicationContext.getSharedPreferences(
            "MyPreferences",
            Context.MODE_PRIVATE
        )
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transcriptionText.setOnTouchListener(object : View.OnTouchListener {
            private val gestureDetector = GestureDetector(
                requireContext(),
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (HomeViewModelHolder.viewModel.isEditingTranscriptionText.value == false && HomeViewModelHolder.viewModel.isFullySuccessfulLiveData.value == true) {
                            Log.d("HomeFragment", "onDoubleTap")
                            HomeViewModelHolder.viewModel.isEditingTranscriptionText.postValue(true)
                        }
                        return super.onDoubleTap(e)
                    }
                })

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(event)
                return false
            }
        })

        HomeViewModelHolder.viewModel.transcription.observe(
            viewLifecycleOwner,
            object : Observer<String?> {
                override fun onChanged(value: String?) {
                    Log.d("HomeFragment", "transcription: $value")
                    if (value != null) {
                        transcriptionText.setText(value)
                        transcriptionText.setSelection(transcriptionText.text.length)
                    }
                }
            })

        HomeViewModelHolder.viewModel.isSelectFileButtonEnabled.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    selectFileButton.isEnabled = value
                }
            })

        HomeViewModelHolder.viewModel.isTranscriptionTextVisible.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    transcriptionText.visibility = if (value) View.VISIBLE else View.GONE
                }
            })

        HomeViewModelHolder.viewModel.isStopTranscriptionButtonVisible.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    stopTranscriptionButton.visibility = if (value) View.VISIBLE else View.GONE
                }
            })

        HomeViewModelHolder.viewModel.isHeaderVisible.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    header.visibility = if (value) View.VISIBLE else View.GONE
                }
            })

        HomeViewModelHolder.viewModel.isSelectFileButtonVisible.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    selectFileButton.visibility = if (value == true) View.VISIBLE else View.GONE
                }
            })

        HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    transcriptionText.isEnabled = value
                }
            })

        HomeViewModelHolder.viewModel.isRunningLiveData.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    Log.d("isRunning state:", value.toString())
                    if (value) {
                        HomeViewModelHolder.viewModel.isSelectFileButtonVisible.postValue(false)
                        HomeViewModelHolder.viewModel.isStopTranscriptionButtonVisible.postValue(
                            true
                        )
                        HomeViewModelHolder.viewModel.isHeaderVisible.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isEditableTranscriptionText.postValue(false)
                        HomeViewModelHolder.viewModel.loadTranscription(requireContext().applicationContext)
                    }
                }
            })

        HomeViewModelHolder.viewModel.isGoingToStopLiveData.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    Log.d("HomeFragment", "isGoingToStop : $value")
                    if (value) {
                        HomeViewModelHolder.viewModel.isStopTranscriptionButtonVisible.postValue(
                            false
                        )
                        HomeViewModelHolder.viewModel.isSelectFileButtonVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isSelectFileButtonEnabled.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isEditableTranscriptionText.postValue(false)
                        HomeViewModelHolder.viewModel.loadTranscription(requireContext().applicationContext)
                    }
                }
            })

        HomeViewModelHolder.viewModel.isFullyStoppedLiveData.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    Log.d("HomeFragment", "isFullyStopped : $value")
                    if (value) {
                        HomeViewModelHolder.viewModel.isHeaderVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isTranscriptionTextVisible.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.postValue(false)
                        HomeViewModelHolder.viewModel.isStopTranscriptionButtonVisible.postValue(
                            false
                        )
                        HomeViewModelHolder.viewModel.isSelectFileButtonVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isSelectFileButtonEnabled.postValue(true)
                        HomeViewModelHolder.viewModel.isEditableTranscriptionText.postValue(false)
                        preferences.edit()?.putString("WorkRequestId", null)?.apply()
                    }
                }
            })

        HomeViewModelHolder.viewModel.isFullySuccessfulLiveData.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    if (value) {
                        //HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.postValue(true)
                        HomeViewModelHolder.viewModel.isStopTranscriptionButtonVisible.postValue(
                            false
                        )
                        HomeViewModelHolder.viewModel.isSelectFileButtonVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isEditableTranscriptionText.postValue(true)
                        HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.postValue(true)
                        HomeViewModelHolder.viewModel.isTranscriptionTextVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isHeaderVisible.postValue(false)
                        preferences.edit()?.putString("WorkRequestId", null)?.apply()
                        transcriptionText.setSelection(0)
                    }
                }
            })

        HomeViewModelHolder.viewModel.isInitializedLiveData.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    Log.d("HomeFragment", "isInitialized : $value")
                    if (value) {
                        HomeViewModelHolder.viewModel.isStopTranscriptionButtonVisible.postValue(
                            false
                        )
                        HomeViewModelHolder.viewModel.isSelectFileButtonVisible.postValue(true)
                        HomeViewModelHolder.viewModel.isSelectFileButtonEnabled.postValue(true)
                        HomeViewModelHolder.viewModel.isEditableTranscriptionText.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextEnabled.postValue(false)
                        HomeViewModelHolder.viewModel.isTranscriptionTextVisible.postValue(false)
                        HomeViewModelHolder.viewModel.isHeaderVisible.postValue(true)
                    }
                }
            })

        HomeViewModelHolder.viewModel.isEditingTranscriptionText.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    if (HomeViewModelHolder.viewModel.isEditableTranscriptionText.value == true) {
                        setEditTextState(transcriptionText, value)
                        HomeViewModelHolder.viewModel.isRequestSpellingSuggestions.postValue(!value)
                    }
                }
            })

        HomeViewModelHolder.viewModel.isRequestSpellingSuggestions.observe(
            viewLifecycleOwner,
            object : Observer<Boolean> {
                override fun onChanged(value: Boolean) {
                    if (HomeViewModelHolder.viewModel.isEditableTranscriptionText.value == true) {
                        if (value && HomeViewModelHolder.viewModel.isSpellAlreadyRequested.value == true) {
                            applySpellingMarks()
                        } else {
                            removeSpellingMarks()
                        }
                    }
                }
            })

        var totalOffset = "Résultat de la transcription :\n\n".length  // Pour le premier paragraphe
        var previousParagraph = ""

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

        val workId = preferences.getString("WorkRequestId", null)?.let {
            UUID.fromString(it)
        }

        // Récupérez l'URI depuis les arguments du Fragment
        val selectedUri = arguments?.getString("fileUri")?.let { Uri.parse(it) }
        Log.d("Uri", selectedUri.toString())
        if (selectedUri != null && !HomeViewModelHolder.viewModel.isRunningLiveData.value!! && !HomeViewModelHolder.viewModel.isGoingToStopLiveData.value!!) {
            lifecycleScope.launch(Dispatchers.IO) {
                val inputStream = requireActivity().contentResolver.openInputStream(selectedUri)
                val tempFile = File.createTempFile("media", null, requireActivity().cacheDir)
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val tempFilePath = tempFile.absolutePath
                preferences.edit()?.putString("tempFilePath", tempFilePath)?.apply()

                withContext(Dispatchers.Main) {
                    startTranscription(tempFilePath)
                }
            }
        }

        Log.d("WorkId", workId.toString())

        if (workId != null) {
            observeWorkStatus(workId)
        }

        transcriptionText.viewTreeObserver.addOnScrollChangedListener {
            lastClickedInfo?.let { info ->
                val (newX, newY) = calculateLineTop(info.start, transcriptionText)

                // Update the position of the popup
                currentListPopupWindow?.apply {
                    horizontalOffset = newX
                    verticalOffset = newY
                    // Vous pourriez avoir besoin d'appeler `show()` pour forcer la mise à jour de la position.
                    if (isShowing) {
                        dismiss()
                        show()
                    }
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,  // Le propriétaire du cycle de vie
            object :
                OnBackPressedCallback(true) { // `true` signifie que le callback est activé par défaut
                override fun handleOnBackPressed() {
                    if (HomeViewModelHolder.viewModel.isEditableTranscriptionText.value == true && HomeViewModelHolder.viewModel.isFullySuccessfulLiveData.value == true) {
                        HomeViewModelHolder.viewModel.isEditingTranscriptionText.postValue(
                            false
                        )
                    }
                }
            }
        )
    }

    private fun startTranscription(inputFilePath: String) {
        val workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf("audioFilePath" to inputFilePath))
            .build()

        preferences.edit()?.putString("WorkRequestId", workRequest.id.toString())?.apply()

        WorkManager.getInstance(requireContext().applicationContext).enqueue(workRequest)

        preferences.edit().putBoolean("isInitialized", false).apply()
        preferences.edit().putBoolean("isFullyStopped", false).apply()
        preferences.edit().putBoolean("isFullySuccessful", false).apply()
        preferences.edit().putBoolean("isRunning", true).apply()

        SpellCheckerSingleton.isSpellCheckerReady.postValue(false)
        HomeViewModelHolder.viewModel.isSpellAlreadyRequested.postValue(false)

        observeWorkStatus(workRequest.id)
    }

    private fun setEditTextState(editText: EditText, enabled: Boolean) {
        editText.apply {
            isFocusable = enabled
            isFocusableInTouchMode = enabled
            isEnabled = true  // Gardez cela toujours à 'true'
            isCursorVisible = enabled
            keyListener = if (enabled) EditText(this.context).keyListener else null
            movementMethod = if (enabled) ScrollingMovementMethod() else LinkMovementMethod()
        }
    }

    private fun observeWorkStatus(workId: UUID) {
        WorkManager.getInstance(requireContext().applicationContext).getWorkInfoByIdLiveData(workId)
            .observe(requireActivity(), object : Observer<WorkInfo> {
                override fun onChanged(value: WorkInfo) {
                    Log.d("ENTERREEDDD:::", value.toString())
                    if (value.state.isFinished) {
                        // Update UI with progress here
                        if (value.state == WorkInfo.State.CANCELLED) {
                            // Handle cancellation here
                            Log.d("Whisper", "Job was cancelled")

                            //notificationManager.cancel(NOTIFICATION_ID)
                        }

                        if (value.state == WorkInfo.State.FAILED) {
                            stopTranscription()
                        }

                        if (value.state == WorkInfo.State.BLOCKED) {
                            // Handle cancellation here
                            Log.d("Whisper", "Job is blocked")
                        }

                        if (value.state == WorkInfo.State.SUCCEEDED) {
                            // Handle finished state
                            Log.d(ContentValues.TAG, "Reach end of transcription")

                            CoroutineScope(Dispatchers.IO).launch {
                                TranscriptionWorker.releaseResources()
                            }

                            preferences.edit()?.putBoolean("isRunning", false)?.apply()
                            preferences.edit()?.putBoolean("isFullySuccessful", true)?.apply()

                            //viewModel.isTranscriptionTextEnabled.value = true
                            //viewModel.isStopTranscriptionButtonVisible.value = false
                            //viewModel.isSelectFileButtonVisible.value = true
                            //HomeViewModelHolder.viewModel.isEditableTranscriptionText.postValue(false)
                        }
                    }
                }
            })
    }

    var spellingModifications: List<() -> Unit> = emptyList()
    val lastIndices = HashMap<String, Int>()

    fun calculateLineTop(start: Int, editText: EditText): Pair<Int, Int> {
        val layout = editText.layout

        // Vérification que 'start' est dans les limites du texte de l'EditText
        if (start >= editText.length()) {
            return Pair(0, 0)  // Retourner des valeurs par défaut si 'start' est hors limites
        }

        val line = layout.getLineForOffset(start)
        val baseline = layout.getLineBaseline(line)
        val ascent = layout.getLineAscent(line)

        val scrollY = editText.scrollY
        val widgetHeight = editText.height

        // Calculate y position considering visible window
        val y = baseline + ascent - scrollY

        // If calculated y is outside of the visible window, set it to the top or bottom of the widget
        val visibleY = when {
            y < 0 -> 0
            y > widgetHeight -> widgetHeight
            else -> y
        }

        val x = layout.getPrimaryHorizontal(start)
            .toInt() + editText.scrollX  // take into account the scrolled position

        val xOffset = 20  // Décalage en pixels sur l'axe des x
        val yOffset = 20  // Décalage en pixels sur l'axe des y

        return Pair(x + xOffset, visibleY + yOffset)
    }

    private var currentListPopupWindow: ListPopupWindow? = null

    fun applySpellingMarks() {
        val ssb = SpannableStringBuilder(transcriptionText.text)

        for (info in HomeViewModelHolder.viewModel.misspelledWords) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    val (x, y) = calculateLineTop(info.start, transcriptionText)
                    showSuggestionsMenu(
                        widget,
                        Pair(x, y),
                        info.start,
                        info.end,
                        info.suggestions.toTypedArray()
                    )
                    lastClickedInfo = info  // Sauvegarder la dernière information sur le mot cliqué
                }
            }

            Log.d("HomeFragment","start is ${info.start} at ${info.suggestions.get(0)}")

            ssb.setSpan(
                clickableSpan,
                info.start,
                info.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            ssb.setSpan(
                ForegroundColorSpan(
                    ContextCompat.getColor(
                        requireContext().applicationContext,
                        R.color.colorTypo
                    )
                ),
                info.start,
                info.end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Utilisation du thread principal de l'UI pour les modifications de l'UI
        activity?.runOnUiThread {
            transcriptionText.text = ssb
            transcriptionText.movementMethod = LinkMovementMethod()
        }
    }

    fun removeSpellingMarks() {
        val ssb = SpannableStringBuilder(transcriptionText.text)

        // Étape 1: Supprimer les spans
        val clickableSpans = ssb.getSpans(0, ssb.length, ClickableSpan::class.java)
        val colorSpans = ssb.getSpans(0, ssb.length, ForegroundColorSpan::class.java)

        for (span in clickableSpans) {
            ssb.removeSpan(span)
        }

        for (span in colorSpans) {
            ssb.removeSpan(span)
        }

        // Étape 2: Réinitialiser le texte
        activity?.runOnUiThread {
            transcriptionText.text = ssb

            // Étape 3: Réinitialiser la méthode de mouvement
            transcriptionText.movementMethod =
                ScrollingMovementMethod()  // ou une autre méthode de mouvement par défaut
        }
    }

    private fun stopTranscription() {
        // Annuler le travail de transcription
        /*Log.d("Whisper", "Annuler le travail de transcription")

        val workId = preferences.getString("WorkRequestId", null)
        if (workId != null) {
            val workRequestId = UUID.fromString(workId)

            WorkManager.getInstance(requireContext().applicationContext)
                .cancelWorkById(workRequestId)
        }*/
        val stopTranscriptionIntent =
            Intent(context, StopTranscriptionReceiver::class.java).apply {
                action = "STOP_TRANSCRIPTION"
                //putExtra("WORK_ID", workRequestId.toString())
            }

        requireContext().applicationContext.sendBroadcast(stopTranscriptionIntent)
    }

    private fun replaceWord(start: Int, end: Int, newWord: String) {
        val ssb = SpannableStringBuilder(transcriptionText.text)
        ssb.replace(start, end, newWord)
        transcriptionText.text = ssb

        // Calculer le décalage induit par le remplacement du mot
        val offset = newWord.length - (end - start)

        // Variable pour stocker l'info à supprimer
        var infoToRemove: MisspelledWordInfo? = null

        // Mettre à jour les indices des mots mal orthographiés
        for (info in HomeViewModelHolder.viewModel.misspelledWords) {
            if (info.start == start && info.end == end) {
                // Mettre à jour les indices du mot remplacé
                info.end = start + newWord.length

                // Marquer ce mot pour suppression car il est maintenant correct
                infoToRemove = info
            } else if (info.start > end) {
                // Mettre à jour les indices des mots suivants
                info.start += offset
                info.end += offset
            }
        }

        // Supprimer le mot corrigé de la liste des mots mal orthographiés
        if (infoToRemove != null) {
            HomeViewModelHolder.viewModel.misspelledWords.remove(infoToRemove)
        }
    }

    private fun showSuggestionsMenu(
        view: View,
        position: Pair<Int, Int>,
        start: Int,
        end: Int,
        suggestions: Array<String>
    ) {
        // Fermeture de la popup existante
        currentListPopupWindow?.dismiss()

        val (x, y) = position
        val listPopupWindow = ListPopupWindow(view.context)
        listPopupWindow.anchorView = view
        listPopupWindow.width = 500
        listPopupWindow.height = WindowManager.LayoutParams.WRAP_CONTENT
        listPopupWindow.horizontalOffset = x
        listPopupWindow.verticalOffset = y

        listPopupWindow.setAdapter(
            ArrayAdapter(
                view.context,
                android.R.layout.simple_list_item_1,
                suggestions
            )
        )

        listPopupWindow.setOnItemClickListener { _, _, newPosition, _ ->
            removeStylingFromWord(start, end)
            replaceWord(start, end, suggestions[newPosition])
            applySpellingMarks()
            listPopupWindow.dismiss()
        }

        listPopupWindow.show()

        // Sauvegarde de la référence à la ListPopupWindow actuelle
        currentListPopupWindow = listPopupWindow
    }

    private fun removeStylingFromWord(start: Int, end: Int) {
        val ssb = SpannableStringBuilder(transcriptionText.text)
        val toRemove = ssb.getSpans(start, end, Object::class.java)

        for (span in toRemove) {
            ssb.removeSpan(span)
        }

        transcriptionText.text = ssb
    }
}