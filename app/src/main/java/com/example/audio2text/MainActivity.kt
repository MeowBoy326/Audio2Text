package com.example.audio2text

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private lateinit var selectAudioFileLauncher: ActivityResultLauncher<Intent>
    private val bufferSize = 1024 * 4
    private lateinit var myProgressBarConversion: ProgressBar
    private lateinit var myProgressBarTranscription: ProgressBar
    private lateinit var transcriptionText: EditText
    private lateinit var header: TextView
    private lateinit var selectFileButton: FloatingActionButton
    val CHANNEL_ID = "transcription_channel"
    private val ACTION_UPDATE_PROGRESS = "com.example.action.UPDATE_PROGRESS"
    private lateinit var stopTranscriptionButton: FloatingActionButton
    // Global variable to keep track of the work request
    private lateinit var workRequest: OneTimeWorkRequest
    private val NOTIFICATION_ID_FINISHED = 2
    val TRANSCRIPTION_FINISHED_CHANNEL_ID = "transcription_finished_channel"

    private lateinit var notificationManager : NotificationManager
    private lateinit var tempFile: File

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    companion object {
        var modelPath = ""
    }

    /*private val progressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val progress = intent?.getIntExtra("progress", 0) ?: 0
            Log.d(TAG,"Progress is : ${progress}")
            if (progress == 100) {
                Log.d(TAG, "Reach end of transcription")
                stopTranscriptionButton.visibility = View.GONE
                selectFileButton.visibility = View.VISIBLE
            }
        }
    }*/

    val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            val cursor = contentResolver.query(TranscriptionContentProvider.CONTENT_URI, null, null, null, null)
            cursor?.moveToFirst()
            val columnIndex = cursor?.getColumnIndex("transcription") ?: -1
            val transcription = if (columnIndex != -1) {
                cursor?.getString(columnIndex)
            } else {
                ""
            }
            cursor?.close()

            transcriptionText.setText(transcription)
        }
    }

    /*private val transcriptionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val transcription = intent.getStringExtra("transcription")

            // Update the transcription text view with the updated transcription
            transcriptionText.text = transcription

            // Make the transcription text view visible
            transcriptionText.visibility = View.VISIBLE

            stopTranscriptionButton.visibility = View.VISIBLE
            selectFileButton.visibility = View.INVISIBLE
        }
    }*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //val sharedPref = applicationContext.getSharedPreferences("transcription", Context.MODE_PRIVATE)
        /*with(sharedPref.edit()) {
            clear()
            apply()
        }*/
        val preferences = getSharedPreferences("MyPreferences", MODE_PRIVATE)
        val isFirstLaunch = preferences.getBoolean("isFirstLaunch", true)
        if (isFirstLaunch) {
            // Mettre à jour la préférence pour indiquer que l'application a été lancée
            preferences.edit().putBoolean("isFirstLaunch", false).apply()
            // Rediriger vers TutorialActivity
            val intent = Intent(this, TutorialActivity::class.java)
            startActivity(intent)
            finish()
        }
        contentResolver.delete(TranscriptionContentProvider.CONTENT_URI, null, null)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Configurer le tiroir
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Gérer les clics sur les éléments de la barre latérale
        navView.setNavigationItemSelectedListener { item ->
            // Handle navigation view item clicks here.
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        stopTranscriptionButton = findViewById(R.id.stop_transcription_fab)
        stopTranscriptionButton.setOnClickListener {
            stopTranscriptionButton.isEnabled = false
            stopTranscription()
        }
        transcriptionText = findViewById(R.id.resultEditText)
        header = findViewById(R.id.header)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Set up the ActivityResultLauncher
        selectAudioFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                transcriptionText.visibility = View.VISIBLE
                stopTranscriptionButton.visibility = View.VISIBLE
                selectFileButton.visibility = View.GONE
                val selectedUri: Uri = result.data!!.data!!

                // Lancez une coroutine pour exécuter le code bloquant
                lifecycleScope.launch(Dispatchers.IO) {
                    // Ouvrez un flux d'entrée à partir de l'URI
                    val inputStream = contentResolver.openInputStream(selectedUri)

                    // Créez un fichier temporaire dans le répertoire cache de votre application
                    tempFile = File.createTempFile("media", null, cacheDir)

                    // Copiez le contenu du flux d'entrée dans le fichier temporaire
                    inputStream?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Utilisez le chemin du fichier temporaire pour vos besoins
                    val tempFilePath = tempFile.absolutePath

                    // Revenez au thread principal pour mettre à jour l'interface utilisateur
                    withContext(Dispatchers.Main) {
                        // Démarrez la transcription
                        startTranscription(tempFilePath)
                    }
                }
            }
        }

        // Add a click listener to the select file button
        selectFileButton = findViewById(R.id.select_file_fab)
        selectFileButton.setOnClickListener {
            // Prompt the user to select an audio file
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
            }
            // Make the select file button invisible
            header.visibility = View.GONE
            selectAudioFileLauncher.launch(intent)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val viewRect = Rect()
        navView.getGlobalVisibleRect(viewRect)

        if (!viewRect.contains(ev.rawX.toInt(), ev.rawY.toInt()) && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun startTranscription(inputFilePath: String) {
        workRequest = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(workDataOf("audioFilePath" to inputFilePath))
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)

        TranscriptionWorker.whisperContext?.inferenceStoppedListener = object : InferenceStoppedListener {
            override fun onInferenceStopped() {
                // Réactivez le bouton ici
                selectFileButton.isEnabled = true
            }
        }

        // Observe work status
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.id)
            .observe(this, Observer { workInfo ->
                if (workInfo != null) {
                    // Update UI with progress here
                    if (workInfo.state == WorkInfo.State.CANCELLED) {
                        // Handle cancellation here
                        Log.d("Whisper","Job was cancelled")
                        stopTranscriptionButton.visibility = View.GONE
                        selectFileButton.isEnabled = false
                        selectFileButton.visibility = View.VISIBLE
                        lifecycleScope.launch(Dispatchers.Default) {
                            TranscriptionWorker.releaseResources(applicationContext, true)
                        }

                        Toast.makeText(this, "La transcription a été arrêtée", Toast.LENGTH_SHORT).show()
                    }

                    if (workInfo.state == WorkInfo.State.BLOCKED) {
                        // Handle cancellation here
                        Log.d("Whisper","Job is blocked")
                    }

                    if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                        // Handle finished state
                        Log.d(TAG, "Reach end of transcription")
                        transcriptionText.isEnabled = true
                        stopTranscriptionButton.visibility = View.GONE
                        selectFileButton.visibility = View.VISIBLE

                        Log.d("END", "code reached at the end")
                        val intent = Intent(applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            applicationContext,
                            0,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE
                        )

                        // Logic to show "Transcription finished" notification
                        val finishedNotification =
                            NotificationCompat.Builder(applicationContext, TRANSCRIPTION_FINISHED_CHANNEL_ID)
                                .setContentTitle("Transcription terminée")
                                .setSmallIcon(R.drawable.notification_icon)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setContentIntent(pendingIntent) // Définissez l'intention pendante ici
                                .setAutoCancel(true) // Ferme automatiquement la notification après le clic
                                .build()

                        notificationManager.notify(NOTIFICATION_ID_FINISHED, finishedNotification)
                    }
                }
            })
    }

    private fun stopTranscription() {
        // Annuler le travail de transcription
        if (::workRequest.isInitialized) {
            WorkManager.getInstance(this).cancelWorkById(workRequest.id)
        }
        // Supprimez le fichier temporaire
        if (::tempFile.isInitialized && tempFile.exists()) {
            tempFile.delete()
        }
        // Nettoyer la textview et les SharedPreferences
        contentResolver.delete(TranscriptionContentProvider.CONTENT_URI, null, null)
        transcriptionText.setText("")
        /*val sharedPref = applicationContext.getSharedPreferences("transcription", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            clear()
            apply()
        }*/
        //header.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(TranscriptionContentProvider.CONTENT_URI, true, contentObserver)

        // Enregistrez le BroadcastReceiver avec l'IntentFilter
        // Register the broadcast receiver for transcription updates
        /*LocalBroadcastManager.getInstance(this).registerReceiver(
            transcriptionUpdateReceiver,
            IntentFilter("com.example.audio2text.TRANSCRIPTION_UPDATE")
        )*/
        //LocalBroadcastManager.getInstance(this).registerReceiver(progressReceiver, IntentFilter(ACTION_UPDATE_PROGRESS))
        //val sharedPref = applicationContext.getSharedPreferences("transcription", Context.MODE_PRIVATE)
        //val transcription = sharedPref.getString("transcription", "")
        val cursor = contentResolver.query(TranscriptionContentProvider.CONTENT_URI, null, null, null, null)
        val transcription = if (cursor?.moveToFirst() == true) {
            val columnIndex = cursor.getColumnIndex("transcription")
            cursor.getString(columnIndex)
        } else {
            "" // ou une valeur par défaut si vous préférez
        }

        cursor?.close()
        if (transcription != null) {
            Log.d("transcription", transcription)
        } else {
            Log.d("transcription", "Transcription is null")
        }
        transcriptionText.setText(transcription)

        // Make the transcription text view visible
        transcriptionText.visibility = View.VISIBLE

    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(contentObserver)
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
        // Désenregistrez le BroadcastReceiver
       // LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver)
        //LocalBroadcastManager.getInstance(this).unregisterReceiver(transcriptionUpdateReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Whisper", "Appel on Destroy Main")
        //val intent = Intent(this, TranscriptionService::class.java)
        //stopService(intent)
        stopTranscription()
    }

    fun getPathFromUri(context: Context, uri: Uri): String? {
        // Check if the Uri is a content Uri, which is the case if it comes from the file picker.
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                if ("primary".equals(type, ignoreCase = true)) {
                    return context.getExternalFilesDir(null).toString() + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)

                if (id.startsWith("raw:")) {
                    return id.removePrefix("raw:")
                }

                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), id.toLong()
                )

                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]

                val contentUri = when (type) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // If it's a content Uri, we can use the ContentResolver to query it.
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            // If it's a file Uri, we can just get the path part.
            return uri.path
        }

        return null
    }

    fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        context.contentResolver.query(uri!!, null, selection, selectionArgs, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return it.getString(index)
            }
        }
        return null
    }

    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
}
