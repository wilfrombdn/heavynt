package com.wilfrombdn.heavynt

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// ---------- body regions ----------
val REGIONS: LinkedHashMap<String, String> = linkedMapOf(
    "upperChest" to "Upper chest", "chest" to "Chest", "shoulders" to "Front delts", "sideDelts" to "Side delts", "rearDelts" to "Rear delts",
    "traps" to "Traps", "upperBack" to "Mid back", "lats" to "Lats", "lowerBack" to "Lower back",
    "biceps" to "Biceps", "triceps" to "Triceps", "forearms" to "Forearms", "abs" to "Abs", "obliques" to "Obliques",
    "hipFlexors" to "Hip flexors", "glutes" to "Glutes", "abductors" to "Outer hips", "adductors" to "Inner thighs",
    "quads" to "Quads", "hamstrings" to "Hamstrings", "calves" to "Calves", "tibialis" to "Shins", "neck" to "Neck",
)

/** Muscles grouped by body area, for pickers. */
val REGION_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Chest" to listOf("upperChest", "chest"),
    "Shoulders" to listOf("shoulders", "sideDelts", "rearDelts"),
    "Back" to listOf("traps", "upperBack", "lats", "lowerBack"),
    "Arms" to listOf("biceps", "triceps", "forearms"),
    "Core" to listOf("abs", "obliques"),
    "Hips & legs" to listOf("hipFlexors", "glutes", "abductors", "adductors", "quads", "hamstrings", "calves", "tibialis"),
    "Neck" to listOf("neck"),
)
val CATEGORIES = listOf("Chest", "Back", "Shoulders", "Arms", "Core", "Legs", "Olympic", "Neck")
val DEFAULT_STD = mapOf("Chest" to 1.0, "Back" to 0.9, "Shoulders" to 0.5, "Arms" to 0.5, "Core" to 0.5, "Legs" to 1.4, "Olympic" to 0.9, "Neck" to 0.2)
val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

// ---------- dates ----------
fun today(): LocalDate = LocalDate.now()
fun dateKey(d: LocalDate = today()): String = d.toString()
fun parseKey(k: String): LocalDate = LocalDate.parse(k)
fun addDays(key: String, n: Int): String = parseKey(key).plusDays(n.toLong()).toString()

/** A new month has started since the bodyweight was last saved, and we haven't asked about it yet this month. */
fun weighInDue(st: AppState, day: LocalDate = today()): Boolean {
    val s = st.settings
    val month = day.toString().take(7)
    return s.bodyweight > 0 && s.bodyweightSetAt.take(7) < month && s.weighInAsked != month
}
fun dayDiff(a: String, b: String): Int = ChronoUnit.DAYS.between(parseKey(b), parseKey(a)).toInt()
fun weekdayIdx(key: String): Int = parseKey(key).dayOfWeek.value - 1 // Mon = 0
fun mondayOf(key: String): String = addDays(key, -weekdayIdx(key))
fun nowIso(): String = Instant.now().toString()
fun isoToLocalDate(iso: String): LocalDate = Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
fun isoMillis(iso: String): Long = Instant.parse(iso).toEpochMilli()

// ---------- schedule ----------
fun cycleIndex(s: Schedule, key: String = dateKey()): Int {
    val len = s.days.size
    if (len == 0) return -1
    return Math.floorMod(dayDiff(key, s.start), len)
}

/** What the repeating schedule says for a date (ignores one-off switches). */
fun scheduledPlan(st: AppState, key: String = dateKey()): Plan? {
    val i = cycleIndex(st.schedule, key)
    if (i < 0) return null
    return st.plan(st.schedule.days[i])
}

/** What you are actually doing on a date: a one-off switch wins over the schedule. */
fun planForDate(st: AppState, key: String = dateKey()): Plan? {
    val o = st.overrides[key]
    if (o != null) return st.plan(o.plan)
    return scheduledPlan(st, key)
}

fun todaysLog(st: AppState, planId: String? = null): Log? =
    st.logs.firstOrNull { isoToLocalDate(it.start).toString() == dateKey() && (planId == null || it.planId == planId) }

fun nextWorkout(st: AppState): Pair<String, Plan>? {
    for (i in 1..max(1, st.schedule.days.size)) {
        val key = addDays(dateKey(), i)
        val p = planForDate(st, key)
        if (p != null) return key to p
    }
    return null
}

// ---------- strength ----------
fun est1RM(weight: Double, reps: Int): Double = if (reps <= 1) weight else weight * (1 + reps / 30.0)

const val LB_PER_KG = 2.20462
val LEVELS = listOf("beginner", "novice", "intermediate", "advanced", "elite")

