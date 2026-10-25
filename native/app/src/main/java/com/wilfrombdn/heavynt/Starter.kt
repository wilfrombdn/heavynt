package com.wilfrombdn.heavynt

// Starter plan so a new install has something to follow and shows how plans work: a 7 on / 7 off rotation,
// training Wed-Sun of the week off (rest on its first and last day). Weights start at 0 so you set your own.

const val STARTER_NAME = "7 on / 7 off"

private fun day(id: String, name: String, vararg items: PlanItem) = Plan("starter-$id", name, items.toList())
private fun i(ex: String, sets: Int, reps: Int, note: String = "") = PlanItem(ex, sets, reps, 0.0, note)

val STARTER_DAYS: List<Plan> = listOf(
    day("1", "Chest + Back (heavy)",
        i("incline-db", 4, 8),
        i("wide-pulldown", 4, 8),
        i("bench", 4, 8),
        i("cable-row", 4, 8),
        i("incline-db-fly", 3, 8),
        i("db-row", 3, 8, "Single-arm"),
        i("pallof", 3, 8),
    ),
    day("2", "Shoulders + Arms",
        i("ohp", 4, 8),
        i("lateral", 4, 8),
        i("rear-delt", 3, 8),
        i("face-pull", 3, 8),
        i("ez-curl", 3, 8),
        i("cgbp", 3, 8),
        i("hammer", 3, 8),
        i("oh-rope", 3, 8, "Overhead cable"),
    ),
    day("3", "Legs",
        i("leg-press", 4, 8, "Feet high and wide"),
        i("back-ext", 4, 8, "45°, hold a plate once easy"),
        i("split-squat", 3, 8),
        i("leg-curl", 4, 8),
        i("db-calf", 3, 8, "Add a dumbbell once easy"),
        i("lateral", 3, 8),
        i("side-plank", 3, 30, "30 second hold each side"),
    ),
    day("4", "Chest + Back",
        i("row", 4, 8),
        i("machine-chest", 4, 8),
        i("close-pulldown", 4, 8, "Neutral grip"),
        i("dip", 3, 8, "Weighted"),
        i("straight-arm", 3, 8),
        i("pec-deck", 3, 8),
        i("reverse-pec-deck", 3, 8),
        i("farmers-walk", 3, 1, "Carry 40 m"),
    ),
    day("5", "Shoulders + Arms (machines/cables)",
        i("machine-shoulder", 3, 8),
        i("cable-lateral", 4, 8),
        i("face-pull", 3, 8),
        i("incline-curl", 3, 8),
        i("oh-tri", 3, 8, "Dumbbell"),
        i("cable-curl", 3, 8),
        i("rope-pushdown", 3, 8),
    ),
)

/** Two-week cycle: week 1 trains Wed-Sun, week 2 is the work week. Anchored to the owner's week off (Tue 20 - Mon 26 Oct 2026). */
fun starterSchedule(): Schedule =
    Schedule("2026-10-19", List(14) { d -> if (d in 2..6) STARTER_DAYS[d - 2].id else null })

/** 3.9.0 shipped the starter with supersets and per-side notes; bring those days in line, keeping any weights already set. */
fun fixStarterDays(plans: List<Plan>): List<Plan> {
    val fresh = STARTER_DAYS.associateBy { it.id }
    return plans.map { p ->
        val f = fresh[p.id] ?: return@map p
        p.copy(items = p.items.mapIndexed { n, it -> f.items.getOrNull(n)?.takeIf { x -> x.ex == it.ex }?.let { x -> it.copy(note = x.note, ss = false) } ?: it })
    }
}
