package app.folio.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray

class FolioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateOne(context, manager, id) }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, FolioWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.folio_widget)
            val raw = context.getSharedPreferences("folio_local", Context.MODE_PRIVATE)
                .getString("notes", "[]") ?: "[]"
            val notes = runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    if (item.optBoolean("deleted") || item.optBoolean("archived")) null
                    else Triple(item.optString("title", "Untitled Note"), item.optString("body", ""), item.optLong("updatedAt"))
                }.sortedByDescending { it.third }
            }.getOrDefault(emptyList())
            val latest = notes.firstOrNull()
            views.setTextViewText(R.id.widget_count, "${notes.size} notes in your library")
            views.setTextViewText(R.id.widget_title, latest?.first?.ifBlank { "Untitled Note" } ?: "Your notes are ready")
            views.setTextViewText(R.id.widget_body, latest?.second?.lineSequence()?.firstOrNull()?.take(100) ?: "Tap to open Folio")
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, views)
        }
    }
}