class SexStandard(val refKg: Double, val exponent: Double, val levels: Map<String, Double>)
class Standards(
    val male: SexStandard, val female: SexStandard, val age: List<Pair<Double, Double>>, val percentiles: Map<String, Double>,
    val femaleRatio: Map<String, Double>, val femaleRatioDefault: Double, val bodyweightFloor: Double,
)

/** Share of peak (25-40) strength at this age, read off the standards' age curve. */
fun ageFactor(age: Int): Double {
    val t = STANDARDS.age
    if (age <= t.first().first) return t.first().second
    if (age >= t.last().first) return t.last().second
    val i = t.indexOfFirst { it.first >= age }
    val (a0, f0) = t[i - 1]; val (a1, f1) = t[i]
    return f0 + (f1 - f0) * (age - a0) / (a1 - a0)
}

/** Intermediate 1RM ÷ bodyweight for this sex at its reference bodyweight. */
fun stdFor(ex: Exercise, female: Boolean): Double {
    if (!female) return ex.std
    val measured = if (ex.stdF > 0) ex.stdF
        else ex.std * (ex.primary.firstOrNull()?.let { STANDARDS.femaleRatio[it] } ?: STANDARDS.femaleRatioDefault)
    // pull-ups, dips…: an average woman still gets a few reps, so never ask for less than one bodyweight rep
    return if (ex.bw > 0) max(measured, ex.bw * STANDARDS.bodyweightFloor) else measured
}

/**
 * What a lifter with this profile lifts for one rep at a given level (default: the user's own target level).
 * Scales with bodyweight^exponent per sex, counts weight above BMI 27 half, then applies the age curve.
 */
fun targetFor(ex: Exercise, st: Settings, level: String = st.level): Double {
    val kg = if (st.unit == "lb") st.bodyweight / LB_PER_KG else st.bodyweight
    var eff = kg
    if (st.heightCm > 0) {
        val m = st.heightCm / 100
        val cap = 27 * m * m
        if (kg > cap) eff = cap + 0.5 * (kg - cap)
    }
    val female = st.sex == "female"
    val sx = if (female) STANDARDS.female else STANDARDS.male
    val baseKg = sx.refKg * (eff / sx.refKg).pow(sx.exponent) * ageFactor(st.age) * (sx.levels[level] ?: 1.0)
    return stdFor(ex, female) * baseKg * (if (st.unit == "lb") LB_PER_KG else 1.0)
}

/** Roughly what share of lifters with the same sex, age and bodyweight this one-rep max beats. */
fun percentileFor(ex: Exercise, st: Settings, e1rm: Double): Int {
    val pts = LEVELS.map { targetFor(ex, st, it) to (STANDARDS.percentiles[it] ?: 50.0) }
    val p = when {
        e1rm <= pts.first().first -> pts.first().second * e1rm / pts.first().first
        e1rm >= pts.last().first -> min(99.0, pts.last().second + 20 * (e1rm / pts.last().first - 1))
        else -> {
            val i = pts.indexOfFirst { it.first >= e1rm }
            val (w0, p0) = pts[i - 1]; val (w1, p1) = pts[i]
            p0 + (p1 - p0) * (e1rm - w0) / (w1 - w0)
        }
    }
    return p.roundToInt().coerceIn(1, 99)
}

/** The level band this one-rep max reaches, e.g. "Novice". */
fun levelReached(ex: Exercise, st: Settings, e1rm: Double): String =
    (LEVELS.lastOrNull { e1rm >= targetFor(ex, st, it) } ?: "below beginner").replaceFirstChar { it.uppercase() }

data class LiftScore(val score: Double, val e1rm: Double, val target: Double)

fun entryScore(ex: Exercise, e: Entry, st: Settings): LiftScore? {
    val bw = st.bodyweight
    if (bw <= 0) return null
    if (ex.bw <= 0 && e.weight <= 0) return null
    val load = ex.bw * bw + e.weight
    if (load <= 0) return null
    val est = est1RM(load, e.reps)
    val t = targetFor(ex, st)
    return LiftScore(est / t, est, t)
}

data class RegionLift(val name: String, val score: Double, val e1rm: Double, val target: Double, val primary: Boolean, val ex: Exercise? = null)
data class RegionScore(val score: Double, val lifts: List<RegionLift>)

private const val SECONDARY_WEIGHT = 0.35
private const val WINDOW_DAYS = 90L

