@file:JvmName("NotificationUtils")
package com.example.audio2text

/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import java.util.UUID

/**
 * Create the notification and required channel (O+) for running work in a foreground service.
 */
fun createNotification(
    context: Context,
    notificationTitle: String,
    showProgress: Boolean = false,
    progress: Int = 0
): Notification {
    val channelId = MyApplication.CHANNEL_ID
    val cancelText = context.getString(R.string.cancel_processing)
    val name = context.getString(R.string.channel_name)
    val stopTranscriptionIntent =
        Intent(context, StopTranscriptionReceiver::class.java).apply {
            action = "STOP_TRANSCRIPTION"
            //putExtra("WORK_ID", workRequestId.toString())
        }

    val stopTranscriptionPendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        stopTranscriptionIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    //val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(workRequestId, stopTranscriptionIntent)

    val builder = NotificationCompat.Builder(context, channelId)
        .setContentTitle(notificationTitle)
        .setTicker(notificationTitle)
        .setSmallIcon(R.drawable.notification_icon)
        .setOngoing(true)
        .addAction(android.R.drawable.ic_delete, cancelText, stopTranscriptionPendingIntent)

    if (showProgress) {
        builder.setProgress(100, progress, false)
    }

    return builder.build()
}

/**
 * Create the required notification channel for O+ devices.
 */
fun createNotificationChannel(
    context: Context,
    channelId: String,
    name: String,
    notificationImportance: Int = NotificationManager.IMPORTANCE_HIGH
): NotificationChannel {
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return NotificationChannel(
        channelId, name, notificationImportance
    ).also { channel ->
        notificationManager.createNotificationChannel(channel)
    }
}