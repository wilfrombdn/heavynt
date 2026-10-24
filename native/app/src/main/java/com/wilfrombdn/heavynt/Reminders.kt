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
    private const val WEIGH_IN = 7100

    private fun pending(ctx: Context, i: Int, title: String?, text: String?): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(ctx, BASE + i, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, st: AppState) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val time = runCatching { LocalTime.parse(st.settings.reminderTime) }.getOrDefault(LocalTime.of(7, 0))
        scheduleWeighIn(ctx, am, time)
        for (i in 0..30) am.cancel(pending(ctx, i, null, null))
        if (!st.settings.reminderOn) return
        for (i in 0..30) {
            val key = addDays(dateKey(), i)
            val plan = planForDate(st, key) ?: continue
            if (i == 0 && todaysLog(st, plan.id) != null) continue
            val at = LocalDateTime.of(parseKey(key), time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= System.currentTimeMillis()) continue
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, i, "Today: ${plan.name}", "${plan.items.size} exercises are ready. Tap to start your workout."))
        }
    }

    /** Weigh-in nudge on the 1st of next month; the receiver drops it if the weight was already updated that month. */
    private fun scheduleWeighIn(ctx: Context, am: AlarmManager, time: LocalTime) {
        val intent = Intent(ctx, ReminderReceiver::class.java).putExtra("weighIn", true)
        val pi = PendingIntent.getBroadcast(ctx, WEIGH_IN, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val first = today().withDayOfMonth(1).plusMonths(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, LocalDateTime.of(first, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun show(ctx: Context, title: String, text: String, id: Int = 42) {
        ensureChannel(ctx)
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_workout).setContentTitle(title).setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { /* permission revoked */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getBooleanExtra("weighIn", false)) {
            val st = storageFor(ctx).loadOrNull() ?: return
            if (weighInDue(st)) Reminders.show(ctx, "New month: weigh in", "Update your bodyweight so your strength targets stay accurate.", id = 43)
            Reminders.schedule(ctx, st) // queue next month's
            return
        }
        val title = intent.getStringExtra("title") ?: return
        Reminders.show(ctx, title, intent.getStringExtra("text") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) storageFor(ctx).loadOrNull()?.let { Reminders.schedule(ctx, it) }
    }
}
