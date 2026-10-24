@file:OptIn(ExperimentalFoundationApi::class)

package com.wilfrombdn.heavynt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Screen(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun PageHead(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.h1)
            if (subtitle != null) Muted(subtitle)
        }
        trailing?.invoke()
    }
}

@Composable
fun ItemCard(st: AppState, it: ActiveItem, interactive: Boolean, badge: String? = null, onTap: () -> Unit = {}, onLong: () -> Unit = {}) {
    val ex = st.ex(it.ex) ?: return
    val last = remember(st.logs, it.ex) { exerciseHistory(st, it.ex).lastOrNull() }
    val plateau = interactive && plateauInfo(st, it.ex, it.weight) != null
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    val base = Modifier.fillMaxWidth().clip(shape)
        .background(if (it.done) Color(0x1F22C55E) else C.panel)
        .border(1.dp, if (it.done) C.green else C.line, shape)
    val m = if (interactive) base.combinedClickable(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap() },
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() },
    ) else base
    Row(m.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(if (it.done) C.green else Color.Transparent)
                .border(1.5.dp, if (it.done) C.green else Color(0xFF555555), CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (it.done) Text("✓", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (badge != null) Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(badge, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                }
                Text(ex.name, style = T.name, modifier = Modifier.weight(1f, fill = false))
            }
            val hint = if (interactive && ex.bw <= 0 && it.weight <= 0) " · hold to set weight" else ""
            Text(describe(st, it.ex, it.sets, it.reps, it.weight) + hint, style = T.sub.copy(color = if (it.done) C.greenSoft else C.sub))
            if (it.note.isNotBlank()) Text(it.note, style = T.small.copy(color = Color(0xFFB9B9B9)))
            if (last != null && interactive) Text(
                "Last: ${last.sets} × ${last.reps} @ ${fmtNum(last.weight)} ${st.settings.unit}${if (ex.pair) " total" else ""}",
                style = T.small,
            )
        }
        if (plateau) Tag("Plateau")
    }
}

/** Exercises in order; supersets are boxed together under a "Superset" header with A1/A2 badges. */
@Composable
fun ItemList(st: AppState, items: List<ActiveItem>, interactive: Boolean, onTap: (Int) -> Unit = {}, onLong: (Int) -> Unit = {}) {
    val groups = supersetGroups(items.map { it.ss })
    var i = 0
    while (i < items.size) {
        val g = groups.firstOrNull { it.start == i }
        if (g == null) {
            val idx = i
            ItemCard(st, items[idx], interactive, onTap = { onTap(idx) }, onLong = { onLong(idx) })
            i++
        } else {
            val shape = RoundedCornerShape(20.dp)
            Column(
                Modifier.fillMaxWidth().border(1.5.dp, Color(0xFF8A8A8A), shape).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Superset ${g.letter}")
                    Text("· back to back, rest after each round", style = T.small)
                }
                for (k in 0 until g.size) {
                    val idx = g.start + k
                    ItemCard(st, items[idx], interactive, badge = "${g.letter}${k + 1}", onTap = { onTap(idx) }, onLong = { onLong(idx) })
                }
            }
            i += g.size
        }
    }
}

