package com.pcl.chronoslock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Opens other apps even when PCL is not on screen.
 * Android normally blocks that. Three ways around it, tried in this order:
 *  1. PCL is on screen: just start it.
 *  2. "Display over other apps" is allowed: show a 1-pixel invisible overlay for a moment, which Android treats as a
 *     visible window and therefore lets the launch through (this works on Android 10 to 15).
 *  3. Otherwise post a notification: tapping it opens the app (also tries a full-screen intent where Android allows it).
 */
object Launcher {
    enum class Result { OPENED, NOTIFIED, FAILED }

    private const val CHANNEL = "pcl_actions"
    private val main = Handler(Looper.getMainLooper())
    private var nid = 4000

    fun launch(ctx: Context, intent: Intent, label: String): Result {
        val app = ctx.applicationContext
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ChronosEngine.appVisible) {
            return try { app.startActivity(intent); Result.OPENED } catch (e: Exception) { Result.FAILED }
        }
        if (Settings.canDrawOverlays(app)) {
            var view: View? = null
            val wm = app.getSystemService(WindowManager::class.java)
            try {
                val v = View(app)
                val lp = WindowManager.LayoutParams(
                    1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.START
                wm.addView(v, lp)
                view = v
                app.startActivity(intent)
                return Result.OPENED
            } catch (e: Exception) {
                // fall through to the notification
            } finally {
                val v = view
                if (v != null) main.postDelayed({ runCatching { wm.removeView(v) } }, 3000)
            }
        }
        return if (notify(app, intent, label)) Result.NOTIFIED else Result.FAILED
    }

    private fun notify(ctx: Context, intent: Intent, label: String): Boolean = try {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "PCL actions", NotificationManager.IMPORTANCE_HIGH))
        val id = nid++
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("PCL: tap to continue")
            .setContentText(label.ifBlank { "Your program wants to open an app" } + ". Allow display over other apps in PCL > System to skip this tap.")
            .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).build()
        nm.notify(id, n)
        true
    } catch (e: Exception) { false }

    fun canOpenInBackground(ctx: Context) = Settings.canDrawOverlays(ctx)
}
