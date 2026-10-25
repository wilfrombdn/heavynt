package com.wilfrombdn.heavynt

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
    var manage by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    val today = dateKey()
    val idx = cycleIndex(st.schedule, today)
    val weeks = st.schedule.days.size / 7
    val on = st.schedule.days.count { it != null }

    Screen(head = { PageHead("Plans") }) {
        HCard(onClick = { manage = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Current plan")
                    Text(st.planName, style = T.big)
                    Muted("${st.plans.size} workout day${if (st.plans.size == 1) "" else "s"}" +
                        if (st.saved.isNotEmpty()) " · ${st.saved.size} saved plan${if (st.saved.size == 1) "" else "s"}" else "")
                }
                Text("›", style = TextStyle(fontSize = 22.sp, color = C.muted))
            }
        }
        HItem(topLine = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Schedule")
                    Muted("Repeats ${if (weeks == 1) "every week" else "every $weeks weeks"} · $on workout${if (on == 1) "" else "s"} per cycle" +
                        if (idx >= 0 && weeks > 1) " · this is week ${idx / 7 + 1}" else "")
                }
                Box(Modifier.clip(Look.shape(14.dp)).border(Look.line, Color(0xFF3A3A3A), Look.shape(14.dp)).clickableNoRipple { schedule = true }.padding(horizontal = 18.dp, vertical = 9.dp)) {
                    Text("Edit", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
            }
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 14) {
                    val k = addDays(today, i)
                    val p = planNameFor(st, st.schedule.days.getOrNull(cycleIndex(st.schedule, k)))
                    val isToday = i == 0
                    Column(
                        Modifier.width(44.dp).clip(Look.shape(12.dp)).background(if (isToday) Color.White else Color.Transparent)
                            .border(Look.line, if (isToday) Color.White else if (p != null) Color(0xFF555555) else C.line, Look.shape(12.dp)).padding(vertical = 6.dp),
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
        SectionTitle("Workout days")
        // listed like the workout screen: flat rows, a short bar, the count on the right
        Column {
            Box(Modifier.fillMaxWidth().height(Look.line).background(C.line))
            st.plans.forEach { p ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable { editPlan = p.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(Look.shape(2.dp)).background(Color(0xFF4A4A4A)))
                    Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(p.name, style = T.name.copy(fontSize = 15.sp))
                        Text(p.items.mapNotNull { st.ex(it.ex)?.name }.take(3).joinToString(" · ").ifEmpty { "Empty" }, style = T.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${p.items.size}", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White))
                        Text("exercises", style = T.small)
                    }
                }
                Box(Modifier.fillMaxWidth().height(Look.line).background(C.line))
            }
        }
        if (st.plans.isEmpty()) Muted("No workout days yet. Add one, then pick which weekdays it falls on under Schedule › Edit.")
        HButton("+ New workout day", {
            val p = Plan(uid(), "Day ${st.plans.size + 1}")
            vm.update { it.copy(plans = it.plans + p) }
            editPlan = p.id
        }, style = BtnStyle.Primary)
    }

    editPlan?.let { id -> if (st.plan(id) != null) PlanEditor(st, vm, id) { editPlan = null } else editPlan = null }
    if (schedule) ScheduleEditor(st, vm) { schedule = false }
    if (manage) ManagePlans(st, vm, onBuild = { manage = false; building = true }) { manage = false }
    if (building) BuildPlan(st, vm) { building = false }
}

@Composable
private fun ManagePlans(st: AppState, vm: AppViewModel, onBuild: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    Sheet("Your plans", onDismiss) {
        Labeled("Current plan name") { HTextField(st.planName, { v -> vm.update { it.copy(planName = v) } }, placeholder = "e.g. Summer cut") }
        HButton("+ Build a new plan", onBuild, style = BtnStyle.Primary)
        HButton("Save a copy of this plan", { vm.update { it.saveCopyOfPlan() }; toast(ctx, "Saved to Saved plans") })
        SectionTitle("Saved plans")
        if (st.saved.isEmpty()) Muted("Nothing saved yet. When you build a new plan, the one you have now is kept here so you can switch back any time.")
        st.saved.reversed().forEach { sp ->
            HCard {
                Text(sp.name, style = T.name)
                Muted("${sp.plans.size} workout day${if (sp.plans.size == 1) "" else "s"}" + if (sp.savedAt.isNotEmpty()) " · saved ${fmtDay(sp.savedAt)}" else "")
                Muted(sp.plans.joinToString(" · ") { it.name }.ifEmpty { "Empty" })
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HButton("Switch to this", {
                        dialogs.ask("Switch to \"${sp.name}\"?", "\"${st.planName}\" is kept in Saved plans so you can switch back any time.",
                            DialogBtn("Switch", primary = true) { vm.update { it.switchToSaved(sp.id) }; toast(ctx, "Now using ${sp.name}"); onDismiss() },
                            DialogBtn("Cancel"))
                    }, Modifier.weight(1f), style = BtnStyle.Primary)
                    HButton("Delete", {
                        dialogs.ask("Delete saved plan?", "\"${sp.name}\" will be gone for good. Your workout history stays.",
                            DialogBtn("Delete", primary = true) { vm.update { it.deleteSaved(sp.id) } }, DialogBtn("Cancel"))
                    }, Modifier.weight(1f), style = BtnStyle.Danger)
                }
            }
        }
    }
}

