package com.wilfrombdn.heavynt

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.time.temporal.ChronoUnit

@Composable
fun RunScreen(st: AppState, vm: AppViewModel) {
    val live by RunTracker.live.collectAsState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val id = openId
    if (id != null) {
        BackHandler { openId = null }
        key(id) { RunDetail(st, vm, id, onBack = { openId = null }) }
        return
    }
    val r = live
    if (r != null) LiveRunView(r, vm, onSaved = { openId = it }) else RunHome(st, vm, open = { openId = it })
}

@Composable
private fun BigStat(value: String, label: String, modifier: Modifier = Modifier, size: Int = 40) {
    Column(modifier) {
        Text(value, style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, color = Color.White))
        Text(label.uppercase(), style = T.eyebrow)
    }
}

@Composable
private fun LiveRunView(r: LiveRun, vm: AppViewModel, onSaved: (String) -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    // the clock ticks every second; distance and pace move with each GPS fix
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(r.paused) { while (!r.paused) { delay(1000); now = android.os.SystemClock.elapsedRealtime() } }
    val sec = RunTracker.movingMs(r, now) / 1000

    fun finish(save: Boolean) {
        val done = RunTracker.live.value ?: return
        val moving = RunTracker.movingMs(done)
        RunService.stop(ctx)
        RunTracker.clear()
        if (save) onSaved(saveRun(ctx, vm, done.start, moving, done.points).id)
        else { RunFiles.partial(ctx).delete(); toast(ctx, "Run discarded") }
    }

    Screen(head = { PageHead("Run", "Started ${fmtDate(r.start)}") }) {
        Text(if (r.paused) "Paused" else if (!r.gpsOk) "Waiting for GPS…" else "Recording", style = T.small.copy(color = if (r.paused || !r.gpsOk) C.muted else C.green))
        BigStat(fmtClock(sec), "Time", size = 64)
        Box(Modifier.fillMaxWidth().height(Look.line).background(C.line))
        Row(Modifier.fillMaxWidth()) {
            BigStat(fmtKm(r.distanceM), "Kilometres", Modifier.weight(1f))
            BigStat(fmtPace(if (r.paused) null else currentPaceSecPerKm(r.points)), "Pace /km", Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(Look.line).background(C.line))
        Row(Modifier.fillMaxWidth()) {
            BigStat(fmtPace(paceSecPerKm(r.distanceM, sec.toDouble())), "Avg pace /km", Modifier.weight(1f), size = 24)
            BigStat("≈ ${elevationGainM(r.points).toInt()} m", "Climb", Modifier.weight(1f), size = 24)
        }
        Box(Modifier.height(12.dp))
        if (r.paused) HButton("Resume", { RunService.send(ctx, RunService.ACTION_RESUME) }, style = BtnStyle.Primary)
        else HButton("Pause", { RunService.send(ctx, RunService.ACTION_PAUSE) })
        HButton("Finish", {
            dialogs.ask("Finish run?", "${fmtKm(r.distanceM)} km in ${fmtClock(sec)}." +
                    if (r.distanceM < 50) " Almost no distance was recorded, so there's no route to save." else "",
                DialogBtn("Save run", primary = true) { finish(true) },
                DialogBtn("Keep going"),
                DialogBtn("Discard", danger = true) { finish(false) })
        }, style = if (r.paused) BtnStyle.Outline else BtnStyle.Primary)
        Text("Tracking keeps going with the screen off. Pause and resume from the notification too.", style = T.small)
    }
}

@Composable
private fun RunHome(st: AppState, vm: AppViewModel, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    var partial by remember { mutableStateOf(RunFiles.loadPartial(ctx)) }

    fun startTracking() {
        if (!gpsOn(ctx)) {
            dialogs.ask("Turn on location", "GPS is off. Turn on location to track your run.",
                DialogBtn("Open settings", primary = true) { ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }, DialogBtn("Cancel"))
            return
        }
        RunFiles.partial(ctx).delete(); partial = null
        RunService.start(ctx)
    }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        when {
            res[Manifest.permission.ACCESS_FINE_LOCATION] == true -> startTracking()
            res[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> dialogs.ask("Precise location needed",
                "Only approximate location was allowed, which can't measure a run. Allow precise location for Heavyn't in your phone settings.",
                DialogBtn("Open app settings", primary = true) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null)))
                }, DialogBtn("Cancel"))
            else -> toast(ctx, "Location is needed to track a run")
        }
    }
    fun requestStart() {
        if (hasPreciseLocation(ctx)) { startTracking(); return }
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        perms.launch(list.toTypedArray())
    }

    val today = today()
    val weekStart = parseKey(mondayOf(dateKey()))
    val week = st.runs.filter { !isoToLocalDate(it.start).isBefore(weekStart) }
    val month = st.runs.filter { isoToLocalDate(it.start).let { d -> d.year == today.year && d.month == today.month } }

    Screen(head = { PageHead("Run") }) {
        Row(Modifier.fillMaxWidth()) {
            BigStat(fmtKm(week.sumOf { it.distanceM }), "km this week", Modifier.weight(1f), size = 28)
            BigStat(fmtKm(month.sumOf { it.distanceM }), "km this month", Modifier.weight(1f), size = 28)
            BigStat("${week.size}", "runs this week", Modifier.weight(1f), size = 28)
        }
        Box(Modifier.height(4.dp))
        HButton("Start run", { requestStart() }, style = BtnStyle.Primary)
        partial?.let { p ->
            HItem(topLine = true) {
                Text("Unfinished run from ${fmtDate(p.start)}", style = T.name)
                Text("${fmtKm(trackDistanceM(p.points))} km · ${fmtClock(p.activeMs / 1000)}. The app was closed before it was finished.", style = T.small)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HButton("Save it", { open(saveRun(ctx, vm, p.start, p.activeMs, p.points).id); partial = null }, Modifier.weight(1f))
                    HButton("Discard", { RunFiles.partial(ctx).delete(); partial = null }, Modifier.weight(1f), style = BtnStyle.Danger)
                }
            }
        }
        if (st.runs.isEmpty()) Muted("No runs yet. Tap Start run, put your phone away and go. Time, distance and pace are tracked even with the screen off.")
        else {
            SectionTitle("Your runs")
            Column { st.runs.sortedByDescending { it.start }.forEach { run -> RunRow(run) { open(run.id) } } }
        }
    }
}