@Composable
fun WorkoutScreen(st: AppState, vm: AppViewModel, goHome: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    var editIdx by remember { mutableStateOf<Int?>(null) }
    var promptIdx by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSwitch by remember { mutableStateOf(false) }
    var plateaus by remember { mutableStateOf<List<String>>(emptyList()) }

    fun start(planId: String?) {
        vm.update { it.startWorkout(planId) }
        val s = vm.state.value
        plateaus = s.active?.items?.filter { plateauInfo(s, it.ex, it.weight) != null }?.map { it.ex }?.distinct() ?: emptyList()
    }

    fun finish() {
        val a = st.active ?: return
        val done = a.items.count { it.done }
        val left = a.items.size - done
        when {
            done == 0 -> dialogs.ask("Nothing completed", "No exercises were checked off yet.",
                DialogBtn("Keep going", primary = true), DialogBtn("Discard workout") { vm.update { it.discardWorkout() } })
            left > 0 -> dialogs.ask("Finish workout?", "$left exercise${if (left > 1) "s" else ""} not completed will be skipped.",
                DialogBtn("Finish & save", primary = true) { vm.update { it.finishWorkout() }; toast(ctx, "Workout saved"); goHome() }, DialogBtn("Keep going"))
            else -> { vm.update { it.finishWorkout() }; toast(ctx, "Workout saved"); goHome() }
        }
    }

    Screen {
        val a = st.active
        if (a != null) {
            val done = a.items.count { it.done }
            PageHead(a.planName, "Started ${fmtDate(a.start)}")
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.line)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (a.items.isEmpty()) 0f else done / a.items.size.toFloat()).background(C.green))
            }
            Text("$done/${a.items.size} done · tap to complete · hold for sets & reps", style = T.small)
            ItemList(st, a.items, true, onTap = { i -> if (a.items[i].done) vm.update { s -> s.toggleDone(i) } else promptIdx = i }, onLong = { i -> editIdx = i })
            HButton("+ Add exercise", { showAdd = true })
            HButton("Finish workout", { finish() }, style = BtnStyle.Primary)
        } else {
            val plan = planForDate(st)
            val doneToday = plan != null && todaysLog(st, plan.id) != null
            val o = st.overrides[dateKey()]
            val skipped = o?.skipped?.let { st.plan(it) }
            PageHead("Workout", fmtToday())
            if (plan != null) {
                HCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(if (o != null) "Today · switched" else "Today")
                            Text(plan.name, style = T.big)
                            if (skipped != null) Text("Skipped ${skipped.name}", style = T.small)
                        }
                        if (doneToday) Tag("Done ✓")
                    }
                }
                ItemList(st, plan.items.map { ActiveItem(it.ex, it.sets, it.reps, it.weight, it.note, ss = it.ss) }, false)
                HButton(if (doneToday) "Start again" else "Start workout", { start(plan.id) }, style = BtnStyle.Primary)
                HButton("Switch today's workout", { showSwitch = true })
            } else {
                val n = nextWorkout(st)
                HCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rest day", style = T.big)
                        Muted(if (n != null) "Next: ${n.second.name} on ${fmtLongDay(n.first)}" else "No workouts scheduled")
                        if (skipped != null) Text("Skipped ${skipped.name}", style = T.small)
                    }
                }
                HButton("Train anyway: choose a workout", { showSwitch = true }, style = BtnStyle.Primary)
            }
        }
    }

    // ----- sheets -----
    editIdx?.let { idx ->
        val item = st.active?.items?.getOrNull(idx)
        if (item == null) editIdx = null else EditItemSheet(st, vm, idx, item, onDismiss = { editIdx = null })
    }
    promptIdx?.let { idx ->
        val item = st.active?.items?.getOrNull(idx)
        if (item == null || item.done) promptIdx = null else WeightPromptSheet(st, vm, idx, item, onDismiss = { promptIdx = null })
    }
    if (showAdd) AddExerciseSheet(st, vm, onDismiss = { showAdd = false })
    if (showSwitch) SwitchSheet(st, vm, onDismiss = { showSwitch = false }, start = { start(it) })
    plateaus.firstOrNull()?.let { exId ->
        PlateauSheet(st, vm, exId, onDone = { plateaus = plateaus.drop(1) })
    }
}

/** Complete an item with the given numbers, then offer to make a changed set/rep/weight permanent in the plan. */
private fun completeAndOfferPlanUpdate(vm: AppViewModel, dialogs: DialogHost, idx: Int, exId: String, s: Int, r: Int, weight: Double) {
    vm.update { it.editItem(idx, s, r, weight, complete = true) }
    val now = vm.state.value
    val plan = now.plan(now.active?.planId)
    val pi = plan?.items?.firstOrNull { it.ex == exId }
    val ex = now.ex(exId)
    if (plan != null && pi != null && ex != null && (pi.sets != s || pi.reps != r || pi.weight != weight)) {
        dialogs.ask("Update the plan?",
            "Change ${ex.name} from ${describe(now, ex.id, pi.sets, pi.reps, pi.weight)} to ${describe(now, ex.id, s, r, weight)} permanently in \"${plan.name}\"?",
            DialogBtn("Yes, update the plan", primary = true) { vm.update { it.updatePlanItem(plan.id, ex.id, s, r, weight) } },
            DialogBtn("Just this workout"))
    }
}

