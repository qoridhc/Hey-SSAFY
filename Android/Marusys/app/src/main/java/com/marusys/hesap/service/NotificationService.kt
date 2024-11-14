package com.marusys.hesap.service

import android.app.Service

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.marusys.hesap.R

class NotificationService : Service() {
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "BackgroundServiceChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Background Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("앱 실행 중")
            .setContentText("앱이 백그라운드에서 실행 중입니다.")
            .setSmallIcon(R.drawable.marusys_icon)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}