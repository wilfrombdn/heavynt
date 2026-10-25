package com.wilfrombdn.heavynt

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun HistoryScreen(st: AppState, vm: AppViewModel, onBack: () -> Unit) {
    var open by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf<String?>(null) }
    var runId by remember { mutableStateOf<String?>(null) }
    val dialogs = LocalDialogs.current
    runId?.let { id ->
        androidx.activity.compose.BackHandler { runId = null }
        androidx.compose.runtime.key(id) { RunDetail(st, vm, id, onBack = { runId = null }) }
        return
    }

    Screen {
        Row(Modifier.clickable(onClick = onBack).padding(vertical = 4.dp)) { Text("‹  Workout", style = T.muted) }
        PageHead("History")
        if (st.logs.isEmpty() && st.runs.isEmpty()) Muted("No workouts yet. Finish one and it shows up here with the date and time.")
        // workouts and runs together, newest first
        val items: List<Pair<Long, Any>> = st.logs.map { isoMillis(it.start) to it } + st.runs.map { isoMillis(it.start) to it }
        Column { items.sortedByDescending { it.first }.forEach { (_, item) ->
            if (item is RunSummary) { RunRow(item) { runId = item.id }; return@forEach }
            val log = item as Log
            val isOpen = log.id in open
            HItem(onClick = { open = if (isOpen) open - log.id else open + log.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(log.planName, style = T.name); Text(fmtDate(log.start), style = T.small) }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${log.entries.size} exercises", style = T.small)
                        if (log.end != null) Text(fmtDuration(log.start, log.end), style = T.small)
                    }
                }
                if (isOpen) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        log.entries.forEach { e ->
                            val ex = st.ex(e.ex)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(ex?.name ?: "Removed exercise", style = T.body, modifier = Modifier.weight(1f))
                                Text(setText(st, ex, e.sets, e.reps, e.weight), style = T.small)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                            HButton("Edit", { editing = log.id }, Modifier.weight(1f))
                            HButton("Delete", {
                                dialogs.ask("Delete workout?", "This also removes it from your strength map.",
                                    DialogBtn("Delete", primary = true) { vm.update { it.copy(logs = it.logs.filter { l -> l.id != log.id }) } }, DialogBtn("Cancel"))
                            }, Modifier.weight(1f), style = BtnStyle.Danger)
                        }
                    }
                }
            }
        } }
    }
    editing?.let { id -> st.logs.firstOrNull { it.id == id }?.let { EditLogSheet(st, vm, it) { editing = null } } }
}

private class DraftEntry(ex: String, sets: Double, reps: Double, weight: Double, val at: String?) {
    var ex by mutableStateOf(ex)
    var sets by mutableStateOf(sets)
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)
}

@Composable
private fun EditLogSheet(st: AppState, vm: AppViewModel, log: Log, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(log.planName) }
    var whenLdt by remember { mutableStateOf(LocalDateTime.ofInstant(Instant.parse(log.start), ZoneId.systemDefault())) }
    val rows = remember { mutableStateListOf<DraftEntry>().apply { addAll(log.entries.map { DraftEntry(it.ex, it.sets.toDouble(), it.reps.toDouble(), it.weight, it.at) }) } }

    Sheet("Edit workout", onDismiss) {
        Labeled("Name") { HTextField(name, { name = it }) }
        Labeled("Date & time") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HCard(Modifier.weight(1f), onClick = {
                    DatePickerDialog(ctx, { _, y, m, d -> whenLdt = whenLdt.withYear(y).withMonth(m + 1).withDayOfMonth(d) }, whenLdt.year, whenLdt.monthValue - 1, whenLdt.dayOfMonth).show()
                }) { Text(fmtLongDay(whenLdt.toLocalDate().toString()), style = T.body) }
                HCard(Modifier.weight(1f), onClick = {
                    TimePickerDialog(ctx, { _, h, mi -> whenLdt = whenLdt.withHour(h).withMinute(mi) }, whenLdt.hour, whenLdt.minute, false).show()
                }) { Text("%d:%02d %s".format(if (whenLdt.hour % 12 == 0) 12 else whenLdt.hour % 12, whenLdt.minute, if (whenLdt.hour < 12) "AM" else "PM"), style = T.body) }
            }
        }
        SectionTitle("Exercises")
        rows.forEachIndexed { i, r ->
            val ex = st.ex(r.ex)
            Column(Modifier.fillMaxWidth().border(Look.line, C.line, Look.shape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExercisePicker(vm, st.exercises, r.ex, { v -> r.ex = v })
                if (ex?.pair == true) NoteBox("Weight is both dumbbells combined. $PAIR_NOTE")
                if (ex?.isCardio == true) MiniNumber("Minutes", r.reps, { r.sets = 1.0; r.reps = it })
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniNumber("Sets", r.sets, { r.sets = it }, Modifier.weight(1f))
                    MiniNumber("Reps", r.reps, { r.reps = it }, Modifier.weight(1f))
                    MiniNumber(if (ex?.pair == true) "Total (${st.settings.unit})" else "Weight (${st.settings.unit})", r.weight, { r.weight = it }, Modifier.weight(1.4f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconBtn("✕", { rows.removeAt(i) }) }
            }
        }
        HButton("+ Add exercise", { rows.add(DraftEntry("", 3.0, 10.0, 0.0, null)) })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HButton("Save changes", {
                val entries = rows.filter { it.ex.isNotEmpty() }
                if (entries.isEmpty()) { toast(ctx, "Add at least one exercise"); return@HButton }
                val newStart = whenLdt.atZone(ZoneId.systemDefault()).toInstant()
                val shift = newStart.toEpochMilli() - isoMillis(log.start)
                val updated = log.copy(
                    start = newStart.toString(),
                    end = log.end?.let { Instant.ofEpochMilli(isoMillis(it) + shift).toString() },
                    planName = name.trim().ifEmpty { "Workout" },
                    entries = entries.map { Entry(it.ex, it.sets.toInt().coerceAtLeast(1), it.reps.toInt().coerceAtLeast(1), it.weight, Instant.ofEpochMilli(isoMillis(it.at ?: log.start) + shift).toString()) },
                )
                vm.update { s -> s.copy(logs = s.logs.map { if (it.id == log.id) updated else it }) }
                toast(ctx, "Workout updated"); onDismiss()
            }, style = BtnStyle.Primary)
            HButton("Cancel", onDismiss)
        }
    }
}
