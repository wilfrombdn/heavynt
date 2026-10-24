package com.wilfrombdn.heavynt

import kotlinx.serialization.Serializable

// The JSON shape matches the web app's backup format, so a backup copied from the old app imports directly.

@Serializable
data class Settings(
    val unit: String = "lb",
    val bodyweight: Double = 0.0,
    val heightCm: Double = 0.0,
    val age: Int = 30,
    val sex: String = "male",
    val level: String = "intermediate",
    val plateauSessions: Int = 3,
    val reminderOn: Boolean = false,
    val reminderTime: String = "07:00",
    val openOnLaunch: Boolean = true,
    val profileAsked: Boolean = false,
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val cat: String,
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val std: Double = 1.0,
    val bw: Double = 0.0,
    val pair: Boolean = false,
    val custom: Boolean = false,
)

@Serializable
data class PlanItem(
    val ex: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val weight: Double = 0.0,
    val note: String = "",
)

@Serializable
data class Plan(val id: String, val name: String, val items: List<PlanItem> = emptyList())

/** start = a Monday ("week 1"); days holds 7 entries per week and repeats every days.size / 7 weeks. */
@Serializable
data class Schedule(val start: String, val days: List<String?>)

@Serializable
data class Entry(
    val ex: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val at: String? = null,
)

@Serializable
data class Log(
    val id: String,
    val start: String,
    val end: String? = null,
    val planId: String? = null,
    val planName: String = "Workout",
    val entries: List<Entry> = emptyList(),
)

@Serializable
data class ActiveItem(
    val ex: String,
    val sets: Int,
    val reps: Int,
    val weight: Double = 0.0,
    val note: String = "",
    val done: Boolean = false,
    val at: String? = null,
    val extra: Boolean = false,
)

@Serializable
data class Active(
    val id: String,
    val start: String,
    val planId: String? = null,
    val planName: String = "Quick workout",
    val items: List<ActiveItem> = emptyList(),
)

/** One-off switch for a date: plan = what you do instead (null = rest), skipped = the scheduled plan you dropped. */
@Serializable
data class Override(val plan: String? = null, val skipped: String? = null)

@Serializable
data class AppState(
    val v: Int = 4,
    val settings: Settings = Settings(),
    val exercises: List<Exercise> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val schedule: Schedule = Schedule(java.time.LocalDate.now().toString(), List(14) { null }),
    val logs: List<Log> = emptyList(),
    val active: Active? = null,
    val snooze: Map<String, Int> = emptyMap(),
    val overrides: Map<String, Override> = emptyMap(),
)

class StarterPlan(val name: String, val weekday: Int, val items: List<PlanItem>)

fun AppState.ex(id: String): Exercise? = exercises.firstOrNull { it.id == id }
fun AppState.plan(id: String?): Plan? = plans.firstOrNull { it.id == id }
