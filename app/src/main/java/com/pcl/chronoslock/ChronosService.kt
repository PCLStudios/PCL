package com.pcl.chronoslock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Keeps the process alive so the wake word works with the screen off. */
class ChronosService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Chronos Lock", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("PCL active")
            .setContentText("Say \"Chronos Lock\"")
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, notification)
        }
        ChronosEngine.init(this)
        ChronosEngine.start()
        return START_STICKY
    }

    override fun onDestroy() {
        ChronosEngine.stop()
        super.onDestroy()
    }

    private companion object { const val CHANNEL = "pcl_lock" }
}
