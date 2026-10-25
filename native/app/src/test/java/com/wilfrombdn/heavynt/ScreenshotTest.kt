package com.wilfrombdn.heavynt

import android.app.Application
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        val dir = File(System.getProperty("shots") ?: "build/shots").apply { mkdirs() }
        val view = rule.activity.window.decorView
        val w = view.width.takeIf { it > 0 } ?: 1080
        val h = view.height.takeIf { it > 0 } ?: 2400
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tabs() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        // a profile and a few logged lifts so the map has colour
        val now = nowIso()
        vm.update { s ->
            s.copy(
                settings = s.settings.copy(bodyweight = 180.0, heightCm = 178.0, profileAsked = true),
                logs = listOf(Log("1", now, now, null, "Push", listOf(
                    Entry("bench", 3, 5, 225.0, now), Entry("incline-db", 3, 8, 140.0, now), Entry("squat", 3, 5, 135.0, now),
                    Entry("rdl", 3, 8, 225.0, now), Entry("pullup", 3, 10, 0.0, now), Entry("calf-raise", 3, 10, 300.0, now),
                    Entry("db-curl", 3, 8, 30.0, now)))),
            )
        }
        // one saved run with a route, to show the Run tab and a run's page
        val mPerDeg = 2 * Math.PI * 6_371_000.0 / 360
        val pts = (0..160).map { i -> RunPoint(49.28 + i * 15 / mPerDeg, -123.12 + (if (i > 80) (i - 80) * 6 / mPerDeg else 0.0), 20.0 + i % 7, i * 5000L + if (i > 80) 120_000 else 0, if (i > 80) 1 else 0) }
        val run = RunSummary("r1", nowIso(), nowIso(), trackDistanceM(pts), 800, elevationGainM(pts))
        RunFiles.save(app, run.id, pts)
        vm.update { it.copy(runs = listOf(run)) }
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.setContent { HeavyntTheme { App(vm) } }
        fun settle() { rule.waitForIdle() }
        settle()
        shot("launch")
        for ((tab, name) in listOf("Home" to "home", "Workout" to "workout", "Run" to "run", "Plans" to "plans", "Exercises" to "exercises")) {
            rule.onNodeWithText(tab).performClick(); settle(); shot(name)
            if (name == "workout") {
                rule.onNodeWithText("History", substring = true).performClick(); settle(); shot("history")
                rule.onNodeWithText("‹  Workout").performClick(); settle()
            }
            if (name == "run") {
                rule.onAllNodesWithText("km", substring = false)[0].performClick(); settle(); shot("run-detail")
                rule.onNodeWithText("Splits", ignoreCase = true).assertExists()
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
                rule.onNodeWithText("Start run").assertExists()
            }
            if (name == "plans") {
                // schedule: each week of the 2-week starter shows its own dates
                rule.onNodeWithText("Edit").performClick(); settle()
                val mon = mondayOf(dateKey())
                val w1 = rule.onAllNodesWithText("Week 1 · ", substring = true, ignoreCase = true).fetchSemanticsNodes()
                val w2 = rule.onAllNodesWithText("Week 2 · ", substring = true, ignoreCase = true).fetchSemanticsNodes()
                assertEquals(1, w1.size); assertEquals(1, w2.size)
                rule.onNodeWithText("${fmtDay(mon)} – ${fmtDay(addDays(mon, 6))} · this week", substring = true, ignoreCase = true).assertExists()
                rule.onNodeWithText("Week 2 · ${fmtDay(addDays(mon, 7))}", substring = true, ignoreCase = true).assertExists()
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
                if (rule.onAllNodesWithText("Current plan", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty()) { rule.onNodeWithText("Plans").performClick(); settle() }
                rule.onNodeWithText("Current plan", substring = true, ignoreCase = true).performClick(); settle()
                rule.onNodeWithText("+ Build a new plan").performClick(); settle()
                rule.onNodeWithText("e.g. Upper / Lower").performTextInput("Upper / Lower"); rule.onNodeWithText("Create plan").performClick(); settle(); shot("plans-new")
            }
            if (name == "exercises") {
                // sheets don't show in screenshots, so check their text exists instead
                rule.onNodeWithText("Barbell Bench Press").performClick(); settle()
                rule.onNodeWithText("How you compare", ignoreCase = true).assertExists()
                rule.onNodeWithText("Stronger than about", substring = true).assertExists()
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
                if (rule.onAllNodesWithText("+ Add exercise").fetchSemanticsNodes().isEmpty()) { rule.onNodeWithText("Exercises").performClick(); settle() }
                rule.onNodeWithText("+ Add exercise").performClick(); settle()
                // main and helper muscle pickers, both grouped by body area
                rule.onAllNodesWithText("Hips & legs", ignoreCase = true).assertCountEquals(2)
                rule.onAllNodesWithText("Inner thighs").assertCountEquals(2)
                rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle()
                if (rule.onAllNodesWithText("Olympic").fetchSemanticsNodes().isEmpty()) { rule.onNodeWithText("Exercises").performClick(); settle() }
                rule.onNodeWithText("Olympic").performScrollTo().performClick(); settle(); shot("exercises-open")
            }
        }
        vm.update { it.withSettings { x -> x.copy(sex = "female") } }
        rule.onNodeWithText("Home").performClick(); settle(); shot("home-female")
        vm.enterDemo(); settle(); shot("demo-female")
        vm.exitDemo(); vm.update { it.withSettings { x -> x.copy(sex = "male") } }; vm.enterDemo(); settle(); shot("demo-male")
        vm.exitDemo(); settle()
        assertEquals("Upper / Lower", vm.state.value.planName)  // real data came back
        // a run being recorded
        val clock = android.os.SystemClock.elapsedRealtime()
        RunTracker.live.value = LiveRun(nowIso(), clock - 754_000, pts.take(60), activeMs = 0, resumedAt = clock - 754_000, distanceM = trackDistanceM(pts.take(60)), gpsOk = true)
        rule.onNodeWithText("Run").performClick(); settle(); shot("run-live")
        rule.onNodeWithText("Pause").assertExists()
        RunTracker.clear(); settle()
        // the recap after finishing a workout
        FinishSummary.value = WorkoutSummary("Push", 62, 7, 12450.0, 10, listOf("Barbell Bench Press" to "230 lb (+5)"))
        settle()
        rule.onNodeWithText("Workout saved").assertExists()
        rule.onNodeWithText("230 lb (+5)").assertExists()
        FinishSummary.value = null; settle()
        // a workout in progress: first lift done, weights set, one beating last time
        val w = listOf(145.0, 120.0, 225.0, 140.0, 40.0, 60.0, 30.0)
        val day = STARTER_DAYS[0].let { d -> d.copy(items = d.items.mapIndexed { i, x -> x.copy(weight = w[i]) }) }
        vm.update { it.copy(plans = it.plans + day).startWorkout(day.id) }
        vm.update { it.toggleDone(0) }
        rule.onNodeWithText("Workout").performClick(); settle(); shot("workout-active")
    }
}