@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.wilfrombdn.heavynt

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val bg = Color.Black
    val panel = Color(0xFF0E0E0E)
    val line = Color(0xFF262626)
    val muted = Color(0xFF8A8A8A)
    val sub = Color(0xFFCFCFCF)
    val field = Color(0xFF111111)
    val fieldLine = Color(0xFF333333)
    val green = Color(0xFF22C55E)
    val greenSoft = Color(0xFF9FDCB4)
    val good = Color(0xFF6AAAFF)
    val goodF = Color(0xFFFF7AC8)
    val bad = Color(0xFFFF6B70)
    val sheet = Color(0xFF0B0B0B)
}

object T {
    val h1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = (-0.5).sp)
    val big = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val title = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    val body = TextStyle(fontSize = 15.sp, color = Color.White)
    val name = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    val sub = TextStyle(fontSize = 14.sp, color = C.sub)
    val muted = TextStyle(fontSize = 14.sp, color = C.muted)
    val small = TextStyle(fontSize = 12.sp, color = C.muted)
    val eyebrow = TextStyle(fontSize = 11.sp, color = C.muted, letterSpacing = 1.2.sp)
    val label = TextStyle(fontSize = 12.sp, color = C.muted)
}

@Composable
fun HeavyntTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Color.Black, background = Color.Black, onBackground = Color.White,
            surface = Color.Black, onSurface = Color.White, surfaceVariant = C.panel, outline = C.line,
        ),
        content = content,
    )
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ---------- layout atoms ----------
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) = Text(text.uppercase(), style = T.eyebrow, modifier = modifier)

@Composable
fun SectionTitle(text: String) = Text(text.uppercase(), style = T.eyebrow, modifier = Modifier.padding(top = 8.dp))

@Composable
fun HCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = C.line,
    bg: Color = C.panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(14.dp),
        content = content,
    )
}

@Composable
fun Tag(text: String) {
    Box(Modifier.border(1.dp, Color(0xFF555555), RoundedCornerShape(99.dp)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(text, style = T.small.copy(color = Color.White))
    }
}

enum class BtnStyle { Primary, Outline, Danger }

@Composable
fun HButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Outline, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    val (bgc, fg, bd) = when (style) {
        BtnStyle.Primary -> Triple(Color.White, Color.Black, Color.White)
        BtnStyle.Outline -> Triple(Color.Transparent, Color.White, Color(0xFF3A3A3A))
        BtnStyle.Danger -> Triple(Color.Transparent, C.bad, Color(0xFF3A2222))
    }
    Box(
        modifier.fillMaxWidth().height(50.dp).clip(shape).background(bgc).border(1.dp, bd, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = if (enabled) 1f else 0.4f))) }
}

@Composable
fun IconBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, C.line, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = TextStyle(fontSize = 16.sp, color = Color.White)) }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) = Text(text, style = T.muted, modifier = modifier)

// ---------- sheets & dialogs ----------
@Composable
fun Sheet(title: String?, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = C.sheet,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF333333)) },
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 760.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) Text(title, style = T.title)
            content()
        }
    }
}

class DialogBtn(val label: String, val primary: Boolean = false, val danger: Boolean = false, val onClick: () -> Unit = {})
class DialogSpec(val title: String, val message: String?, val buttons: List<DialogBtn>)

class DialogHost {
    var spec by mutableStateOf<DialogSpec?>(null)
    fun ask(title: String, message: String?, vararg buttons: DialogBtn) { spec = DialogSpec(title, message, buttons.toList()) }
}

val LocalDialogs = staticCompositionLocalOf { DialogHost() }

@Composable
fun DialogLayer() {
    val host = LocalDialogs.current
    val spec = host.spec ?: return
    Sheet(spec.title, onDismiss = { host.spec = null }) {
        if (spec.message != null) Muted(spec.message)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.buttons.forEach { b ->
                HButton(b.label, { host.spec = null; b.onClick() }, style = if (b.primary) BtnStyle.Primary else if (b.danger) BtnStyle.Danger else BtnStyle.Outline)
            }
        }
    }
}

