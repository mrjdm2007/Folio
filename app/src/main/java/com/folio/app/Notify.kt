package com.folio.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Prefs { fun p(ctx: Context): SharedPreferences = ctx.getSharedPreferences("folio", Context.MODE_PRIVATE) }

/** Sessions finished while the app was closed are parked here, then merged into the notebook on next launch. */
object TimerPrefs {
    private const val SEP = "\u0001"
    @Synchronized fun markLogged(ctx: Context, end: Long): Boolean {
        val sp = Prefs.p(ctx); val set = sp.getStringSet("logged", emptySet())!!.toMutableSet()
        val added = set.add(end.toString())
        if (added) sp.edit().putStringSet("logged", set.sortedBy { it.toLongOrNull() ?: 0L }.takeLast(30).toSet()).commit()
        return added
    }
    @Synchronized fun addPending(ctx: Context, end: Long, subject: String, minutes: Int) {
        val sp = Prefs.p(ctx); val set = sp.getStringSet("pending", emptySet())!!.toMutableSet()
        set.add(listOf(end.toString(), subject.replace(SEP, " "), minutes.toString()).joinToString(SEP))
        sp.edit().putStringSet("pending", set).commit()
    }
    @Synchronized fun takePending(ctx: Context): List<Session> {
        val sp = Prefs.p(ctx); val set = sp.getStringSet("pending", emptySet())!!
        val out = set.mapNotNull { s ->
            val p = s.split(SEP)
            val t = p.getOrNull(0)?.toLongOrNull()
            if (p.size == 3 && t != null) Session(t, p[1], p[2].toIntOrNull() ?: 0) else null
        }
        if (set.isNotEmpty()) sp.edit().remove("pending").commit()
        return out
    }
}

object Notifier {
    const val CH_TIMER = "timer"; const val CH_DONE = "timer_done"; const val CH_STUDY = "study"
    const val ID_TIMER = 11; const val ID_DONE = 12; const val ID_STUDY = 13

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Focus timer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Timer finished", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        })
        nm.createNotificationChannel(NotificationChannel(CH_STUDY, "Study reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, id: Int, b: NotificationCompat.Builder) { if (canPost(ctx)) NotificationManagerCompat.from(ctx).notify(id, b.build()) }

    fun timerRunning(ctx: Context, end: Long, subject: String, minutes: Int) {
        channels(ctx)
        post(ctx, ID_TIMER, NotificationCompat.Builder(ctx, CH_TIMER).setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle(subject.ifBlank { "Focus session" }).setContentText("$minutes minute session")
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(end).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS).setContentIntent(openApp(ctx)))
    }
    fun timerPaused(ctx: Context, leftSec: Int, subject: String) {
        channels(ctx)
        post(ctx, ID_TIMER, NotificationCompat.Builder(ctx, CH_TIMER).setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle(subject.ifBlank { "Focus session" })
            .setContentText(String.format(java.util.Locale.US, "Paused · %02d:%02d left", leftSec / 60, leftSec % 60))
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(openApp(ctx)))
    }
    fun clearTimer(ctx: Context) { NotificationManagerCompat.from(ctx).cancel(ID_TIMER) }
    fun timerDone(ctx: Context, minutes: Int, subject: String) {
        channels(ctx); clearTimer(ctx)
        post(ctx, ID_DONE, NotificationCompat.Builder(ctx, CH_DONE).setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle("Focus session complete")
            .setContentText("$minutes min of ${subject.ifBlank { "study" }}. Take a short break.")
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).setContentIntent(openApp(ctx)))
    }
    fun study(ctx: Context, title: String, body: String, big: String) {
        channels(ctx)
        post(ctx, ID_STUDY, NotificationCompat.Builder(ctx, CH_STUDY).setSmallIcon(R.drawable.ic_stat_folio)
            .setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setAutoCancel(true).setContentIntent(openApp(ctx)))
    }

