package com.wilfrombdn.heavynt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExercisesScreen(st: AppState, vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Exercise?>(null) }
    var creating by remember { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    Screen {
        PageHead("Exercises")
        HTextField(query, { query = it }, placeholder = "Search exercises")
        HButton("+ Add exercise", { creating = true }, style = BtnStyle.Primary)
        val q = query.trim()
        val hists = st.exercises.associate { it.id to exerciseHistory(st, it.id) }
        val row: @Composable (Exercise) -> Unit = { ex -> ExerciseRow(st, ex, hists[ex.id].orEmpty()) { detail = ex.id } }
        if (q.isNotEmpty()) {
            // Searching: show every match, grouped, so nothing hides inside a closed folder.
            for (cat in st.exercises.map { it.cat }.distinct()) {
                val exs = st.exercises.filter { it.cat == cat && it.name.contains(q, ignoreCase = true) }.sortedBy { it.name }
                if (exs.isEmpty()) continue
                SectionTitle(cat)
                exs.forEach { row(it) }
            }
        } else {
            val mine = st.exercises.filter { hists[it.id].orEmpty().isNotEmpty() }.sortedByDescending { hists[it.id]!!.last().at }
            if (mine.isNotEmpty()) {
                SectionTitle("Your lifts")
                mine.forEach { row(it) }
            }
            SectionTitle("Browse all ${st.exercises.size}")
            st.exercises.map { it.cat }.distinct().chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { cat ->
                        val exs = st.exercises.filter { it.cat == cat }
                        val logged = exs.count { hists[it.id].orEmpty().isNotEmpty() }
                        HCard(Modifier.weight(1f), onClick = { open = if (open == cat) null else cat }, borderColor = if (open == cat) Color.White else C.line) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat, style = T.name, modifier = Modifier.weight(1f))
                                Text("${exs.size}", style = T.muted)
                            }
                            Text(if (logged > 0) "$logged logged" else "Tap to open", style = T.small)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
                pair.firstOrNull { it == open }?.let { cat -> st.exercises.filter { it.cat == cat }.sortedBy { it.name }.forEach { row(it) } }
            }
        }
    }

    detail?.let { id -> st.ex(id)?.let { ExerciseDetail(st, vm, it, onEdit = { editing = it; detail = null }, onDismiss = { detail = null }) } }
    editing?.let { ExerciseEditor(vm, it) { editing = null } }
    if (creating) ExerciseEditor(vm, null) { creating = false }
}

@Composable
private fun ExerciseRow(st: AppState, ex: Exercise, hist: List<HistEntry>, onClick: () -> Unit) {
    val last = hist.lastOrNull()
    HCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = T.name)
                Text(if (last != null) "${fmtNum(last.weight)} ${st.settings.unit}${if (ex.pair) " total" else ""} · ${fmtShort(last.at)}" else "Not logged yet", style = T.small)
            }
            if (hist.size > 1) Spark(hist.map { it.weight })
        }
    }
}

@Composable
private fun Spark(points: List<Double>) {
    val min = points.min(); val max = points.max(); val r = if (max - min == 0.0) 1.0 else max - min
    Canvas(Modifier.size(56.dp, 18.dp)) {
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - ((v - min) / r * (size.height - 4) + 2).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFFAAAAAA), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun WeightChart(points: List<HistEntry>, unit: String) {
    val pts = if (points.size > 1) points else listOf(points[0], points[0])
    val ws = pts.map { it.weight }
    val min = ws.min(); val max = ws.max(); val r = if (max - min == 0.0) 1.0 else max - min
    val t0 = isoMillis(pts.first().at); val t1 = isoMillis(pts.last().at); val tr = if (t1 - t0 == 0L) 1L else t1 - t0
    Column {
        Text("${fmtNum(max)} $unit", style = T.small)
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 6.dp)) {
            val pad = 8.dp.toPx()
            val xy = pts.map {
                Offset(pad + ((isoMillis(it.at) - t0).toFloat() / tr) * (size.width - 2 * pad),
                    size.height - pad - ((it.weight - min) / r).toFloat() * (size.height - 2 * pad))
            }
            val path = Path().apply { xy.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, Color.White, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            xy.forEach { drawCircle(Color.Black, 3.5.dp.toPx(), it); drawCircle(Color.White, 3.5.dp.toPx(), it, style = Stroke(1.4.dp.toPx())) }
        }
        Text(fmtNum(min), style = T.small)
    }
}

