package com.wilfrombdn.heavynt

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// Developer demo: a made-up lifter whose upper body and core are well above average and whose legs, hips
// and lower back are well below, so every region of the body map lights up deep blue/pink or deep red.
// The exercises are picked so no "strong" lift helps a "weak" muscle (and the other way round).

/** Upper body, core and neck, logged at this × the average lift. */
private val DEMO_STRONG = listOf(
    "bench", "incline-bench", "ohp", "lateral", "reverse-pec-deck", "shrug", "cable-row", "lat-pulldown",
    "bb-curl", "pushdown", "wrist-curl", "cable-crunch", "woodchop", "leg-raise", "neck-curl",
)

/** Legs, hips and lower back, logged at this × the average lift. */
private val DEMO_WEAK = listOf(
    "squat", "seated-leg-curl", "hip-thrust", "hip-adduction", "hip-abduction", "calf-raise", "tibialis-raise", "good-morning",
)

private const val STRONG = 1.8
private const val WEAK = 0.4
private const val REPS = 5
private const val WEEKS = 8

private fun round5(x: Double) = maxOf(0.0, Math.round(x / 5) * 5.0)

/** The demo state for this person's sex (their real data is kept aside by the caller). */
fun demoState(real: AppState): AppState {
    val female = real.settings.sex == "female"
    val settings = real.settings.copy(
        unit = "lb", bodyweight = if (female) 140.0 else 180.0, heightCm = if (female) 165.0 else 178.0, age = 30,
        level = "intermediate", profileAsked = true, bodyweightSetAt = dateKey(), weighInAsked = YearMonth.now().toString(),
    )
    val exs = LIBRARY.associateBy { it.id }

    /** Weight that gives this × the average one-rep max for a set of REPS. */
    fun finalWeight(id: String, factor: Double): Double {
        val ex = exs.getValue(id)
        val load = factor * targetFor(ex, settings, "intermediate") / (1 + REPS / 30.0)
        return load - ex.bw * settings.bodyweight
    }
    val finals = DEMO_STRONG.associateWith { finalWeight(it, STRONG) } + DEMO_WEAK.associateWith { finalWeight(it, WEAK) }

    val days = listOf(
        Plan("demo-upper", "Upper (demo)", DEMO_STRONG.filter { exs.getValue(it).cat !in setOf("Core", "Neck") }.map { PlanItem(it, 3, REPS) }),
        Plan("demo-lower", "Lower (demo)", DEMO_WEAK.map { PlanItem(it, 3, REPS) }),
        Plan("demo-core", "Core & neck (demo)", DEMO_STRONG.filter { exs.getValue(it).cat in setOf("Core", "Neck") }.map { PlanItem(it, 3, REPS) }),
    )
    // Mon upper, Wed lower, Fri core; weights climb from 85% to 100% so the charts trend upward.
    val today = today()
    val monday = parseKey(mondayOf(dateKey()))
    val zone = ZoneId.systemDefault()
    val sessions = (WEEKS downTo 0).flatMap { w -> listOf(0 to days[0], 2 to days[1], 4 to days[2]).map { (d, p) -> monday.minusWeeks(w.toLong()).plusDays(d.toLong()) to p } }
        .filter { it.first.isBefore(today) }
    val logs = sessions.mapIndexed { i, (day: LocalDate, plan) ->
        val progress = 0.85 + 0.15 * i / maxOf(1, sessions.size - 1)
        val start = day.atTime(18, 0).atZone(zone).toInstant()
        Log(
            id = "demo-$i", start = start.toString(), end = start.plusSeconds(3600).toString(), planId = plan.id, planName = plan.name,
            entries = plan.items.mapIndexed { j, it ->
                Entry(it.ex, it.sets, it.reps, round5(finals.getValue(it.ex) * progress), start.plusSeconds(300L * (j + 1)).toString())
            },
        )
    }
    val plans = days.map { p -> p.copy(items = p.items.map { it.copy(weight = round5(finals.getValue(it.ex))) }) }
    val sched = MutableList<String?>(7) { null }.also { it[0] = plans[0].id; it[2] = plans[1].id; it[4] = plans[2].id }
    return AppState(
        settings = settings, exercises = LIBRARY, plans = plans, schedule = Schedule(mondayOf(dateKey()), sched), logs = logs,
        planName = "Demo plan",
    )
}
