package com.pcl.chronoslock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.util.Locale

/** The four home-screen widgets (long-press your Android home screen > Widgets > PCL). */
object PclWidgets {
    const val ACTION = "com.pcl.chronoslock.WIDGET_TAP"

    enum class Kind(val provider: Class<*>, val glyph: String) {
        LOCK(LockWidget::class.java, "◎"),
        ALWAYS(AlwaysWidget::class.java, "◉"),
        MODE1(Mode1Widget::class.java, "◆"),
        MODE2(Mode2Widget::class.java, "★")
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (k in Kind.values()) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, k.provider))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(ctx, k))
        }
    }

    private fun build(ctx: Context, kind: Kind): RemoteViews {
        val e = ChronosEngine
        val label: String; val active: Boolean; val sub: String; val bg: Int; val tint: Int
        when (kind) {
            Kind.LOCK -> { label = "CHRONOS LOCK"; active = e.locked.value; sub = if (active) "ACTIVE" else "TAP TO ACTIVATE"; bg = R.drawable.widget_bg_green; tint = 0xFF38F5A8.toInt() }
            Kind.ALWAYS -> { label = "ALWAYS ON"; active = e.alwaysOn.value; sub = if (active) "MIC ON" else "MIC OFF"; bg = R.drawable.widget_bg_cyan; tint = 0xFF22E4FF.toInt() }
            Kind.MODE1 -> { label = Names.combat.uppercase(Locale.US); active = e.combat.value; sub = if (active) "ACTIVE" else "OFF"; bg = R.drawable.widget_bg_red; tint = 0xFFFF4D5E.toInt() }
            Kind.MODE2 -> { label = Names.quantum.uppercase(Locale.US); active = e.quantum.value; sub = if (active) "ACTIVE" else "OFF"; bg = R.drawable.widget_bg_violet; tint = 0xFFA78BFA.toInt() }
        }
        val rv = RemoteViews(ctx.packageName, R.layout.widget_tile)
        rv.setTextViewText(R.id.w_glyph, kind.glyph)
        rv.setTextViewText(R.id.w_label, label)
        rv.setTextViewText(R.id.w_state, sub)
        rv.setInt(R.id.w_root, "setBackgroundResource", if (active) bg else R.drawable.widget_bg_off)
        rv.setTextColor(R.id.w_glyph, if (active) tint else Color.parseColor("#8A97B5"))
        rv.setTextColor(R.id.w_state, if (active) tint else Color.parseColor("#8A97B5"))
        val tap = Intent(ctx, WidgetActionReceiver::class.java).setAction(ACTION).putExtra("kind", kind.name)
        rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getBroadcast(ctx, kind.ordinal, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return rv
    }
}

abstract class PclWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ChronosEngine.init(context)
        PclWidgets.updateAll(context)
    }
}
class LockWidget : PclWidgetProvider()
class AlwaysWidget : PclWidgetProvider()
class Mode1Widget : PclWidgetProvider()
class Mode2Widget : PclWidgetProvider()

/** Handles a tap on any PCL widget by doing exactly what the matching Home button does. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PclWidgets.ACTION) return
        ChronosEngine.init(context)
        runCatching { startChronosService(context) }          // keeps the mic alive; a widget tap is allowed to do this
        when (intent.getStringExtra("kind")) {
            "LOCK" -> ChronosEngine.toggleLock()
            "ALWAYS" -> ChronosEngine.setAlwaysOn(!ChronosEngine.alwaysOn.value)
            "MODE1" -> ChronosEngine.toggleCombat()
            "MODE2" -> ChronosEngine.toggleQuantum()
        }
        PclWidgets.updateAll(context)
    }
}