@Composable
private fun ExerciseDetail(st: AppState, vm: AppViewModel, ex: Exercise, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val dialogs = LocalDialogs.current
    val hist = exerciseHistory(st, ex.id)
    Sheet(ex.name, onDismiss) {
        Muted("${ex.cat} · ${ex.primary.joinToString(", ") { REGIONS[it] ?: it }}")
        if (hist.isNotEmpty()) {
            WeightChart(hist, st.settings.unit)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted("Best weight"); Text("${fmtNum(hist.maxOf { it.weight })} ${st.settings.unit}", style = T.body) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Muted("Sessions"); Text("${hist.size}", style = T.body) }
            SectionTitle("History")
            hist.reversed().take(30).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtDate(it.at), style = T.small, modifier = Modifier.weight(1f))
                    Text("${it.sets} × ${it.reps} @ ${fmtNum(it.weight)} ${st.settings.unit}${if (ex.pair) " total" else ""}", style = T.body.copy(fontSize = 13.sp))
                }
            }
        } else Muted("No sets logged yet.")
        HButton("Edit exercise", onEdit)
        if (ex.custom) HButton("Delete exercise", {
            dialogs.ask("Delete exercise?", "Past workouts keep their records but plans using it will lose it.",
                DialogBtn("Delete", primary = true) { vm.update { it.deleteExercise(ex.id) }; onDismiss() }, DialogBtn("Cancel"))
        }, style = BtnStyle.Danger)
    }
}

@Composable
private fun ExerciseEditor(vm: AppViewModel, ex: Exercise?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(ex?.name ?: "") }
    var cat by remember { mutableStateOf(ex?.cat ?: "Chest") }
    var prim by remember { mutableStateOf(ex?.primary?.toSet() ?: emptySet()) }
    var sec by remember { mutableStateOf(ex?.secondary?.toSet() ?: emptySet()) }
    var std by remember { mutableStateOf(if ((ex?.std ?: 0.0) > 0) fmtNum(ex!!.std) else "") }
    var pair by remember { mutableStateOf(ex?.pair ?: false) }
    var bw by remember { mutableStateOf((ex?.bw ?: 0.0) > 0) }
    Sheet(if (ex == null) "New exercise" else "Edit exercise", onDismiss) {
        Labeled("Name") { HTextField(name, { name = it }, placeholder = "e.g. Incline Cable Fly") }
        Labeled("Category") { PickerField(CATEGORIES.map { Opt(it, it) }, cat, { cat = it }) }
        Labeled("Main muscles worked") { Chips(prim) { k -> prim = if (k in prim) prim - k else prim + k } }
        Labeled("Helper muscles") { Chips(sec) { k -> sec = if (k in sec) sec - k else sec + k } }
        Labeled("Intermediate lift (× bodyweight, optional)") {
            HTextField(std, { std = it }, keyboard = KeyboardType.Decimal, placeholder = "Auto (${DEFAULT_STD[cat]}× bodyweight)")
            Text("How heavy a solid intermediate lifts this for one rep. Used to colour the body map.", style = T.small)
        }
        CheckRow(pair, { pair = it }, "Done with a pair of dumbbells (enter both added together)")
        CheckRow(bw, { bw = it }, "Bodyweight movement (pull-ups, dips…): weight is extra added load")
        HButton("Save", {
            when {
                name.isBlank() -> toast(ctx, "Give it a name")
                prim.isEmpty() -> toast(ctx, "Pick at least one main muscle")
                else -> {
                    val built = (ex ?: Exercise("c-" + uid(), "", cat, custom = true)).copy(
                        name = name.trim(), cat = cat, primary = prim.toList(), secondary = (sec - prim).toList(),
                        std = std.toDoubleOrNull()?.takeIf { it > 0 } ?: DEFAULT_STD[cat] ?: 1.0,
                        bw = if (bw) (ex?.bw?.takeIf { it > 0 } ?: 1.0) else 0.0, pair = pair,
                    )
                    vm.update { it.upsertExercise(built) }
                    toast(ctx, if (ex == null) "Exercise added" else "Saved"); onDismiss()
                }
            }
        }, style = BtnStyle.Primary)
    }
}
