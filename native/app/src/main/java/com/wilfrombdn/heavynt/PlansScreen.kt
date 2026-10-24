package com.wilfrombdn.heavynt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.time.format.TextStyle as DayStyle
import java.util.Locale

private fun planNameFor(st: AppState, id: String?) = st.plan(id)?.name

@Composable
fun PlansScreen(st: AppState, vm: AppViewModel) {
    var editPlan by remember { mutableStateOf<String?>(null) }
    var schedule by remember { mutableStateOf(false) }
    val today = dateKey()
    val idx = cycleIndex(st.schedule, today)
    val weeks = st.schedule.days.size / 7
    val on = st.schedule.days.count { it != null }

    Screen {
        PageHead("Plans")
        HCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Schedule")
                    Muted("Repeats ${if (weeks == 1) "every week" else "every $weeks weeks"} · $on workout${if (on == 1) "" else "s"} per cycle" +
                        if (idx >= 0 && weeks > 1) " · this is week ${idx / 7 + 1}" else "")
                }
                Box(Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(14.dp)).clickableNoRipple { schedule = true }.padding(horizontal = 18.dp, vertical = 9.dp)) {
                    Text("Edit", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
            }
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 14) {
                    val k = addDays(today, i)
                    val p = planNameFor(st, st.schedule.days.getOrNull(cycleIndex(st.schedule, k)))
                    val isToday = i == 0
                    Column(
                        Modifier.width(44.dp).clip(RoundedCornerShape(12.dp)).background(if (isToday) Color.White else Color.Transparent)
                            .border(1.dp, if (isToday) Color.White else if (p != null) Color(0xFF555555) else C.line, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val fg = if (isToday) Color.Black else if (p != null) Color.White else C.muted
                        Text(parseKey(k).dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()), style = TextStyle(fontSize = 10.sp, color = fg))
                        Text(parseKey(k).dayOfMonth.toString(), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg))
                        Text(p?.take(4) ?: "–", style = TextStyle(fontSize = 10.sp, color = fg))
                    }
                }
            }
        }
        SectionTitle("Workout plans")
        st.plans.forEach { p ->
            HCard(onClick = { editPlan = p.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.big)
                        Muted(p.items.mapNotNull { st.ex(it.ex)?.name }.take(4).joinToString(" · ").ifEmpty { "Empty" })
                    }
                    Text("›", style = TextStyle(fontSize = 22.sp, color = C.muted))
                }
            }
        }
        HButton("+ New plan", {
            val p = Plan(uid(), "Plan ${st.plans.size + 1}")
            vm.update { it.copy(plans = it.plans + p) }
            editPlan = p.id
        }, style = BtnStyle.Primary)
    }

    editPlan?.let { id -> if (st.plan(id) != null) PlanEditor(st, vm, id) { editPlan = null } else editPlan = null }
    if (schedule) ScheduleEditor(st, vm) { schedule = false }
}