@Composable
fun RunRow(r: RunSummary, onClick: () -> Unit) {
    HItem(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Run", style = T.name)
                Text("${fmtDate(r.start)} · ${fmtClock(r.movingSec)} · ${fmtPace(paceSecPerKm(r.distanceM, r.movingSec.toDouble()))} /km", style = T.small)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtKm(r.distanceM), style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White))
                Text("km", style = T.small)
            }
        }
    }
}

/** One saved run: route map, the numbers, and per-km splits. */
@Composable
fun RunDetail(st: AppState, vm: AppViewModel, id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val run = st.runs.firstOrNull { it.id == id } ?: run { onBack(); return }
    val pts = remember(id) { RunFiles.load(ctx, id) }
    Screen(head = {
        Row(Modifier.clickable(onClick = onBack).padding(vertical = 4.dp)) { Text("‹  Back", style = T.muted) }
        PageHead("Run", fmtDate(run.start))
    }) {
        when {
            pts != null && pts.size >= 2 -> RouteMap(pts)
            pts != null -> Muted("No route to show: you didn't move far enough for GPS to draw one.")
            else -> Muted("Route not available for this run (it was restored from a backup).")
        }
        Row(Modifier.fillMaxWidth()) {
            BigStat(fmtKm(run.distanceM), "Kilometres", Modifier.weight(1f), size = 32)
            BigStat(fmtClock(run.movingSec), "Time", Modifier.weight(1f), size = 32)
        }
        Row(Modifier.fillMaxWidth()) {
            BigStat(fmtPace(paceSecPerKm(run.distanceM, run.movingSec.toDouble())), "Avg pace /km", Modifier.weight(1f), size = 32)
            BigStat("≈ ${run.elevGainM.toInt()} m", "Climb", Modifier.weight(1f), size = 32)
        }
        val elapsedMin = ChronoUnit.MINUTES.between(java.time.Instant.parse(run.start), java.time.Instant.parse(run.end))
        if (elapsedMin * 60 > run.movingSec + 60) Text("Moving time only. ${elapsedMin} min from start to finish including pauses.", style = T.small)
        if (pts != null) {
            val splits = kmSplits(pts)
            if (splits.isNotEmpty()) {
                SectionTitle("Splits")
                Column {
                    splits.forEach { s ->
                        HItem {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (s.metres >= 999) "Km ${s.km}" else "Last ${fmtKm(s.metres)} km", style = T.name.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
                                Text("${fmtPace(paceSecPerKm(s.metres, s.seconds))} /km", style = T.body)
                            }
                        }
                    }
                }
            }
        }
        HButton("Delete run", {
            dialogs.ask("Delete run?", "This run and its route will be gone for good.",
                DialogBtn("Delete", primary = true) { RunFiles.delete(ctx, id); vm.update { s -> s.copy(runs = s.runs.filter { it.id != id }) }; onBack() },
                DialogBtn("Cancel"))
        }, style = BtnStyle.Danger)
    }
}

/** OpenStreetMap with the route drawn on it, dark to match the app. Tests (Robolectric) get a placeholder instead. */
@Composable
private fun RouteMap(pts: List<RunPoint>) {
    val shape = Look.shape(12.dp)
    val mod = Modifier.fillMaxWidth().height(300.dp).clip(shape)
    if (Build.FINGERPRINT == "robolectric") { Box(mod.background(C.panel)); return }
    AndroidView(modifier = mod, factory = { c ->
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = c.packageName
            osmdroidBasePath = java.io.File(c.cacheDir, "osmdroid")
            osmdroidTileCache = java.io.File(c.cacheDir, "osmdroid/tiles")
        }
        org.osmdroid.views.MapView(c).apply {
            setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            overlayManager.tilesOverlay.setColorFilter(org.osmdroid.views.overlay.TilesOverlay.INVERT_COLORS)
            val geo = pts.map { org.osmdroid.util.GeoPoint(it.lat, it.lon) }
            for (seg in pts.indices.groupBy { pts[it].seg }.values) {
                overlays.add(org.osmdroid.views.overlay.Polyline(this).apply {
                    setPoints(seg.map { geo[it] })
                    outlinePaint.color = android.graphics.Color.WHITE
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                })
            }
            overlays.add(org.osmdroid.views.overlay.CopyrightOverlay(c))
            val box = org.osmdroid.util.BoundingBox.fromGeoPoints(geo)
            addOnFirstLayoutListener { _, _, _, _, _ -> zoomToBoundingBox(box.increaseByScale(1.3f), false) }
            onResume()
        }
    }, onRelease = { it.onDetach() })
}
