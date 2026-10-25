package com.wilfrombdn.heavynt

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.io.File

/** The run being recorded. Lives outside the ViewModel so the screen reconnects to it after the app is closed and reopened. */
data class LiveRun(
    val start: String,
    val startElapsed: Long,
    val points: List<RunPoint> = emptyList(),
    val paused: Boolean = false,
    /** Moving ms banked before the latest resume. */
    val activeMs: Long = 0,
    val resumedAt: Long = startElapsed,
    val seg: Int = 0,
    val distanceM: Double = 0.0,
    val gpsOk: Boolean = false,
)

/** What's kept on disk while recording, so a run survives the app being killed. */
@Serializable
data class PartialRun(val start: String, val activeMs: Long, val points: List<RunPoint>)

object RunTracker {
    val live = MutableStateFlow<LiveRun?>(null)
    private var filter = TrackFilter()

    fun movingMs(r: LiveRun, now: Long = SystemClock.elapsedRealtime()) = r.activeMs + if (r.paused) 0 else now - r.resumedAt

    fun begin() {
        val now = SystemClock.elapsedRealtime()
        filter = TrackFilter()
        live.value = LiveRun(nowIso(), now)
    }

    fun onFix(loc: Location) {
        val r = live.value ?: return
        if (r.paused) return
        val elapsed = loc.elapsedRealtimeNanos / 1_000_000
        val p = RunPoint(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else 0.0, elapsed - r.startElapsed, r.seg)
        val acc = if (loc.hasAccuracy()) loc.accuracy else 99f
        if (filter.accept(p, acc)) {
            val prev = r.points.lastOrNull()
            val add = if (prev != null && prev.seg == p.seg) dist(prev, p) else 0.0
            live.value = r.copy(points = r.points + p, distanceM = r.distanceM + add, gpsOk = true)
        } else if ((acc <= 20f) != r.gpsOk) live.value = r.copy(gpsOk = acc <= 20f)
    }

    fun pause() {
        val r = live.value ?: return
        if (!r.paused) live.value = r.copy(paused = true, activeMs = movingMs(r))
    }

    fun resume() {
        val r = live.value ?: return
        if (!r.paused) return
        filter.newSegment()
        live.value = r.copy(paused = false, resumedAt = SystemClock.elapsedRealtime(), seg = r.seg + 1)
    }

    fun clear() { live.value = null }
}

object RunFiles {
    fun dir(ctx: Context) = File(ctx.filesDir, "runs").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.json")
    fun partial(ctx: Context) = File(dir(ctx), "current.json")

    fun save(ctx: Context, id: String, pts: List<RunPoint>) = file(ctx, id).writeText(AppJson.encodeToString(pts))
    fun load(ctx: Context, id: String): List<RunPoint>? =
        runCatching { AppJson.decodeFromString<List<RunPoint>>(file(ctx, id).readText()) }.getOrNull()
    fun delete(ctx: Context, id: String) { file(ctx, id).delete() }
    fun deleteAll(ctx: Context) { dir(ctx).deleteRecursively() }

    fun savePartial(ctx: Context, r: LiveRun) = runCatching {
        partial(ctx).writeText(AppJson.encodeToString(PartialRun(r.start, RunTracker.movingMs(r), r.points)))
    }
    fun loadPartial(ctx: Context): PartialRun? =
        runCatching { AppJson.decodeFromString<PartialRun>(partial(ctx).readText()) }.getOrNull()
}

/** Turn finished points into a saved run: the route goes to its own file, the summary into the app state. */
fun saveRun(ctx: Context, vm: AppViewModel, start: String, movingMs: Long, pts: List<RunPoint>): RunSummary {
    val s = RunSummary(uid(), start, nowIso(), trackDistanceM(pts), movingMs / 1000, elevationGainM(pts))
    RunFiles.save(ctx, s.id, pts)
    RunFiles.partial(ctx).delete()
    vm.update { it.copy(runs = it.runs + s) }
    return s
}

fun hasPreciseLocation(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun gpsOn(ctx: Context) = (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isProviderEnabled(LocationManager.GPS_PROVIDER)

/** Keeps GPS running with the screen off; shows time, distance and pace in a notification with Pause/Resume. */
class RunService : Service(), LocationListener {
    private val handler = Handler(Looper.getMainLooper())
    private var lastFlush = 0L
    private val tick = object : Runnable {
        override fun run() {
            val r = RunTracker.live.value ?: return
            notify(r)
            val now = SystemClock.elapsedRealtime()
            if (now - lastFlush > 20_000) { RunFiles.savePartial(this@RunService, r); lastFlush = now }
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> RunTracker.pause()
            ACTION_RESUME -> RunTracker.resume()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            else -> {
                startForegroundNow()
                if (RunTracker.live.value == null) RunTracker.begin()
                startGps()
            }
        }
        RunTracker.live.value?.let { notify(it) }
        return START_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Run tracking", NotificationManager.IMPORTANCE_LOW))
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, build(RunTracker.live.value), type)
    }

    private fun startGps() {
        if (!hasPreciseLocation(this)) { stopSelf(); return }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.removeUpdates(this)
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
        } catch (_: SecurityException) { stopSelf(); return }
        handler.removeCallbacks(tick)
        lastFlush = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    override fun onLocationChanged(location: Location) = RunTracker.onFix(location)
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
        super.onDestroy()
    }

    private fun notify(r: LiveRun) = getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(r))

    private fun build(r: LiveRun?): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra(EXTRA_OPEN, "run").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(label: String, act: String) = NotificationCompat.Action(0, label,
            PendingIntent.getService(this, act.hashCode(), Intent(this, RunService::class.java).setAction(act), PendingIntent.FLAG_IMMUTABLE))
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_workout)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(open)
        if (r == null) return b.setContentTitle("Run starting…").build()
        val sec = RunTracker.movingMs(r) / 1000
        b.setContentTitle(if (r.paused) "Run paused · ${fmtKm(r.distanceM)} km" else "Run · ${fmtKm(r.distanceM)} km")
            .setContentText("${fmtClock(sec)} · ${fmtPace(paceSecPerKm(r.distanceM, sec.toDouble()))} /km" + if (!r.gpsOk && !r.paused) " · waiting for GPS" else "")
        b.addAction(if (r.paused) action("Resume", ACTION_RESUME) else action("Pause", ACTION_PAUSE))
        return b.build()
    }

    companion object {
        const val CHANNEL = "run-tracking"
        const val NOTIF_ID = 4242
        const val ACTION_START = "com.wilfrombdn.heavynt.RUN_START"
        const val ACTION_PAUSE = "com.wilfrombdn.heavynt.RUN_PAUSE"
        const val ACTION_RESUME = "com.wilfrombdn.heavynt.RUN_RESUME"
        const val ACTION_STOP = "com.wilfrombdn.heavynt.RUN_STOP"
        const val EXTRA_OPEN = "open"

        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, RunService::class.java).setAction(ACTION_START))
        fun send(ctx: Context, action: String) = ctx.startService(Intent(ctx, RunService::class.java).setAction(action))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, RunService::class.java))
    }
}