/** Shown when you tap an exercise to check it off: confirm the total weight (or bodyweight) you actually used. */
@Composable
private fun WeightPromptSheet(st: AppState, vm: AppViewModel, idx: Int, item: ActiveItem, onDismiss: () -> Unit) {
    val dialogs = LocalDialogs.current
    val ex = st.ex(item.ex) ?: return
    val last = remember { exerciseHistory(st, item.ex).lastOrNull() }
    val start = if (item.weight > 0) item.weight else last?.weight ?: 0.0
    var weight by remember { mutableStateOf(start) }
    val unit = st.settings.unit
    Sheet(ex.name, onDismiss) {
        Muted("${item.sets} × ${item.reps} · how much did you lift?")
        if (last != null) Muted("Last time: ${fmtNum(last.weight)} $unit${if (ex.pair) " total" else ""} · ${fmtShort(last.at)}")
        Stepper(weight, { weight = it }, label = "Total weight ($unit)", step = weightStep(st.settings))
        if (ex.pair) NoteBox(PAIR_NOTE)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton(if (weight > 0) "Complete · ${fmtNum(weight)} $unit" else "Complete · bodyweight", {
                onDismiss()
                completeAndOfferPlanUpdate(vm, dialogs, idx, item.ex, item.sets, item.reps, weight)
            }, style = BtnStyle.Primary)
            HButton("Bodyweight (no added weight)", {
                onDismiss()
                completeAndOfferPlanUpdate(vm, dialogs, idx, item.ex, item.sets, item.reps, 0.0)
            })
        }
    }
}

@Composable
private fun EditItemSheet(st: AppState, vm: AppViewModel, idx: Int, item: ActiveItem, onDismiss: () -> Unit) {
    val dialogs = LocalDialogs.current
    val ex = st.ex(item.ex) ?: return
    var sets by remember { mutableStateOf(item.sets.toDouble()) }
    var reps by remember { mutableStateOf(item.reps.toDouble()) }
    var weight by remember { mutableStateOf(item.weight) }
    val last = remember { exerciseHistory(st, item.ex).lastOrNull() }
    Sheet(ex.name, onDismiss) {
        if (last != null) Muted("Last time: ${last.sets} × ${last.reps} @ ${fmtNum(last.weight)} ${st.settings.unit}${if (ex.pair) " total" else ""} · ${fmtShort(last.at)}")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper(sets, { sets = it }, Modifier.weight(1f), "Sets", min = 1.0, max = 20.0)
            Stepper(reps, { reps = it }, Modifier.weight(1f), "Reps", min = 1.0, max = 100.0)
        }
        Stepper(weight, { weight = it }, label = weightLabel(st, ex), step = weightStep(st.settings))
        if (ex.pair) NoteBox(PAIR_NOTE)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton(if (item.done) "Save" else "Save & complete", {
                onDismiss()
                completeAndOfferPlanUpdate(vm, dialogs, idx, item.ex, sets.toInt(), reps.toInt(), weight)
            }, style = BtnStyle.Primary)
            HButton("Remove from this workout", { onDismiss(); vm.update { it.removeItem(idx) } }, style = BtnStyle.Danger)
        }
    }
}

@Composable
fun NoteBox(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(text, style = T.small.copy(color = Color(0xFFCFCFCF), fontSize = 13.sp))
    }
}

