package com.wilfrombdn.heavynt

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- formatting ----------
private val zone get() = ZoneId.systemDefault()
private fun fmt(p: String) = DateTimeFormatter.ofPattern(p, Locale.getDefault()).withZone(zone)
fun fmtDate(iso: String): String = fmt("EEE, MMM d · h:mm a").format(Instant.parse(iso))
fun fmtShort(iso: String): String = fmt("MMM d").format(Instant.parse(iso))
fun fmtDay(key: String): String = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(parseKey(key))
fun fmtLongDay(key: String): String = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()).format(parseKey(key))
fun fmtToday(): String = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()).format(today())
fun fmtDuration(a: String, b: String): String {
    val m = maxOf(1L, (isoMillis(b) - isoMillis(a)) / 60000L)
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun describe(st: AppState, ex: String, sets: Int, reps: Int, weight: Double): String {
    val e = st.ex(ex)
    val w = when {
        weight > 0 -> " @ ${fmtNum(weight)} ${st.settings.unit}${if (e?.pair == true) " total" else ""}"
        e != null && e.bw > 0 -> " · bodyweight"
        else -> ""
    }
    return "$sets × $reps$w"
}

fun weightLabel(st: AppState, ex: Exercise?): String =
    if (ex?.pair == true) "Weight (${st.settings.unit}) · both dumbbells combined" else "Weight (${st.settings.unit})"

const val PAIR_NOTE = "Add both dumbbells together, e.g. two 40s = 80."

// ---------- workout transitions ----------
fun AppState.startWorkout(planId: String?): AppState {
    if (active != null) return this
    val p = plan(planId)
    return copy(active = Active(
        id = uid(), start = nowIso(), planId = p?.id, planName = p?.name ?: "Quick workout",
        items = p?.items?.map { ActiveItem(it.ex, it.sets, it.reps, it.weight, it.note, ss = it.ss) } ?: emptyList(),
    ))
}

private fun AppState.mapItems(f: (List<ActiveItem>) -> List<ActiveItem>): AppState =
    active?.let { copy(active = it.copy(items = f(it.items))) } ?: this

fun AppState.toggleDone(i: Int): AppState = mapItems { items ->
    items.mapIndexed { j, it -> if (j == i) it.copy(done = !it.done, at = if (!it.done) nowIso() else null) else it }
}

fun AppState.editItem(i: Int, sets: Int, reps: Int, weight: Double, complete: Boolean): AppState = mapItems { items ->
    items.mapIndexed { j, it ->
        if (j != i) it else it.copy(
            sets = sets, reps = reps, weight = weight,
            done = it.done || complete, at = if (!it.done && complete) nowIso() else it.at,
        )
    }
}

fun AppState.removeItem(i: Int): AppState = mapItems { items -> items.filterIndexed { j, _ -> j != i } }
fun AppState.addItem(ex: String, sets: Int, reps: Int, weight: Double): AppState =
    mapItems { it + ActiveItem(ex, sets, reps, weight, extra = true) }

fun AppState.finishWorkout(): AppState {
    val a = active ?: return this
    val done = a.items.filter { it.done }
    if (done.isEmpty()) return copy(active = null)
    val log = Log(a.id, a.start, nowIso(), a.planId, a.planName, done.map { Entry(it.ex, it.sets, it.reps, it.weight, it.at) })
    return copy(logs = logs + log, active = null)
}

fun AppState.discardWorkout(): AppState = copy(active = null)

fun AppState.updatePlanItem(planId: String, ex: String, sets: Int, reps: Int, weight: Double): AppState =
    copy(plans = plans.map { p ->
        if (p.id != planId) p else p.copy(items = p.items.map { if (it.ex == ex) it.copy(sets = sets, reps = reps, weight = weight) else it })
    })

/** Raise a lift's weight everywhere that matters: today's workout and the plan. */
fun AppState.raiseWeight(ex: String, weight: Double): AppState {
    val a = active
    val withActive = a?.let { copy(active = it.copy(items = it.items.map { i -> if (i.ex == ex) i.copy(weight = weight) else i })) } ?: this
    val pid = a?.planId ?: return withActive
    return withActive.copy(plans = withActive.plans.map { p ->
        if (p.id != pid) p else p.copy(items = p.items.map { if (it.ex == ex) it.copy(weight = weight) else it })
    })
}

fun AppState.snoozeLift(ex: String, count: Int): AppState = copy(snooze = snooze + (ex to count))

fun AppState.setOverride(key: String, o: Override?): AppState =
    copy(overrides = if (o == null) overrides - key else overrides + (key to o))

// ---------- plans & schedule ----------
fun AppState.replacePlan(p: Plan): AppState = copy(plans = plans.map { if (it.id == p.id) p else it })

fun AppState.deletePlan(id: String): AppState =
    copy(plans = plans.filter { it.id != id }, schedule = schedule.copy(days = schedule.days.map { if (it == id) null else it }))

fun AppState.setScheduleDay(i: Int, planId: String?): AppState =
    copy(schedule = schedule.copy(days = schedule.days.mapIndexed { j, d -> if (j == i) planId else d }))

fun AppState.resizeWeeks(n: Int): AppState {
    val len = n * 7
    val days = schedule.days.take(len) + List(maxOf(0, len - schedule.days.size)) { null }
    return copy(schedule = schedule.copy(days = days))
}

// ---------- whole plans (saved / switch) ----------
private fun AppState.currentAsSaved() = SavedPlan(uid(), planName, plans, schedule, dateKey())

/** Day-by-day switches made for the old plan don't apply to the new one. */
private fun AppState.dropFutureOverrides(): AppState = copy(overrides = overrides.filterKeys { it < dateKey() })

fun AppState.saveCopyOfPlan(): AppState = copy(saved = saved + currentAsSaved())

/** Put the current plan in Saved plans and start a new one, empty or as a copy of the current one. */
fun AppState.startNewPlan(name: String, copyCurrent: Boolean): AppState {
    val base = copy(saved = saved + currentAsSaved(), planName = name)
    if (!copyCurrent) return base.copy(plans = emptyList(), schedule = Schedule(mondayOf(dateKey()), List(7) { null })).dropFutureOverrides()
    val ids = plans.associate { it.id to uid() }
    return base.copy(
        plans = plans.map { it.copy(id = ids.getValue(it.id)) },
        schedule = schedule.copy(days = schedule.days.map { d -> d?.let { ids[it] } }),
    ).dropFutureOverrides()
}

/** Swap a saved plan in; the current one goes into Saved plans in its place. */
fun AppState.switchToSaved(id: String): AppState {
    val target = saved.firstOrNull { it.id == id } ?: return this
    return copy(
        saved = saved.filter { it.id != id } + currentAsSaved(),
        planName = target.name, plans = target.plans, schedule = target.schedule,
    ).dropFutureOverrides()
}

fun AppState.deleteSaved(id: String): AppState = copy(saved = saved.filter { it.id != id })

// ---------- exercises ----------
fun AppState.upsertExercise(e: Exercise): AppState =
    if (exercises.any { it.id == e.id }) copy(exercises = exercises.map { if (it.id == e.id) e else it }) else copy(exercises = exercises + e)

fun AppState.deleteExercise(id: String): AppState =
    copy(
        exercises = exercises.filter { it.id != id }, plans = plans.map { p -> p.copy(items = p.items.filter { it.ex != id }) },
        saved = saved.map { sp -> sp.copy(plans = sp.plans.map { p -> p.copy(items = p.items.filter { it.ex != id }) }) },
    )

// ---------- settings ----------
fun AppState.withSettings(f: (Settings) -> Settings): AppState = copy(settings = f(settings))

/** Switch lb <-> kg, optionally converting every stored weight. */
fun AppState.switchUnit(to: String, convert: Boolean): AppState {
    if (to == settings.unit) return this
    if (!convert) return withSettings { it.copy(unit = to) }
    val k = if (to == "kg") 1 / LB_PER_KG else LB_PER_KG
    fun cv(w: Double) = if (w > 0) Math.round(w * k * 2) / 2.0 else w
    return copy(
        settings = settings.copy(unit = to, bodyweight = Math.round(settings.bodyweight * k * 10) / 10.0),
        plans = plans.map { p -> p.copy(items = p.items.map { it.copy(weight = cv(it.weight)) }) },
        saved = saved.map { sp -> sp.copy(plans = sp.plans.map { p -> p.copy(items = p.items.map { it.copy(weight = cv(it.weight)) }) }) },
        logs = logs.map { l -> l.copy(entries = l.entries.map { it.copy(weight = cv(it.weight)) }) },
        active = active?.let { a -> a.copy(items = a.items.map { it.copy(weight = cv(it.weight)) }) },
    )
}
