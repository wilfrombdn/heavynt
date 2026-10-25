@file:OptIn(ExperimentalFoundationApi::class)

package com.wilfrombdn.heavynt

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize

@Composable
fun Screen(content: @Composable () -> Unit) {
    // Short pages sit in the middle of the screen; long ones scroll from the top as usual.
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = h).padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) { content() }
    }
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
fun ItemCard(st: AppState, it: ActiveItem, interactive: Boolean, next: Boolean = false, badge: String? = null, onTap: () -> Unit = {}, onLong: () -> Unit = {}) {
    val ex = st.ex(it.ex) ?: return
    val last = remember(st.logs, it.ex) { exerciseHistory(st, it.ex).lastOrNull() }
    val plateau = interactive && plateauInfo(st, it.ex, it.weight) != null
    val haptic = LocalHapticFeedback.current
    val unit = st.settings.unit
    val base = Modifier.fillMaxWidth().alpha(if (it.done) 0.4f else 1f)
    val m = if (interactive) base.combinedClickable(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onTap() },
        onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLong() },
    ) else base
    // big number on the right: the weight, extra load on a bodyweight move, or BW
    val (big, bigUnit) = when {
        ex.isCardio -> "${it.reps}" to "min"
        it.weight > 0 && ex.bw > 0 -> "+${fmtNum(it.weight)}" to "$unit + BW"
        it.weight > 0 -> fmtNum(it.weight) to (if (ex.pair) "$unit total" else unit)
        ex.bw > 0 -> "BW" to ""
        else -> "—" to ""
    }
    // flat row: a short bar (white = up next, grey = still to come, green = done), the lift, and the weight
    Row(m.height(IntrinsicSize.Min).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(Look.shape(2.dp)).background(if (it.done) C.green else if (next) Color.White else Color(0xFF4A4A4A)))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (it.done) Text("✓", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = C.green))
                if (badge != null) Box(Modifier.clip(Look.shape(6.dp)).background(Color.White).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text(badge, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black))
                }
                Text(ex.name, style = T.name.copy(fontSize = 15.sp), modifier = Modifier.weight(1f, fill = false))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (ex.isCardio) "Cardio" else "${it.sets} × ${it.reps}", style = T.sub.copy(fontWeight = FontWeight.SemiBold))
                if (last != null && it.weight > 0 && !ex.isCardio) {
                    val d = it.weight - last.weight
                    if (d > 0) Text("▲ +${fmtNum(d)}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = C.green))
                    else Text("Last ${fmtNum(last.weight)}", style = T.small)
                }
            }
            if (it.note.isNotBlank()) Text(it.note, style = T.small.copy(color = Color(0xFFB9B9B9)))
            if (interactive && ex.bw <= 0 && it.weight <= 0 && !ex.isCardio) Text("Hold to set weight", style = T.small)
            if (plateau) Box(Modifier.padding(top = 4.dp)) { Tag("Plateau") }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(big, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (big == "—") C.muted else Color.White))
            if (bigUnit.isNotEmpty()) Text(bigUnit, style = T.small)
        }
    }
}

/** Minutes (and hours) since the workout started, ticking every second. */
@Composable
private fun Elapsed(startIso: String) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startIso) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val sec = ((now - isoMillis(startIso)) / 1000).coerceAtLeast(0)
    val text = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
    Text("⏱ $text", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
}

/** Exercises in order, as one list split by hairlines; supersets get a "Superset" header and A1/A2 badges. */
@Composable
fun ItemList(st: AppState, items: List<ActiveItem>, interactive: Boolean, onTap: (Int) -> Unit = {}, onLong: (Int) -> Unit = {}) {
    val groups = supersetGroups(items.map { it.ss })
    val next = items.indexOfFirst { !it.done }
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        var i = 0
        while (i < items.size) {
            val g = groups.firstOrNull { it.start == i }
            if (g == null) {
                val idx = i
                ItemCard(st, items[idx], interactive, next = idx == next, onTap = { onTap(idx) }, onLong = { onLong(idx) })
                Hairline()
                i++
            } else {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Eyebrow("Superset ${g.letter}")
                    Text("· back to back, rest after each round", style = T.small)
                }
                for (k in 0 until g.size) {
                    val idx = g.start + k
                    ItemCard(st, items[idx], interactive, next = idx == next, badge = "${g.letter}${k + 1}", onTap = { onTap(idx) }, onLong = { onLong(idx) })
                    Hairline()
                }
                i += g.size
            }
        }
    }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(Look.line).background(C.line))