@Composable
private fun PlanEditor(st: AppState, vm: AppViewModel, planId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val plan = st.plan(planId) ?: return
    fun edit(f: (Plan) -> Plan) = vm.update { s -> s.plan(planId)?.let { s.replacePlan(f(it)) } ?: s }
    fun editItem(i: Int, f: (PlanItem) -> PlanItem) = edit { p -> p.copy(items = p.items.mapIndexed { j, it -> if (j == i) f(it) else it }) }
    fun move(i: Int, d: Int) = edit { p ->
        val j = i + d
        if (j !in p.items.indices) p else p.copy(items = p.items.toMutableList().also { l -> val t = l[i]; l[i] = l[j]; l[j] = t })
    }
    fun close() { edit { p -> p.copy(items = p.items.filter { it.ex.isNotEmpty() }) }; onDismiss() }

    Sheet("Edit plan", onDismiss = { close() }) {
        Labeled("Name") { HTextField(plan.name, { v -> edit { it.copy(name = v) } }, placeholder = "Plan name") }
        plan.items.forEachIndexed { i, it ->
            val ex = st.ex(it.ex)
            Column(Modifier.fillMaxWidth().border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExercisePicker(st.exercises, it.ex, { v -> editItem(i) { x -> x.copy(ex = v) } })
                if (ex?.pair == true) NoteBox("Weight is both dumbbells combined. $PAIR_NOTE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniNumber("Sets", it.sets.toDouble(), { v -> editItem(i) { x -> x.copy(sets = v.toInt()) } }, Modifier.weight(1f))
                    MiniNumber("Reps", it.reps.toDouble(), { v -> editItem(i) { x -> x.copy(reps = v.toInt()) } }, Modifier.weight(1f))
                    MiniNumber(if (ex?.pair == true) "Total (${st.settings.unit})" else "Weight (${st.settings.unit})", it.weight,
                        { v -> editItem(i) { x -> x.copy(weight = v) } }, Modifier.weight(1.4f))
                }
                if (i > 0) CheckRow(it.ss, { v -> editItem(i) { x -> x.copy(ss = v) } }, "Superset with the exercise above")
                HTextField(it.note, { v -> editItem(i) { x -> x.copy(note = v) } }, placeholder = "Note (rest time, superset, finisher…)")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    IconBtn("↑", { move(i, -1) }); IconBtn("↓", { move(i, 1) })
                    IconBtn("✕", { edit { p -> p.copy(items = p.items.filterIndexed { j, _ -> j != i }) } })
                }
            }
        }
        HButton("+ Add exercise", { edit { p -> p.copy(items = p.items + PlanItem("", 3, 10, 0.0)) } })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton("Done", { close() }, style = BtnStyle.Primary)
            HButton("Duplicate", {
                vm.update { s -> s.plan(planId)?.let { p -> s.copy(plans = s.plans + p.copy(id = uid(), name = p.name + " copy")) } ?: s }
                toast(ctx, "Plan duplicated"); close()
            })
            HButton("Delete plan", {
                dialogs.ask("Delete plan?", "\"${plan.name}\" will be removed from your schedule too.",
                    DialogBtn("Delete", primary = true) { vm.update { it.deletePlan(planId) }; onDismiss() }, DialogBtn("Cancel"))
            }, style = BtnStyle.Danger)
        }
    }
}

@Composable
private fun ScheduleEditor(st: AppState, vm: AppViewModel, onDismiss: () -> Unit) {
    val s = st.schedule
    val weeks = s.days.size / 7
    val today = dateKey()
    val thisWeek = Math.floorDiv(cycleIndex(s, today), 7)
    var tab by remember { mutableStateOf(maxOf(0, thisWeek)) }
    if (tab >= weeks) tab = weeks - 1
    val len = s.days.size
    val planOpts = listOf(Opt("", "Rest")) + st.plans.map { Opt(it.id, it.name) }

    Sheet("Schedule", onDismiss) {
        Muted("Pick what you train on each weekday. If your pattern spans more than one week, choose how often it repeats.")
        Labeled("How often does this repeat?") {
            Segmented(listOf("1" to "Every week", "2" to "2 weeks", "3" to "3 weeks", "4" to "4 weeks"), weeks.toString(),
                { v -> vm.update { it.resizeWeeks(v.toInt()) } })
        }
        if (weeks > 1) {
            Labeled("Which week is THIS week?") {
                Segmented((0 until weeks).map { it.toString() to "Week ${it + 1}" }, thisWeek.toString(), { v ->
                    tab = v.toInt()
                    vm.update { it.copy(schedule = it.schedule.copy(start = addDays(mondayOf(today), -7 * v.toInt()))) }
                })
            }
            SectionTitle("Set up each week")
            Segmented((0 until weeks).map { it.toString() to "Week ${it + 1}" }, tab.toString(), { tab = it.toInt() })
        } else SectionTitle("Your week")

        for (d in 0 until 7) {
            val i = tab * 7 + d
            val base = addDays(s.start, i)
            val gap = Math.ceil((dayDiff(today, base)).toDouble() / len).toInt()
            val next = if (gap > 0) addDays(base, gap * len) else base
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(110.dp)) {
                    Text(WEEKDAYS[d], style = T.name.copy(fontSize = 15.sp))
                    Text(if (next == today) "Today" else "Next: ${fmtDay(next)}", style = T.small)
                }
                PickerField(planOpts, s.days.getOrNull(i) ?: "", { v -> vm.update { it.setScheduleDay(i, v.ifEmpty { null }) } }, Modifier.weight(1f), "Rest")
            }
        }
        HButton("Clear this week", { for (d in 0 until 7) vm.update { it.setScheduleDay(tab * 7 + d, null) } })
        HButton("Done", onDismiss, style = BtnStyle.Primary)
    }
}
