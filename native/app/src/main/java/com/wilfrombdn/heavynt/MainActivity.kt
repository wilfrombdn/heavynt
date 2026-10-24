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
        setContent { HeavyntTheme { App(vm) } }
    }
}

private class TabDef(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val TABS = listOf(
    TabDef("Home", Icons.home), TabDef("Workout", Icons.workout), TabDef("Plans", Icons.plans),
    TabDef("Exercises", Icons.exercises), TabDef("History", Icons.history),
)

/** Launch on the workout tab when one is running or due today. */
private fun initialTab(st: AppState): Int {
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

    // First run: ask for the profile so the body map has something to compare against.
    LaunchedEffect(Unit) { if (st.settings.bodyweight <= 0 && !st.settings.profileAsked) profile = true }

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
                                if (i == 1 && st.active != null) BadgedBox(badge = { Badge(containerColor = Color.White) }) { Icon(t.icon, t.label) }
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
            Box(Modifier.padding(pad)) {
                when (tab) {
                    0 -> HomeScreen(st, vm, go = { tab = it }, openSettings = { settings = true }, openProfile = { profile = true })
                    1 -> WorkoutScreen(st, vm, goHome = { tab = 0 })
                    2 -> PlansScreen(st, vm)
                    3 -> ExercisesScreen(st, vm)
                    else -> HistoryScreen(st, vm)
                }
            }
        }
        if (settings) SettingsSheet(st, vm, onDismiss = { settings = false }, openProfile = { profile = true })
        if (profile) ProfileSheet(st, vm, onDismiss = { profile = false })
        DialogLayer()
    }
}
