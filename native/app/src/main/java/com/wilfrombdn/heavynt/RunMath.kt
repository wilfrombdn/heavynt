package com.wilfrombdn.heavynt

import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Run tracking maths, kept free of android.* so it runs in plain JVM tests.

/** One GPS fix on a run. t = ms since the run started (pauses included); seg goes up by one after each pause. */
@Serializable
data class RunPoint(val lat: Double, val lon: Double, val alt: Double = 0.0, val t: Long, val seg: Int = 0)

/** What a saved run keeps in the app state; the route itself lives in its own file. */
@Serializable
data class RunSummary(
    val id: String,
    val start: String,
    val end: String,
    val distanceM: Double,
    val movingSec: Long,
    val elevGainM: Double = 0.0,
)

private const val EARTH_M = 6_371_000.0

fun haversineM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2).let { it * it } + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

fun dist(a: RunPoint, b: RunPoint) = haversineM(a.lat, a.lon, b.lat, b.lon)

/**
 * Decides which raw fixes become route points: drops poor-accuracy fixes, the first few while GPS settles,
 * jumps faster than anyone runs, and tiny wobbles while standing still.
 */
class TrackFilter(
    private val maxAccuracyM: Float = 20f,
    private val warmup: Int = 2,
    private val maxSpeedMs: Double = 12.0,
    private val minMoveM: Double = 2.0,
) {
    private var good = 0
    private var last: RunPoint? = null

    /** A new segment starts after a pause: the first fix after it is kept without comparing to the old position. */
    fun newSegment() { last = null; good = 0 }

    fun accept(p: RunPoint, accuracyM: Float): Boolean {
        if (accuracyM > maxAccuracyM) return false
        if (good < warmup) { good++; return false }
        val prev = last ?: run { last = p; return true }
        val d = dist(prev, p)
        val dt = (p.t - prev.t) / 1000.0
        if (dt <= 0) return false
        if (d / dt > maxSpeedMs) return false
        if (d < minMoveM) return false
        last = p
        return true
    }
}

/** Distance in metres, never joining the end of one segment to the start of the next. */
fun trackDistanceM(pts: List<RunPoint>): Double {
    var total = 0.0
    for (i in 1 until pts.size) if (pts[i].seg == pts[i - 1].seg) total += dist(pts[i - 1], pts[i])
    return total
}

/** Climb in metres. GPS height is noisy, so only rises past a few metres from the last low point count. */
fun elevationGainM(pts: List<RunPoint>, hysteresisM: Double = 4.0): Double {
    if (pts.isEmpty()) return 0.0
    var gain = 0.0
    var ref = pts[0].alt
    for (p in pts.drop(1)) {
        if (p.alt > ref + hysteresisM) { gain += p.alt - ref; ref = p.alt }
        else if (p.alt < ref) ref = p.alt
    }
    return gain
}

/**
 * Seconds for each full kilometre, then the leftover part (if any) as the last entry with its distance.
 * Time inside a segment only, so pauses don't count; the moment each km is passed is interpolated.
 */
data class Split(val km: Int, val metres: Double, val seconds: Double)

fun kmSplits(pts: List<RunPoint>, kmM: Double = 1000.0): List<Split> {
    val out = mutableListOf<Split>()
    var done = 0.0     // metres covered before this step
    var moving = 0.0   // moving seconds before this step
    var lastAt = 0.0
    var next = kmM
    for (i in 1 until pts.size) {
        val a = pts[i - 1]; val b = pts[i]
        if (a.seg != b.seg) continue
        val d = dist(a, b); val dt = (b.t - a.t) / 1000.0
        while (d > 0 && done + d >= next) {
            val at = moving + dt * (next - done) / d
            out += Split(out.size + 1, kmM, at - lastAt)
            lastAt = at; next += kmM
        }
        done += d; moving += dt
    }
    val rest = done - (next - kmM)
    if (rest > 1) out += Split(out.size + 1, rest, moving - lastAt)
    return out
}

/** Pace in seconds per km over the last [windowMs] of the current segment, or null when barely moving. */
fun currentPaceSecPerKm(pts: List<RunPoint>, windowMs: Long = 30_000): Double? {
    if (pts.size < 2) return null
    val last = pts.last()
    val tail = pts.filter { it.seg == last.seg && last.t - it.t <= windowMs }
    if (tail.size < 2) return null
    val d = trackDistanceM(tail); val sec = (tail.last().t - tail.first().t) / 1000.0
    if (d < 15 || sec <= 0) return null
    return sec / d * 1000
}

fun paceSecPerKm(distanceM: Double, seconds: Double): Double? = if (distanceM < 10) null else seconds / distanceM * 1000

fun fmtPace(secPerKm: Double?): String {
    if (secPerKm == null || secPerKm.isNaN() || secPerKm > 60 * 60) return "--:--"
    val s = secPerKm.roundToInt()
    return "%d:%02d".format(s / 60, s % 60)
}

fun fmtKm(m: Double): String = "%.2f".format(m / 1000)

fun fmtClock(sec: Long): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
