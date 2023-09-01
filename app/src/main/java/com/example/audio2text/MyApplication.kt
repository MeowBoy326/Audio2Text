package com.example.audio2text

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import java.util.Locale

class MyApplication : Application() {

    companion object {
        const val CHANNEL_ID = "transcription_channel"
        const val CHANNEL_ID2 = "download_channel"
    }

    var chosenLang :String = Locale.getDefault().language

    override fun onCreate() {
        super.onCreate()

        // Create the NotificationChannel
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        val channel2 =
            NotificationChannel(CHANNEL_ID2, "Téléchargements", NotificationManager.IMPORTANCE_LOW)
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(channel2)
    }
}