@Composable
private fun BuildPlan(st: AppState, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("empty") }
    Sheet("Build a new plan", onDismiss) {
        Labeled("Name") { HTextField(name, { name = it }, placeholder = "e.g. Upper / Lower") }
        Labeled("Start from") {
            Segmented(listOf("empty" to "Blank", "copy" to "Copy of current"), from, { from = it })
        }
        NoteBox("\"${st.planName}\" goes into Saved plans, so you can switch back to it any time.")
        HButton("Create plan", {
            if (name.isBlank()) toast(ctx, "Give it a name")
            else { vm.update { it.startNewPlan(name.trim(), from == "copy") }; toast(ctx, "Now using ${name.trim()}"); onDismiss() }
        }, style = BtnStyle.Primary)
    }
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

    Sheet("Edit workout day", onDismiss = { close() }) {
        Labeled("Name") { HTextField(plan.name, { v -> edit { it.copy(name = v) } }, placeholder = "e.g. Push day") }
        plan.items.forEachIndexed { i, it ->
            val ex = st.ex(it.ex)
            Column(Modifier.fillMaxWidth().border(Look.line, C.line, Look.shape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExercisePicker(vm, st.exercises, it.ex, { v -> editItem(i) { x -> if (st.ex(v)?.isCardio == true) x.copy(ex = v, sets = 1, reps = 20, weight = 0.0) else x.copy(ex = v) } })
                if (ex?.pair == true) NoteBox("Weight is both dumbbells combined. $PAIR_NOTE")
                if (ex?.isCardio == true) MiniNumber("Minutes", it.reps.toDouble(), { v -> editItem(i) { x -> x.copy(sets = 1, reps = v.toInt()) } })
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                toast(ctx, "Day duplicated"); close()
            })
            HButton("Delete workout day", {
                dialogs.ask("Delete workout day?", "\"${plan.name}\" will be removed from your schedule too.",
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
    val len = s.days.size
    val thisMon = mondayOf(today)
    val planOpts = listOf(Opt("", "Rest")) + st.plans.map { Opt(it.id, it.name) }

    Sheet("Schedule", onDismiss) {
        Muted("Pick what you train on each weekday. If your pattern spans more than one week, choose how often it repeats.")
        Labeled("How often does this repeat?") {
            Segmented(listOf("1" to "Every week", "2" to "2 weeks", "3" to "3 weeks", "4" to "4 weeks"), weeks.toString(),
                { v -> vm.update { it.resizeWeeks(v.toInt()) } })
        }
        if (weeks > 1) Labeled("Which week are you in now?") {
            Segmented((0 until weeks).map { it.toString() to "Week ${it + 1}" }, thisWeek.toString(), { v ->
                vm.update { it.copy(schedule = it.schedule.copy(start = addDays(thisMon, -7 * v.toInt()))) }
            })
        }
        // Every week listed one after another, each with the real dates it next falls on (this week or later).
        for (w in 0 until weeks) {
            var ws = addDays(s.start, w * 7)
            val behind = dayDiff(thisMon, ws)
            if (behind > 0 && len > 0) ws = addDays(ws, Math.ceil(behind.toDouble() / len).toInt() * len)
            val range = "${fmtDay(ws)} – ${fmtDay(addDays(ws, 6))}" + if (ws == thisMon) " · this week" else ""
            SectionTitle(if (weeks > 1) "Week ${w + 1} · $range" else "Your week · $range")
            for (d in 0 until 7) {
                val i = w * 7 + d
                val date = addDays(ws, d)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.width(110.dp)) {
                        Text(WEEKDAYS[d], style = T.name.copy(fontSize = 15.sp))
                        Text(if (date == today) "Today" else fmtDay(date), style = T.small)
                    }
                    PickerField(planOpts, s.days.getOrNull(i) ?: "", { v -> vm.update { it.setScheduleDay(i, v.ifEmpty { null }) } }, Modifier.weight(1f), "Rest")
                }
            }
            HButton(if (weeks > 1) "Clear week ${w + 1}" else "Clear this week", { for (d in 0 until 7) vm.update { it.setScheduleDay(w * 7 + d, null) } })
        }
        HButton("Done", onDismiss, style = BtnStyle.Primary)
    }
}
