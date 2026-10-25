@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wilfrombdn.heavynt

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.FlowRow

import android.Manifest
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.roundToInt

private val DAY3 = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private fun weekLabel(idx: Int, len: Int) = DAY3[idx % 7] + if (len > 7) " · week ${idx / 7 + 1} of ${len / 7}" else ""

@Composable
fun HomeScreen(st: AppState, vm: AppViewModel, go: (Int) -> Unit, openSettings: () -> Unit, openProfile: () -> Unit) {
    var selected by remember { mutableStateOf<String?>(null) }
    val scores = remember(st.logs, st.settings, st.exercises) { if (st.settings.bodyweight > 0) regionScores(st) else emptyMap() }
    val hasData = scores.isNotEmpty()
    val plan = planForDate(st)
    val idx = cycleIndex(st.schedule)
    val len = st.schedule.days.size

    Screen(head = { PageHead("Strength", fmtToday()) { IconBtnVec(Icons.gear, openSettings) } }) {
        Spacer(Modifier.height(8.dp))
        if (st.demoBackup != null) HCard(borderColor = Color(0xFFFFB020)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Developer demo")
                    Muted("Example data. Your real data is safe and comes back when you exit.")
                }
                SlimButton("Exit") { vm.exitDemo() }
            }
        }

        // Today
        val a = st.active
        if (a != null) {
            HItem(topLine = true) { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Eyebrow("In progress"); Text(a.planName, style = T.big) }
                SlimButton("Resume", { go(1) })
            } }
        } else if (plan != null) {
            val done = todaysLog(st, plan.id) != null
            HItem(topLine = true) { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Today · ${weekLabel(idx, len)}")
                    Text(plan.name, style = T.big)
                    Muted("${plan.items.size} exercises${if (st.overrides[dateKey()] != null) " · switched" else ""}")
                }
                if (done) Tag("Done ✓") else SlimButton("Start", { vm.update { it.startWorkout(plan.id) }; go(1) })
            } }
        } else {
            val n = nextWorkout(st)
            HItem(topLine = true) {
                Eyebrow("Rest day${if (len > 0) " · ${weekLabel(idx, len)}" else ""}")
                Muted(if (n != null) "Next: ${n.second.name} on ${fmtLongDay(n.first)}" else "Nothing scheduled")
            }
        }

        // Body map
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth()) {
            BodyMap(scores, selected, { selected = if (selected == it) null else it }, female = st.settings.sex == "female")
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Below avg", style = T.small)
                val f = st.settings.sex == "female"
                fun c(t: Triple<Int, Int, Int>) = Color(t.first, t.second, t.third)
                Box(Modifier.weight(1f).height(6.dp).clip(Look.shape(3.dp)).background(
                    if (f) Brush.horizontalGradient(0f to c(RED_RGB), 1f to c(PINK_RGB))
                    else Brush.horizontalGradient(0f to c(RED_RGB), scoreT(1.0).toFloat() to c(MID_RGB), 1f to c(BLUE_RGB))))
                Text("Strong", style = T.small)
            }
        }

        if (st.settings.bodyweight > 0) IntakeCard(st, vm)

        if (st.settings.bodyweight <= 0) {
            HItem(onClick = openProfile) {
                Text("Set up your profile", style = T.name)
                Muted("Add your age, height and weight so the map knows what you should be lifting. Tap to start.")
            }
        }

        val sel = selected
        if (sel != null) RegionCard(st, sel, scores[sel]) else if (hasData) Summary(scores, goodColor(st))
    }
}

private val WATER = Color(0xFF4FA8FF)