@Composable
fun WorkoutScreen(st: AppState, vm: AppViewModel, goHome: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    var editIdx by remember { mutableStateOf<Int?>(null) }
    var promptIdx by remember { mutableStateOf<Int?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSwitch by remember { mutableStateOf(false) }
    var plateaus by remember { mutableStateOf<List<String>>(emptyList()) }
    var history by remember { mutableStateOf(false) }
    if (history) {
        BackHandler { history = false }
        HistoryScreen(st, vm, onBack = { history = false })
        return
    }

    fun start(planId: String?) {
        vm.update { it.startWorkout(planId) }
        val s = vm.state.value
        plateaus = s.active?.items?.filter { plateauInfo(s, it.ex, it.weight) != null }?.map { it.ex }?.distinct() ?: emptyList()
    }

    fun discard() = dialogs.ask("Discard this workout?", "Nothing from it will be saved.",
        DialogBtn("Discard workout", danger = true) { vm.update { it.discardWorkout() }; toast(ctx, "Workout discarded"); goHome() },
        DialogBtn("Keep it"))

    fun finish() {
        val a = st.active ?: return
        val done = a.items.count { it.done }
        val left = a.items.size - done
        when {
            done == 0 -> dialogs.ask("Nothing completed", "No exercises were checked off yet.",
                DialogBtn("Keep going", primary = true), DialogBtn("Discard workout") { vm.update { it.discardWorkout() } })
            left > 0 -> dialogs.ask("Finish workout?", "$left exercise${if (left > 1) "s" else ""} not completed will be skipped.",
                DialogBtn("Finish & save", primary = true) { vm.update { it.finishWorkout() }; toast(ctx, "Workout saved"); goHome() }, DialogBtn("Keep going"),
                DialogBtn("Discard workout", danger = true) { discard() })
            else -> { vm.update { it.finishWorkout() }; toast(ctx, "Workout saved"); goHome() }
        }
    }

    Screen {
        val a = st.active
        if (a != null) {
            val done = a.items.count { it.done }
            PageHead(a.planName, "Started ${fmtDate(a.start)}") { Elapsed(a.start) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(8.dp).clip(Look.shape(2.dp)).background(C.line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(if (a.items.isEmpty()) 0f else done / a.items.size.toFloat()).background(C.green))
                }
                Text("$done of ${a.items.size}", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White))
            }
            Text("Tap to complete · hold to change weight, sets & reps", style = T.small)
            ItemList(st, a.items, true, onTap = { i ->
                val item = a.items[i]
                val last = exerciseHistory(st, item.ex).lastOrNull()
                when {
                    item.done -> vm.update { s -> s.toggleDone(i) }
                    st.ex(item.ex)?.isCardio == true -> vm.update { s -> s.editItem(i, item.sets, item.reps, 0.0, complete = true) }
                    item.weight > 0 || last != null -> completeAndSaveToPlan(vm, dialogs, i, item.ex, item.sets, item.reps, if (item.weight > 0) item.weight else last!!.weight)
                    else -> promptIdx = i
                }
            }, onLong = { i -> editIdx = i })
            HButton("+ Add exercise", { showAdd = true })
            HButton("Finish workout", { finish() }, style = BtnStyle.Primary)
        } else {
            val plan = planForDate(st)
            val doneToday = plan != null && todaysLog(st, plan.id) != null
            val o = st.overrides[dateKey()]
            val skipped = o?.skipped?.let { st.plan(it) }
            PageHead("Workout", fmtToday()) {
                Row(Modifier.clickable { history = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.history, "History", tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("  History", style = T.body)
                }
            }
            if (plan != null) {
                // Tap the day to swap it for another day from the plan.
                Row(Modifier.fillMaxWidth().clickable { showSwitch = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow(if (o != null) "Today · switched" else "Today")
                        Text(plan.name, style = T.big)
                        if (skipped != null) Text("Skipped ${skipped.name}", style = T.small)
                        Text("Tap to switch", style = T.small)
                    }
                    if (doneToday) Tag("Done ✓") else Text("›", style = TextStyle(fontSize = 22.sp, color = C.muted))
                }
                ItemList(st, plan.items.map { ActiveItem(it.ex, it.sets, it.reps, it.weight, it.note, ss = it.ss) }, false)
                HButton(if (doneToday) "Start again" else "Start workout", { start(plan.id) }, style = BtnStyle.Primary)
                HButton("Switch today's workout", { showSwitch = true })
            } else {
                val n = nextWorkout(st)
                Box(Modifier.fillMaxWidth().clickable { showSwitch = true }.padding(vertical = 24.dp)) {
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

/** Complete an item and save its weight to the plan by itself; only a sets/reps change asks before becoming permanent. */
private fun completeAndSaveToPlan(vm: AppViewModel, dialogs: DialogHost, idx: Int, exId: String, s: Int, r: Int, weight: Double) {
    vm.update { it.editItem(idx, s, r, weight, complete = true) }
    val now = vm.state.value
    val plan = now.plan(now.active?.planId) ?: return
    val pi = plan.items.firstOrNull { it.ex == exId } ?: return
    if (pi.weight != weight) vm.update { it.updatePlanItem(plan.id, exId, pi.sets, pi.reps, weight) }
    val ex = now.ex(exId) ?: return
    if (pi.sets != s || pi.reps != r) {
        dialogs.ask("Keep ${s} × ${r}?",
            "Change ${ex.name} from ${pi.sets} × ${pi.reps} to ${s} × ${r} for every \"${plan.name}\" workout?",
            DialogBtn("Yes, every time", primary = true) { vm.update { it.updatePlanItem(plan.id, exId, s, r, weight) } },
            DialogBtn("Just today"))
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
                completeAndSaveToPlan(vm, dialogs, idx, item.ex, item.sets, item.reps, weight)
            }, style = BtnStyle.Primary)
            // Bodyweight moves (pull-ups, dips) already count your bodyweight; anything else logs it as the weight.
            val bwWeight = if (ex.bw <= 0) st.settings.bodyweight else 0.0
            HButton(if (bwWeight > 0) "Bodyweight · ${fmtNum(bwWeight)} $unit" else "Bodyweight (no added weight)", {
                onDismiss()
                completeAndSaveToPlan(vm, dialogs, idx, item.ex, item.sets, item.reps, bwWeight)
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
        if (last != null) Muted("Last time: ${setText(st, ex, last.sets, last.reps, last.weight)} · ${fmtShort(last.at)}")
        if (ex.isCardio) Stepper(reps, { reps = it }, label = "Minutes", min = 1.0, max = 300.0)
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper(sets, { sets = it }, Modifier.weight(1f), "Sets", min = 1.0, max = 20.0)
                Stepper(reps, { reps = it }, Modifier.weight(1f), "Reps", min = 1.0, max = 100.0)
            }
            Stepper(weight, { weight = it }, label = weightLabel(st, ex), step = weightStep(st.settings))
            if (ex.pair) NoteBox(PAIR_NOTE)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton(if (item.done) "Save" else "Save & complete", {
                onDismiss()
                if (ex.isCardio) vm.update { it.editItem(idx, 1, reps.toInt(), 0.0, complete = true) }
                else completeAndSaveToPlan(vm, dialogs, idx, item.ex, sets.toInt(), reps.toInt(), weight)
            }, style = BtnStyle.Primary)
            HButton("Remove from this workout", { onDismiss(); vm.update { it.removeItem(idx) } }, style = BtnStyle.Danger)
        }
    }
}

@Composable
fun NoteBox(text: String) {
    Box(Modifier.fillMaxWidth().clip(Look.shape(10.dp)).border(Look.line, Color(0xFF333333), Look.shape(10.dp)).padding(10.dp)) {
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
        ExercisePicker(vm, st.exercises, exId, { id ->
            exId = id
            if (st.ex(id)?.isCardio == true) { sets = 1.0; reps = 20.0; weight = 0.0 }
            exerciseHistory(st, id).lastOrNull()?.let { sets = it.sets.toDouble(); reps = it.reps.toDouble(); weight = it.weight }
        })
        if (ex?.isCardio == true) Stepper(reps, { reps = it }, label = "Minutes", min = 1.0, max = 300.0)
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper(sets, { sets = it }, Modifier.weight(1f), "Sets", min = 1.0, max = 20.0)
                Stepper(reps, { reps = it }, Modifier.weight(1f), "Reps", min = 1.0, max = 100.0)
            }
            Stepper(weight, { weight = it }, label = weightLabel(st, ex), step = weightStep(st.settings))
            if (ex?.pair == true) NoteBox(PAIR_NOTE)
        }
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
