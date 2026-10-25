package com.wilfrombdn.heavynt

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import kotlinx.serialization.Serializable

// ---------- rest timer alarm ----------

/** Buzzes when the rest timer runs out, even with the screen off. */
object RestAlarm {
    private const val CHANNEL = "rest-timer"
    private const val ID = 4343

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(ctx, ID, Intent(ctx, RestReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Match the alarm to the workout: set for a running rest, cancelled otherwise. */
    fun sync(ctx: Context, restUntil: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        if (restUntil <= System.currentTimeMillis()) return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restUntil, pending(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restUntil, pending(ctx))
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restUntil, pending(ctx))
        }
    }

    fun show(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true); vibrationPattern = longArrayOf(0, 300, 150, 300)
            })
        val open = PendingIntent.getActivity(ctx, ID, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_workout).setContentTitle("Rest over").setContentText("Time for your next set.")
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(60_000)
            .setVibrate(longArrayOf(0, 300, 150, 300)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(ID, n) }
    }
}

class RestReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // only if that rest is still running (not skipped or the workout finished meanwhile)
        val a = storageFor(ctx).loadOrNull()?.active ?: return
        if (a.restUntil > 0 && a.restUntil <= System.currentTimeMillis() + 2000) RestAlarm.show(ctx)
    }
}

// ---------- automatic backup ----------

/** A backup file: the whole app state plus every run's route. */
@Serializable
data class BackupFile(val heavyntBackup: Int = 1, val savedAt: String, val state: AppState, val routes: Map<String, List<RunPoint>> = emptyMap())

/** Read either a backup file or the older copy-paste backup (plain state). */
fun parseBackup(text: String): BackupFile =
    if (text.contains("\"heavyntBackup\"")) AppJson.decodeFromString<BackupFile>(text)
    else BackupFile(savedAt = "", state = AppJson.decodeFromString<AppState>(text))

object AutoBackup {
    const val FOLDER = "Heavynt"
    private const val KEEP = 7
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun lastSaved(ctx: Context): String? = prefs(ctx).getString("last", null)
    val supported get() = Build.VERSION.SDK_INT >= 29

    /** Today's file in Downloads/Heavynt (rewritten through the day), keeping the last week of days. */
    fun write(ctx: Context, st: AppState): Result<Unit> = runCatching {
        check(supported) { "Needs Android 10 or newer" }
        if (st.demoBackup != null) return@runCatching   // never back up the demo over real data
        val routes = st.runs.mapNotNull { r -> RunFiles.load(ctx, r.id)?.let { r.id to it } }.toMap()
        val text = AppJson.encodeToString(BackupFile(savedAt = nowIso(), state = st, routes = routes))
        val res = ctx.contentResolver
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val rel = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/"
        val name = "heavynt-backup-${dateKey()}.json"
        val ours = mutableListOf<Pair<Long, String>>()
        res.query(coll, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'heavynt-backup-%'", arrayOf(rel), null)?.use { c ->
            while (c.moveToNext()) ours += c.getLong(0) to c.getString(1)
        }
        val uri = ours.firstOrNull { it.second == name }?.let { android.content.ContentUris.withAppendedId(coll, it.first) }
            ?: res.insert(coll, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            }) ?: error("Couldn't create the backup file")
        res.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        // keep only the newest week of daily files
        ours.filter { it.second != name }.sortedByDescending { it.second }.drop(KEEP - 1).forEach { (id, _) ->
            runCatching { res.delete(android.content.ContentUris.withAppendedId(coll, id), null, null) }
        }
        prefs(ctx).edit().putString("last", nowIso()).apply()
    }
}