/** Today's water (tap glasses) and protein (tap the bar to add 10 g), against targets from the profile. */
@Composable
private fun IntakeCard(st: AppState, vm: AppViewModel) {
    val s = st.settings
    val t = st.todayIntake()
    val glasses = glassesTarget(s)
    val proteinGoal = proteinTargetG(s)
    val step = STANDARDS.nutrition.proteinStepG.toInt()
    HItem {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Water", Modifier.weight(1f))
            Text(waterLabel(s, t.water), style = T.small)
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..maxOf(glasses, t.water)) Glass(i <= t.water) { vm.update { it.tapGlass(i) } }
        }
        Text("Tap a glass when you drink one (${if (s.unit == "lb") "8 oz" else "250 ml"}). Tap the last full one to undo.", style = T.small, modifier = Modifier.padding(top = 6.dp))

        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Protein", Modifier.weight(1f))
            Text("${t.protein} / $proteinGoal g", style = T.small)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val frac = if (proteinGoal > 0) (t.protein.toFloat() / proteinGoal).coerceIn(0f, 1f) else 0f
            Box(Modifier.weight(1f).height(28.dp).clip(Look.shape(14.dp)).background(Color(0xFF1A1A1A))
                .clickableNoRipple { vm.update { it.addProtein(step) } }) {
                Box(Modifier.fillMaxWidth(frac).height(28.dp).clip(Look.shape(14.dp)).background(goodColor(st)))
                Text("+$step g", style = TextStyle(fontSize = 13.sp, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.Center))
            }
            IconBtn("−", { vm.update { it.addProtein(-step) } })
        }
    }
}

@Composable
private fun Glass(full: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp, 30.dp).clickableNoRipple(onClick)) {
        val w = size.width; val h = size.height; val inset = w * 0.12f
        val cup = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w - inset, h); lineTo(inset, h); close()
        }
        if (full) {
            val top = h * 0.22f
            val water = androidx.compose.ui.graphics.Path().apply {
                moveTo(inset * top / h, top); lineTo(w - inset * top / h, top); lineTo(w - inset, h); lineTo(inset, h); close()
            }
            drawPath(water, WATER.copy(alpha = 0.85f))
        }
        drawPath(cup, if (full) WATER else Color(0xFF5A5A5A), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}

