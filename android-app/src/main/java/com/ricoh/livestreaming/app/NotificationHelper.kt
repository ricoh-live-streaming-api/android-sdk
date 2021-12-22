/*
 * Copyright 2021 Ricoh Company, Ltd. All rights reserved.
 */

package com.ricoh.livestreaming.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.Build

class NotificationHelper(context: Context) : ContextWrapper(context) {
    companion object {
        private const val CHANNEL_ID = "channel_id"
    }

    private val manager: NotificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                    CHANNEL_ID, "Notifications", NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    fun getNotification(title: String, body: String) : Notification {
        return notificationBuilder
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setSmallIcon(R.drawable.outline_android_white_48)
                .setContentTitle(title)
                .setContentText(body)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()
    }

    private val notificationBuilder: Notification.Builder
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
}
