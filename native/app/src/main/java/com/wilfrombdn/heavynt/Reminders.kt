package com.wilfrombdn.heavynt

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Daily reminders on workout days: schedules the next 30 days with the system AlarmManager. */
object Reminders {
    private const val CHANNEL = "workout-reminders"
    private const val BASE = 7000

    private fun pending(ctx: Context, i: Int, title: String?, text: String?): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(ctx, BASE + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, st: AppState) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0..30) am.cancel(pending(ctx, i, null, null))
        if (!st.settings.reminderOn) return
        val time = runCatching { LocalTime.parse(st.settings.reminderTime) }.getOrDefault(LocalTime.of(7, 0))
        for (i in 0..30) {
            val key = addDays(dateKey(), i)
            val plan = planForDate(st, key) ?: continue
            if (i == 0 && todaysLog(st, plan.id) != null) continue
            val at = LocalDateTime.of(parseKey(key), time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= System.currentTimeMillis()) continue
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, i, "Today: ${plan.name}", "${plan.items.size} exercises are ready. Tap to start your workout."))
        }
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(ctx: Context, title: String, text: String) {
        ensureChannel(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_workout).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(42, n) } catch (_: SecurityException) { /* permission revoked */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        Reminders.show(ctx, title, intent.getStringExtra("text") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) storageFor(ctx).loadOrNull()?.let { Reminders.schedule(ctx, it) }
    }
}