@Composable
fun IconBtnVec(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(Look.shape(12.dp)).clickableNoRipple(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SlimButton(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(Look.shape(14.dp)).background(Color.White).clickableNoRipple(onClick).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, style = TextStyle(fontSize = 15.sp, color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

@Composable
private fun RegionCard(st: AppState, key: String, sc: RegionScore?) {
    HItem {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(REGIONS[key] ?: key, style = T.big, modifier = Modifier.weight(1f))
            Tag(if (sc != null) scoreLabel(sc.score) else "No data")
        }
        if (sc == null) { Muted("No logged lifts hit this area in the last 90 days."); return@HItem }
        Muted("${(sc.score * 100).roundToInt()}% of your target")
        val u = st.settings.unit
        sc.lifts.take(5).forEach { l ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(l.name + if (l.primary) "" else " (assist)", style = T.body)
                    Text("est. max ${l.e1rm.roundToInt()} $u · target ${l.target.roundToInt()} $u", style = T.small)
                    l.ex?.let { e -> Text("average for you ${targetFor(e, st.settings, "intermediate").roundToInt()} $u · beats ~${percentileFor(e, st.settings, l.e1rm)}% of lifters", style = T.small) }
                }
                Text("${(l.score * 100).roundToInt()}%", style = TextStyle(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (l.score >= 1) goodColor(st) else C.bad))
            }
        }
    }
}

@Composable
private fun Summary(scores: Map<String, RegionScore>, good: Color) {
    val sorted = scores.entries.sortedBy { it.value.score }
    val behind = sorted.filter { it.value.score < 1 }.take(4).joinToString(", ") { REGIONS[it.key] ?: it.key }
    val ok = sorted.filter { it.value.score >= 1 }.reversed().take(4).joinToString(", ") { REGIONS[it.key] ?: it.key }
    val missing = REGIONS.filterKeys { it !in scores }.values.toList()
    val untrained = if (missing.size <= 5) missing.joinToString(", ") else missing.take(4).joinToString(", ") + " + ${missing.size - 4} more"
    HItem {
        if (behind.isNotEmpty()) SummaryLine("Behind target", behind, C.bad)
        if (ok.isNotEmpty()) SummaryLine("On target or better", ok, good)
        if (untrained.isNotEmpty()) SummaryLine("Untrained", untrained, Color.White)
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = T.muted)
        Text(value, style = TextStyle(fontSize = 14.sp, color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// ---------------- profile ----------------
private val LEVEL_INFO = mapOf(
    "beginner" to "Beginner: just starting out",
    "novice" to "Novice: about 6 months of training",
    "intermediate" to "Intermediate: a couple of solid years",
    "advanced" to "Advanced: serious, multi-year lifter",
    "elite" to "Elite: competitive strength athlete",
)

fun profileSummary(s: Settings): String {
    if (s.bodyweight <= 0) return "Not set up yet"
    val imperial = s.unit == "lb"
    val ht = if (s.heightCm > 0) {
        if (imperial) { val inches = s.heightCm / 2.54; " · ${floor(inches / 12).toInt()}'${(inches % 12).roundToInt() % 12}\"" } else " · ${s.heightCm.roundToInt()} cm"
    } else ""
    return "${if (s.sex == "female") "Female" else "Male"} · ${s.age} · ${fmtNum(s.bodyweight)} ${s.unit}$ht · ${s.level}"
}

@Composable
fun ProfileSheet(st: AppState, vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val s = st.settings
    val imperial = s.unit == "lb"
    var sex by remember { mutableStateOf(s.sex) }
    var age by remember { mutableStateOf(s.age.toDouble()) }
    var bw by remember { mutableStateOf(s.bodyweight) }
    val startCm = if (s.heightCm > 0) s.heightCm else if (imperial) 177.8 else 175.0
    var ft by remember { mutableStateOf(floor(startCm / 2.54 / 12)) }
    var inch by remember { mutableStateOf((startCm / 2.54 % 12).roundToInt().toDouble().coerceAtMost(11.0)) }
    var cm by remember { mutableStateOf(startCm.roundToInt().toDouble()) }
    var level by remember { mutableStateOf(s.level) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun markAsked() { if (!s.profileAsked) vm.update { it.withSettings { x -> x.copy(profileAsked = true) } } }
    Sheet("Your profile", onDismiss = { markAsked(); onDismiss() }) {
        Muted("Targets are worked out from your sex, age, height and weight, then compared to what you actually lift. Under target shows red, on or above target shows blue (pink on the female body).")
        Labeled("Sex") { Segmented(listOf("male" to "Male", "female" to "Female"), sex, { sex = it }) }
        Stepper(age, { age = it }, label = "Age", min = 12.0, max = 90.0)
        Stepper(bw, { bw = it }, label = "Bodyweight (${s.unit})", max = 700.0)
        if (imperial) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper(ft, { ft = it }, Modifier.weight(1f), "Height (ft)", min = 3.0, max = 8.0)
            Stepper(inch, { inch = it }, Modifier.weight(1f), "Height (in)", min = 0.0, max = 11.0)
        } else Stepper(cm, { cm = it }, label = "Height (cm)", min = 120.0, max = 230.0)
        Labeled("Where should your strength be?") {
            PickerField(LEVELS.map { Opt(it, LEVEL_INFO[it] ?: it) }, level, { level = it })
            Text("Intermediate = the average lifter with your sex, age and weight.", style = T.small)
        }
        HButton("Save", {
            if (bw <= 0) toast(ctx, "Enter your bodyweight") else {
                val h = if (imperial) Math.round((ft * 12 + inch) * 2.54 * 10) / 10.0 else cm
                vm.update { it.withSettings { x -> x.copy(sex = sex, age = age.toInt(), bodyweight = bw, heightCm = h, level = level, profileAsked = true, bodyweightSetAt = dateKey()) } }
                // The monthly weigh-in reminder is a notification, so ask for permission once the weight is in.
                if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                toast(ctx, "Profile saved"); onDismiss()
            }
        }, style = BtnStyle.Primary)
    }
}

fun goodColor(st: AppState): Color = if (st.settings.sex == "female") C.goodF else C.good

// ---------------- settings ----------------
@Composable
fun SettingsSheet(st: AppState, vm: AppViewModel, onDismiss: () -> Unit, openProfile: () -> Unit) {
    val ctx = LocalContext.current
    val dialogs = LocalDialogs.current
    val s = st.settings
    var restore by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Sheet("Settings", onDismiss) {
        Labeled("Units") {
            Segmented(listOf("lb" to "lb", "kg" to "kg"), s.unit, { to ->
                if (to != s.unit) dialogs.ask("Convert saved weights?", "Switch to $to. Convert your plans, history and bodyweight too?",
                    DialogBtn("Convert everything", primary = true) { vm.update { it.switchUnit(to, true) } },
                    DialogBtn("Just change the label") { vm.update { it.switchUnit(to, false) } })
            })
        }
        HItem(onClick = { onDismiss(); openProfile() }) {
            Eyebrow("Profile & targets"); Text(profileSummary(s), style = T.body)
        }
        Stepper(s.plateauSessions.toDouble(), { v -> vm.update { it.withSettings { x -> x.copy(plateauSessions = v.toInt()) } } },
            label = "Suggest a heavier weight after this many sessions at the same weight", min = 2.0, max = 12.0)
        SwitchRow("Open to today's workout on launch", s.openOnLaunch) { v -> vm.update { it.withSettings { x -> x.copy(openOnLaunch = v) } } }
        SwitchRow("Remind me on workout days", s.reminderOn) { v ->
            if (v && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            vm.update { it.withSettings { x -> x.copy(reminderOn = v) } }
        }
        Labeled("Reminder time") {
            HCard(onClick = {
                val (h, m) = s.reminderTime.split(":").map { it.toInt() }
                TimePickerDialog(ctx, { _, hh, mm -> vm.update { it.withSettings { x -> x.copy(reminderTime = "%02d:%02d".format(hh, mm)) } } }, h, m, false).show()
            }) { Text(s.reminderTime, style = T.body) }
        }
        SectionTitle("Backup")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HButton("Copy backup", {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Heavyn't backup", vm.exportBackup()))
                toast(ctx, "Backup copied to clipboard")
            }, Modifier.weight(1f))
            HButton("Restore", { restore = true }, Modifier.weight(1f))
        }
        HButton("Erase all data", {
            dialogs.ask("Erase everything?", "All workouts, plans and settings are deleted from this device.",
                DialogBtn("Erase all data", primary = true) { vm.eraseAll(); onDismiss() }, DialogBtn("Cancel"))
        }, style = BtnStyle.Danger)
        SectionTitle("Developer")
        if (st.demoBackup == null) HButton("Load demo data", {
            dialogs.ask("Load demo data?", "Shows an example lifter with a filled-in body map (very strong upper body, very weak legs), a demo plan and 8 weeks of history. Your real data is set aside and comes back when you exit the demo.",
                DialogBtn("Load demo", primary = true) { vm.enterDemo(); onDismiss() }, DialogBtn("Cancel"))
        }) else HButton("Exit demo (bring back my data)", { vm.exitDemo(); onDismiss() }, style = BtnStyle.Primary)
        Text("Strength scores are estimates: your best set in the last 90 days → estimated 1-rep max, compared to a target for your profile.", style = T.small)
        HButton("Done", onDismiss, style = BtnStyle.Primary)
    }
    if (restore) RestoreSheet(vm) { restore = false }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = T.body, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black,
            uncheckedTrackColor = Color(0xFF222222), uncheckedThumbColor = Color(0xFF888888), uncheckedBorderColor = Color(0xFF444444)))
    }
}

@Composable
private fun RestoreSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    Sheet("Restore backup", onDismiss) {
        Muted("Paste a backup from this app, or from the old web version of Heavyn't. It replaces your current data.")
        HButton("Paste from clipboard", {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            text = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
        })
        HTextField(text, { text = it }, placeholder = "Paste backup text here", singleLine = false, minLines = 6)
        HButton("Restore (replaces current data)", {
            vm.importBackup(text).onSuccess { toast(ctx, "Backup restored"); onDismiss() }.onFailure { toast(ctx, "That is not a valid backup") }
        }, style = BtnStyle.Primary)
    }
}