// ---------- form controls ----------
@Composable
fun HTextField(
    value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, minLines: Int = 1, center: Boolean = false, big: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    // Tapping in selects everything, so typing replaces the old value; the tap's own cursor move lands just after focus, so keep the selection for a moment.
    var focusedAt by remember { mutableStateOf(0L) }
    BasicTextField(
        value = field, singleLine = singleLine, minLines = minLines,
        onValueChange = { v ->
            val justFocused = SystemClock.uptimeMillis() - focusedAt < 400
            field = if (justFocused && v.text == field.text && singleLine) v.copy(selection = TextRange(0, v.text.length)) else v
            if (v.text != value) onChange(v.text)
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        textStyle = TextStyle(
            fontSize = if (big) 18.sp else 15.sp, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal, color = Color.White,
            textAlign = if (center) TextAlign.Center else TextAlign.Start,
        ),
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.onFocusChanged { f ->
            if (!f.isFocused) focusedAt = 0L
            else if (singleLine && focusedAt == 0L) {
                field = field.copy(selection = TextRange(0, field.text.length)); focusedAt = SystemClock.uptimeMillis()
            }
        },
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth().clip(shape).background(C.field).border(1.dp, C.fieldLine, shape).padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = if (center) Alignment.Center else Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) Text(placeholder, style = T.muted.copy(fontSize = 15.sp))
                inner()
            }
        },
    )
}

@Composable
fun Labeled(label: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = T.label)
        content()
    }
}

@Composable
fun Stepper(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier, label: String? = null,
            min: Double = 0.0, max: Double = 9999.0, step: Double = 1.0) {
    var text by remember(value) { mutableStateOf(fmtNum(value)) }
    fun set(v: Double) { onChange(v.coerceIn(min, max)) }
    val inner: @Composable ColumnScope.() -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StepBtn("−") { set(value - step) }
            HTextField(text, { t -> text = t; t.toDoubleOrNull()?.let { set(it) } }, Modifier.weight(1f), keyboard = KeyboardType.Decimal, center = true, big = true)
            StepBtn("+") { set(value + step) }
        }
    }
    if (label != null) Labeled(label, modifier, inner) else Column(modifier, content = inner)
}

@Composable
private fun StepBtn(text: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(C.field).border(1.dp, C.fieldLine, RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, style = TextStyle(fontSize = 22.sp, color = Color.White)) }
}

/** Compact numeric field used in plan rows (no +/- buttons). */
@Composable
fun MiniNumber(label: String, value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(fmtNum(value)) }
    Labeled(label, modifier) {
        HTextField(text, { t -> text = t; onChange(t.toDoubleOrNull() ?: 0.0) }, keyboard = KeyboardType.Decimal, center = true)
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(modifier.clip(shape).border(1.dp, C.fieldLine, shape)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(Modifier.weight(1f).background(if (on) Color.White else Color.Transparent).clickable { onSelect(v) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center) {
                Text(label, style = TextStyle(fontSize = 14.sp, color = if (on) Color.Black else Color.White, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
            }
        }
    }
}

@Composable
fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = Color.Black, uncheckedColor = Color(0xFF666666)))
        Text(text, style = T.body, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Chips(selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        REGION_GROUPS.forEach { (area, keys) ->
            Text(area.uppercase(), style = T.eyebrow)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keys.forEach { k ->
                    val on = k in selected
                    Box(Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) Color.White else Color.Transparent)
                        .border(1.dp, if (on) Color.White else Color(0xFF3A3A3A), RoundedCornerShape(99.dp)).clickable { onToggle(k) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(REGIONS[k] ?: k, style = TextStyle(fontSize = 13.sp, color = if (on) Color.Black else Color(0xFFBBBBBB)))
                    }
                }
            }
        }
    }
}

// ---------- drop-down picker (themed bottom sheet) ----------
class Opt(val value: String, val label: String, val group: String? = null)

