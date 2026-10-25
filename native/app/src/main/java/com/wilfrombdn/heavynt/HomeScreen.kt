package com.wilfrombdn.heavynt

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
import androidx.compose.foundation.shape.RoundedCornerShape
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

    Screen {
        PageHead("Strength", fmtToday()) { IconBtnVec(Icons.gear, openSettings) }

        // Today
        val a = st.active
        if (a != null) {
            HCard { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Eyebrow("In progress"); Text(a.planName, style = T.big) }
                SlimButton("Resume", { go(1) })
            } }
        } else if (plan != null) {
            val done = todaysLog(st, plan.id) != null
            HCard { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Today · ${weekLabel(idx, len)}")
                    Text(plan.name, style = T.big)
                    Muted("${plan.items.size} exercises${if (st.overrides[dateKey()] != null) " · switched" else ""}")
                }
                if (done) Tag("Done ✓") else SlimButton("Start", { vm.update { it.startWorkout(plan.id) }; go(1) })
            } }
        } else {
            val n = nextWorkout(st)
            HCard {
                Eyebrow("Rest day${if (len > 0) " · ${weekLabel(idx, len)}" else ""}")
                Muted(if (n != null) "Next: ${n.second.name} on ${fmtLongDay(n.first)}" else "Nothing scheduled")
            }
        }

        // Body map
        HCard(Modifier, bg = Color(0xFF0B0B0D)) {
            BodyMap(scores, selected, { selected = if (selected == it) null else it }, female = st.settings.sex == "female")
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Under target", style = T.small)
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(
                    0f to Color(240, 64, 70), 0.48f to Color(150, 62, 66), 0.52f to Color(58, 118, 230), 1f to Color(110, 175, 255))))
                Text("On / above", style = T.small)
            }
        }

        if (st.settings.bodyweight <= 0) {
            HCard(onClick = openProfile) {
                Text("Set up your profile", style = T.name)
                Muted("Add your age, height and weight so the map knows what you should be lifting. Tap to start.")
            }
        } else if (!hasData) {
            HCard { Muted("Finish a workout with weights and the map colours in: red = under your target, blue = on target or better.") }
        }

        val sel = selected
        if (sel != null) RegionCard(st, sel, scores[sel]) else if (hasData) Summary(scores)
    }
}

@Composable
fun IconBtnVec(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.Transparent)
        .then(Modifier.androidxBorder()).clickableNoRipple(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SlimButton(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).clickableNoRipple(onClick).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(text, style = TextStyle(fontSize = 15.sp, color = Color.Black, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

@Composable
private fun RegionCard(st: AppState, key: String, sc: RegionScore?) {
    HCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(REGIONS[key] ?: key, style = T.big, modifier = Modifier.weight(1f))
            Tag(if (sc != null) scoreLabel(sc.score) else "No data")
        }
        if (sc == null) { Muted("No logged lifts hit this area in the last 90 days."); return@HCard }
        Muted("${(sc.score * 100).roundToInt()}% of your target")
        val u = st.settings.unit
        sc.lifts.take(5).forEach { l ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(l.name + if (l.primary) "" else " (assist)", style = T.body)
                    Text("est. max ${l.e1rm.roundToInt()} $u · target ${l.target.roundToInt()} $u", style = T.small)
                }
                Text("${(l.score * 100).roundToInt()}%", style = TextStyle(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (l.score >= 1) C.good else C.bad))
            }
        }
    }
}

@Composable
private fun Summary(scores: Map<String, RegionScore>) {
    val sorted = scores.entries.sortedBy { it.value.score }
    val behind = sorted.filter { it.value.score < 1 }.take(4).joinToString(", ") { REGIONS[it.key] ?: it.key }
    val ok = sorted.filter { it.value.score >= 1 }.reversed().take(4).joinToString(", ") { REGIONS[it.key] ?: it.key }
    val untrained = REGIONS.filterKeys { it !in scores }.values.joinToString(", ")
    HCard {
        if (behind.isNotEmpty()) SummaryLine("Behind target", behind, C.bad)
        if (ok.isNotEmpty()) SummaryLine("On target or better", ok, C.good)
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
        Muted("Targets are worked out from your sex, age, height and weight, then compared to what you actually lift. Under target shows red, on or above target shows blue.")
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
        HCard(onClick = { onDismiss(); openProfile() }) {
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