fun regionScores(st: AppState): Map<String, RegionScore> {
    val cutoff = System.currentTimeMillis() - WINDOW_DAYS * 86_400_000L
    val best = HashMap<String, LiftScore>()
    for (log in st.logs) {
        if (isoMillis(log.start) < cutoff) continue
        for (e in log.entries) {
            val ex = st.ex(e.ex) ?: continue
            val r = entryScore(ex, e, st.settings) ?: continue
            if (r.score > (best[ex.id]?.score ?: 0.0)) best[ex.id] = r
        }
    }
    class Acc(var sum: Double = 0.0, var w: Double = 0.0, val lifts: MutableList<RegionLift> = mutableListOf())
    val acc = HashMap<String, Acc>()
    for ((id, r) in best) {
        val ex = st.ex(id) ?: continue
        fun add(reg: String, w: Double) {
            if (reg !in REGIONS) return
            val a = acc.getOrPut(reg) { Acc() }
            a.sum += r.score * w; a.w += w
            a.lifts += RegionLift(ex.name, r.score, r.e1rm, r.target, w == 1.0, ex)
        }
        ex.primary.forEach { add(it, 1.0) }
        ex.secondary.forEach { add(it, SECONDARY_WEIGHT) }
    }
    return acc.mapValues { (_, a) -> RegionScore(a.sum / a.w, a.lifts.sortedByDescending { it.score }) }
}

fun scoreLabel(s: Double): String = when {
    s < 0.6 -> "Well below target"
    s < 0.85 -> "Below target"
    s < 1.0 -> "Almost there"
    s < 1.15 -> "On target"
    s < 1.4 -> "Above target"
    else -> "Well above target"
}

fun scoreStrength(s: Double): Float = min(1.0, abs(s - 1) / 0.5).toFloat()

// ---------- history & plateaus ----------
data class HistEntry(val ex: String, val sets: Int, val reps: Int, val weight: Double, val at: String)

fun exerciseHistory(st: AppState, exId: String): List<HistEntry> =
    st.logs.flatMap { log -> log.entries.filter { it.ex == exId }.map { HistEntry(it.ex, it.sets, it.reps, it.weight, it.at ?: log.start) } }
        .sortedBy { isoMillis(it.at) }

data class Plateau(val sessions: Int, val since: String, val weight: Double, val count: Int)

/** Same weight for N sessions (or 4+ weeks over 2+ sessions) and not recently dismissed. */
fun plateauInfo(st: AppState, exId: String, currentWeight: Double): Plateau? {
    val hist = exerciseHistory(st, exId)
    if (hist.isEmpty() || currentWeight <= 0) return null
    val n = st.settings.plateauSessions
    val last = hist.last().weight
    if (last != currentWeight) return null
    var streak = 0
    var i = hist.size - 1
    while (i >= 0 && hist[i].weight == last) { streak++; i-- }
    val since = hist[hist.size - streak].at
    val days = (System.currentTimeMillis() - isoMillis(since)) / 86_400_000L
    val hit = streak >= n || (streak >= 2 && days >= 28)
    if (!hit) return null
    val snoozed = st.snooze[exId]
    if (snoozed != null && hist.size - snoozed < n) return null
    return Plateau(streak, since, last, hist.size)
}

fun fmtNum(n: Double): String {
    val r = (n * 100).roundToInt() / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

fun weightStep(st: Settings): Double = if (st.unit == "kg") 2.5 else 5.0

/** Cycle length in whole weeks. */
fun weeksOf(s: Schedule): Int = max(1, ceil(s.days.size / 7.0).toInt())

/** RGB triple used both for the map and for legend/text colours. */
fun scoreRGB(score: Double): Triple<Int, Int, Int> {
    fun mix(a: IntArray, b: IntArray, t: Double) = Triple(
        (a[0] + (b[0] - a[0]) * t).toInt(), (a[1] + (b[1] - a[1]) * t).toInt(), (a[2] + (b[2] - a[2]) * t).toInt(),
    )
    return if (score < 1) mix(intArrayOf(150, 62, 66), intArrayOf(240, 64, 70), minOf(1.0, (1 - score) / 0.5))
    else mix(intArrayOf(58, 118, 230), intArrayOf(110, 175, 255), minOf(1.0, (score - 1) / 0.4))
}

// ---------- supersets ----------
/** A run of 2+ consecutive items where each item after the first is flagged "superset with previous". */
data class SsGroup(val start: Int, val size: Int, val letter: Char)

fun supersetGroups(flags: List<Boolean>): List<SsGroup> {
    val out = mutableListOf<SsGroup>()
    var i = 0
    while (i < flags.size) {
        var j = i + 1
        while (j < flags.size && flags[j]) j++
        if (j - i >= 2) out += SsGroup(i, j - i, 'A' + out.size)
        i = j
    }
    return out
}

/** Older plans only said "A2 · superset…" in the note: turn that into real superset links. */
fun inferSupersets(p: Plan): Plan {
    if (p.items.any { it.ss }) return p
    val tag = Regex("^[A-Z]2( |$)")
    return p.copy(items = p.items.mapIndexed { i, it -> if (i > 0 && tag.containsMatchIn(it.note)) it.copy(ss = true) else it })
}