@Composable
fun PickerField(options: List<Opt>, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Choose…",
                onAddNew: ((String) -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val current = options.firstOrNull { it.value == value }
    Row(
        modifier.fillMaxWidth().clip(shape).background(C.field).border(1.dp, C.fieldLine, shape).clickable { open = true }.padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(current?.label ?: placeholder, style = TextStyle(fontSize = 15.sp, color = if (current == null) C.muted else Color.White), modifier = Modifier.weight(1f))
        Text("▾", style = TextStyle(fontSize = 14.sp, color = C.muted))
    }
    if (open) {
        Sheet(null, onDismiss = { open = false }) {
            var q by remember { mutableStateOf("") }
            val searchable = options.size > 12
            val shown = options.filter { it.label.contains(q.trim(), ignoreCase = true) }
            fun pick(o: Opt) { onChange(o.value); open = false }
            if (!searchable) {
                shown.forEach { o -> PickerRow(o, o.value == value) { pick(o) } }
            } else {
                HTextField(q, { q = it }, placeholder = "Search…")
                // Fixed height (half the screen) so the list doesn't jump around as results change.
                val listHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
                LazyColumn(Modifier.fillMaxWidth().height(listHeight)) {
                    if (shown.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Muted(if (onAddNew != null) "No exercise found" else "No matches")
                            if (onAddNew != null && q.isNotBlank()) HButton("+ Add \"${q.trim()}\"", { open = false; onAddNew(q.trim()) })
                        }
                    }
                    shown.forEachIndexed { i, o ->
                        if (o.group != null && o.group != shown.getOrNull(i - 1)?.group) item { SectionTitle(o.group) }
                        item { PickerRow(o, o.value == value) { pick(o) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(o: Opt, on: Boolean, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(o.label, style = TextStyle(fontSize = 16.sp, color = Color.White, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
            if (on) Icon(Icons.check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
    }
}

fun exerciseOptions(exercises: List<Exercise>): List<Opt> =
    CATEGORIES.flatMap { cat -> exercises.filter { it.cat == cat }.sortedBy { it.name }.map { Opt(it.id, it.name, cat) } } +
        exercises.filter { it.cat !in CATEGORIES }.sortedBy { it.name }.map { Opt(it.id, it.name, it.cat) }

/** Exercise drop-down; a search with no match offers to create the exercise, then picks it. */
@Composable
fun ExercisePicker(vm: AppViewModel, exercises: List<Exercise>, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val opts = remember(exercises) { exerciseOptions(exercises) }
    var newName by remember { mutableStateOf<String?>(null) }
    PickerField(opts, value, onChange, modifier, placeholder = "Choose exercise…", onAddNew = { newName = it })
    newName?.let { n -> ExerciseEditor(vm, null, initialName = n, onSaved = onChange) { newName = null } }
}

// ---------- icons ----------
private fun strokeIcon(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(d), stroke = SolidColor(Color.White), strokeLineWidth = 1.6f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
    ).build()

object Icons {
    val home = strokeIcon("home", "M9.8 5a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0M12 8v6m0 0-3 7m3-7 3 7M7 10h10")
    val workout = strokeIcon("workout", "M3 9v6M6 7v10M18 7v10M21 9v6M6 12h12")
    val plans = strokeIcon("plans", "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2H6a2 2 0 0 1 -2 -2V6a2 2 0 0 1 2 -2zM8 9h8M8 13h8M8 17h5")
    val exercises = strokeIcon("exercises", "M4 6h16M4 12h16M4 18h10")
    val history = strokeIcon("history", "M4 12a8 8 0 1 0 3-6.2M4 4v4h4M12 8v4l3 2")
    val gear = strokeIcon("gear", "M15 12a3 3 0 1 0 -6 0a3 3 0 1 0 6 0M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8")
    val check = strokeIcon("check", "M5 12.5l4.5 4.5L19 7.5")
}


fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
fun Modifier.androidxBorder(): Modifier = this.border(1.dp, C.line, RoundedCornerShape(12.dp))
