package com.wilfrombdn.heavynt

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
        )
        openFrom(intent)
        setContent { HeavyntTheme { App(vm) } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** The run notification opens straight to the Run tab. */
    private fun openFrom(intent: android.content.Intent?) {
        if (intent?.getStringExtra(RunService.EXTRA_OPEN) == "run") OpenTab.value = TAB_RUN
    }
}

const val TAB_RUN = 2
/** The recap shown after finishing a workout. */
val FinishSummary = kotlinx.coroutines.flow.MutableStateFlow<WorkoutSummary?>(null)

/** A tab something outside the screens (like the run notification) asked to show. */
val OpenTab = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

private class TabDef(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val TABS = listOf(
    TabDef("Home", Icons.home), TabDef("Workout", Icons.workout), TabDef("Run", Icons.run), TabDef("Plans", Icons.plans),
    TabDef("Exercises", Icons.exercises),
)

/** Launch on the workout tab when one is running or due today. */
private fun initialTab(st: AppState): Int {
    if (RunTracker.live.value != null) return TAB_RUN
    if (st.active != null) return 1
    val plan = planForDate(st)
    return if (plan != null && todaysLog(st, plan.id) == null && st.settings.openOnLaunch) 1 else 0
}

@Composable
fun App(vm: AppViewModel) {
    val st by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val dialogs = remember { DialogHost() }
    var tab by rememberSaveable { mutableIntStateOf(initialTab(vm.state.value)) }
    var settings by remember { mutableStateOf(false) }
    var profile by remember { mutableStateOf(false) }

    BackHandler(enabled = tab != 0) { tab = 0 }
    val asked by OpenTab.collectAsStateWithLifecycle()
    val liveRun by RunTracker.live.collectAsStateWithLifecycle()
    LaunchedEffect(asked) { asked?.let { tab = it; OpenTab.value = null } }

    // New day on the phone's clock (12:01 am, or coming back to the app on a later day): redraw everything so
    // today's plan and the water/protein card move on to the new date even if the app stayed open.
    var day by remember { mutableStateOf(intakeDayKey() + dateKey()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisToNextRollover() + 1000)
            day = intakeDayKey() + dateKey()
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) day = intakeDayKey() + dateKey() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // First run: ask to allow notifications (workout reminders), then for sex and body details. Nothing else.
    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.update { it.withSettings { x -> x.copy(reminderOn = true) } }
        profile = true
    }
    LaunchedEffect(Unit) {
        if (st.settings.bodyweight <= 0 && !st.settings.profileAsked) {
            val needsAsk = android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            if (needsAsk) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS) else profile = true
        }
    }

    // New month: ask once to update the bodyweight, unless it was already changed this month.
    LaunchedEffect(Unit) {
        if (weighInDue(st)) {
            vm.update { it.withSettings { x -> x.copy(weighInAsked = dateKey().take(7)) } }
            dialogs.ask("New month: weigh in", "Your bodyweight is ${fmtNum(st.settings.bodyweight)} ${st.settings.unit}. Update it so your strength targets stay accurate.",
                DialogBtn("Update weight", primary = true) { profile = true }, DialogBtn("Still the same") {
                    vm.update { it.withSettings { x -> x.copy(bodyweightSetAt = dateKey()) } }
                })
        }
    }

    // Rest timer alarm follows the workout's rest end time.
    LaunchedEffect(st.active?.restUntil) { RestAlarm.sync(ctx, st.active?.restUntil ?: 0) }

    // Automatic backup to Downloads/Heavynt, a few seconds after anything changes.
    LaunchedEffect(st) {
        delay(8000)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AutoBackup.write(ctx, st) }
    }

    // Keep reminders in sync with the schedule.
    LaunchedEffect(st.schedule, st.plans, st.logs, st.overrides, st.settings.reminderOn, st.settings.reminderTime) {
        delay(1500)
        Reminders.schedule(ctx, st)
    }

    CompositionLocalProvider(LocalDialogs provides dialogs) {
        Scaffold(
            containerColor = Color.Black,
            bottomBar = {
                NavigationBar(containerColor = Color.Black) {
                    TABS.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = {
                                if ((i == 1 && st.active != null) || (i == TAB_RUN && liveRun != null)) BadgedBox(badge = { Badge(containerColor = Color.White) }) { Icon(t.icon, t.label) }
                                else Icon(t.icon, t.label)
                            },
                            label = { Text(t.label, style = TextStyle(fontSize = 11.sp)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White, selectedTextColor = Color.White,
                                unselectedIconColor = C.muted, unselectedTextColor = C.muted,
                                indicatorColor = Color(0xFF1C1C1C),
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) { androidx.compose.runtime.key(day) {
                when (tab) {
                    0 -> HomeScreen(st, vm, go = { tab = it }, openSettings = { settings = true }, openProfile = { profile = true })
                    1 -> WorkoutScreen(st, vm, goHome = { tab = 0 })
                    TAB_RUN -> RunScreen(st, vm)
                    3 -> PlansScreen(st, vm)
                    else -> ExercisesScreen(st, vm)
                }
            } }
        }
        if (settings) SettingsSheet(st, vm, onDismiss = { settings = false }, openProfile = { profile = true })
        if (profile) ProfileSheet(st, vm, onDismiss = { profile = false })
        val summary by FinishSummary.collectAsStateWithLifecycle()
        summary?.let { SummarySheet(st, it) { FinishSummary.value = null } }
        DialogLayer()
    }
}