@Composable
private fun AddExerciseSheet(st: AppState, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var exId by remember { mutableStateOf("") }
    var sets by remember { mutableStateOf(3.0) }
    var reps by remember { mutableStateOf(10.0) }
    var weight by remember { mutableStateOf(0.0) }
    val ex = st.ex(exId)
    Sheet("Add exercise", onDismiss) {
        ExercisePicker(st.exercises, exId, { id ->
            exId = id
            exerciseHistory(st, id).lastOrNull()?.let { sets = it.sets.toDouble(); reps = it.reps.toDouble(); weight = it.weight }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper(sets, { sets = it }, Modifier.weight(1f), "Sets", min = 1.0, max = 20.0)
            Stepper(reps, { reps = it }, Modifier.weight(1f), "Reps", min = 1.0, max = 100.0)
        }
        Stepper(weight, { weight = it }, label = weightLabel(st, ex), step = weightStep(st.settings))
        if (ex?.pair == true) NoteBox(PAIR_NOTE)
        HButton("Add", {
            if (exId.isEmpty()) toast(ctx, "Pick an exercise") else { vm.update { it.addItem(exId, sets.toInt(), reps.toInt(), weight) }; onDismiss() }
        }, style = BtnStyle.Primary)
    }
}

@Composable
private fun PlateauSheet(st: AppState, vm: AppViewModel, exId: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val ex = st.ex(exId)
    val item = st.active?.items?.firstOrNull { it.ex == exId }
    val info = item?.let { plateauInfo(st, exId, it.weight) }
    if (ex == null || item == null || info == null) { onDone(); return }
    var target by remember(exId) { mutableStateOf(info.weight + weightStep(st.settings)) }
    fun notYet() { vm.update { it.snoozeLift(exId, info.count) }; onDone() }
    Sheet("Time to level up?", onDismiss = { notYet() }) {
        Text("${ex.name} has been ${fmtNum(info.weight)} ${st.settings.unit} for ${info.sessions} sessions (since ${fmtShort(info.since)}). Ready to go heavier?", style = T.body)
        Stepper(target, { target = it }, label = "New weight (${st.settings.unit})", step = weightStep(st.settings))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton("Increase weight", {
                if (target != info.weight) { vm.update { it.raiseWeight(exId, target) }; toast(ctx, "${ex.name} → ${fmtNum(target)} ${st.settings.unit}") }
                onDone()
            }, style = BtnStyle.Primary)
            HButton("Not yet", { notYet() })
        }
    }
}

/** Pick a different workout for today. Skipping the scheduled one drops it; tomorrow carries on as scheduled. */
@Composable
private fun SwitchSheet(st: AppState, vm: AppViewModel, onDismiss: () -> Unit, start: (String?) -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val key = dateKey()
    val sched = scheduledPlan(st, key)
    val current = planForDate(st, key)

    fun choose(plan: Plan?) {
        onDismiss()
        if (current != null && plan?.id == current.id) return
        if (sched == null || (plan != null && plan.id == sched.id)) {
            vm.update { it.setOverride(key, if (plan != null && sched != null && plan.id == sched.id) null else plan?.let { p -> Override(p.id, null) }) }
            toast(ctx, if (plan != null) "Today: ${plan.name}" else "Today is a rest day")
            return
        }
        dialogs.ask(
            "Skip ${sched.name}?",
            if (plan != null) "${sched.name} is scheduled for today. If you skip it, it's dropped completely and tomorrow carries on with the next day in your schedule."
            else "Make today a rest day. Tomorrow carries on with the next day in your schedule.",
            DialogBtn("Yes, skip ${sched.name}", primary = true) {
                vm.update { it.setOverride(key, Override(plan?.id, sched.id)) }
                toast(ctx, if (plan != null) "Skipped ${sched.name}. Today: ${plan.name}" else "Today is a rest day")
            },
            if (plan != null) DialogBtn("No, do both (this one as an extra)") { start(plan.id) } else DialogBtn("Cancel"),
        )
    }

    Sheet("Switch today's workout", onDismiss) {
        Muted(if (sched != null) "Scheduled today: ${sched.name}. Pick what you want to do instead." else "Nothing is scheduled today. Pick a workout.")
        st.plans.forEach { p ->
            val isSched = sched?.id == p.id
            OptionRow(p.name + if (isSched) " · scheduled" else "", current?.id == p.id) { choose(p) }
        }
        if (sched != null || current != null) OptionRow("Rest day (skip today)", current == null) { choose(null) }
        OptionRow("Empty workout (nothing from a plan)", false) { onDismiss(); start(null) }
        if (st.overrides[key] != null) HButton("Reset to scheduled plan", { onDismiss(); vm.update { it.setOverride(key, null) }; toast(ctx, "Back to your schedule") })
    }
}

@Composable
fun OptionRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TextStyle(fontSize = 16.sp, color = Color.White, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
        if (on) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
}
