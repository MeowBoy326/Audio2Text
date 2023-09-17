package com.example.audio2text

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
//import com.google.android.gms.ads.MobileAds
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.io.IOException
import java.net.SocketException

class MyApplication : Application() {

    companion object {
        const val CHANNEL_ID = "transcription_channel"
        const val CHANNEL_ID2 = "download_channel"
        const val CHANNEL_ID3 = "load_dictionary_channel"
    }

    private val viewModelStore = ViewModelStore()

    override fun onCreate() {
        super.onCreate()

        // Create the NotificationChannel
        val name = getString(R.string.channel_name)
        val name2 = getString(R.string.channel_name2)
        val name3= getString(R.string.channel_name3)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = createNotificationChannel(applicationContext, CHANNEL_ID, name, importance)
            /*NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }*/
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val channel2 = createNotificationChannel(applicationContext, CHANNEL_ID2, name2, importance)
            /*NotificationChannel(CHANNEL_ID2, "Téléchargements", NotificationManager.IMPORTANCE_LOW)*/

        val channel3 = createNotificationChannel(applicationContext, CHANNEL_ID3, name3, importance)
            /*NotificationChannel(CHANNEL_ID3, "Chargement du correcteur", NotificationManager.IMPORTANCE_LOW)*/
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(channel2)
        notificationManager.createNotificationChannel(channel3)

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        HomeViewModelHolder.viewModel = ViewModelProvider(viewModelStore, factory)[HomeViewModel::class.java]
        DictionaryViewModelHolder.viewModel =
            ViewModelProvider(viewModelStore, factory)[DictionaryViewModel::class.java]
        DownloadViewModelHolder.viewModel =
            ViewModelProvider(viewModelStore, factory)[DownloadViewModel::class.java]

        val sharedPreferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE)
        sharedPreferences.registerOnSharedPreferenceChangeListener(PreferenceChangeListenerUtil.preferenceChangeListener)
    }
}