    private fun endIntent(ctx: Context, end: Long, subject: String, minutes: Int): PendingIntent = PendingIntent.getBroadcast(ctx, 77,
        Intent(ctx, TimerReceiver::class.java).putExtra("end", end).putExtra("subj", subject).putExtra("min", minutes),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    /** Exact system alarm: fires even if the app has been closed. */
    fun scheduleEnd(ctx: Context, end: Long, subject: String, minutes: Int) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(end, openApp(ctx)), endIntent(ctx, end, subject, minutes)) }
    }
    fun cancelEnd(ctx: Context) { runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(endIntent(ctx, 0, "", 0)) } }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val end = i.getLongExtra("end", 0L); val minutes = i.getIntExtra("min", 0); val subject = i.getStringExtra("subj") ?: ""
        if (TimerPrefs.markLogged(ctx, end)) TimerPrefs.addPending(ctx, end, subject, minutes)
        Notifier.timerDone(ctx, minutes, subject)
    }
}

object ReminderScheduler {
    fun apply(ctx: Context) {
        val sp = Prefs.p(ctx); val wm = WorkManager.getInstance(ctx)
        if (!sp.getBoolean("rem_on", false)) { wm.cancelUniqueWork("folio-reminders"); return }
        val hrs = sp.getInt("rem_hours", 2).toLong()
        wm.enqueueUniquePeriodicWork("folio-reminders", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(hrs, TimeUnit.HOURS).setInitialDelay(hrs, TimeUnit.HOURS).build())
    }
}

/** Builds one reminder from your own notebook: a due flashcard (answer underneath), an exam countdown, or a topic to revise. */
class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext; val sp = Prefs.p(ctx); val force = inputData.getBoolean("force", false)
        if (!force) {
            if (!sp.getBoolean("rem_on", false)) return Result.success()
            val h = LocalTime.now().hour; val qs = sp.getInt("quiet_start", 22); val qe = sp.getInt("quiet_end", 7)
            val quiet = if (qs > qe) (h >= qs || h < qe) else (h in qs until qe)
            if (quiet) return Result.success()
        }
        val f = File(ctx.filesDir, "folio.json")
        val d = if (f.exists()) runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<com.folio.app.Data>(f.readText()) }.getOrNull() else null
        val today = LocalDate.now().toEpochDay(); val key = "shown_$today"
        val shown = sp.getStringSet(key, emptySet())!!.toMutableSet()   // so the same topic is not repeated on the same day
        var title: String? = null; var body = ""; var big = ""
        if (d != null) {
            val cards = d.cards.filter { it.due <= System.currentTimeMillis() && it.id !in shown }
            val exam = d.exams.filter { it.date in today..(today + 7) && "exam${it.id}" !in shown }.minByOrNull { it.date }
            val topics = d.nodes.filter { !it.deleted && (it.kind == "topic" || it.kind == "page") && it.blocks.any { b -> b.text.isNotBlank() } && it.id !in shown }
            when {
                cards.isNotEmpty() -> { val c = cards.random(); title = "Recall: ${c.front}"; body = "Pull down to see the answer"; big = "Answer: ${c.back}"; shown += c.id }
                exam != null -> {
                    val dd = exam.date - today
                    title = if (dd == 0L) "${exam.title} is today" else "${exam.title} in $dd days"
                    body = "Time for a quick revision session."; big = body; shown += "exam${exam.id}"
                }
                topics.isNotEmpty() -> {
                    val t = topics.random(); title = "Revise: ${t.title}"
                    val snip = t.blocks.firstOrNull { it.type == "text" && it.text.isNotBlank() }?.text?.take(140) ?: ""
                    body = "Can you explain it without looking?"; big = if (snip.isBlank()) body else "$body\n\n$snip"; shown += t.id
                }
            }
        }
        if (title == null && force) { title = "Reminders are on"; body = "Add topics or flashcards and they will show up here."; big = body }
        if (title == null) return Result.success()
        sp.edit().putStringSet(key, shown).apply()
        Notifier.study(ctx, title, body, big)
        return Result.success()
    }
}
