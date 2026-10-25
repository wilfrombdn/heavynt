package com.wilfrombdn.heavynt

import android.app.Application
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
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
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        rule.setContent { HeavyntTheme { App(vm) } }
        fun settle() { rule.waitForIdle() }
        settle()
        shot("launch")
        for ((tab, name) in listOf("Home" to "home", "Workout" to "workout", "Plans" to "plans", "Exercises" to "exercises", "History" to "history")) {
            rule.onNodeWithText(tab).performClick(); settle(); shot(name)
            if (name == "plans") {
                rule.onNodeWithText("Current plan", substring = true, ignoreCase = true).performClick(); settle()
                rule.onNodeWithText("+ Build a new plan").performClick(); settle()
                rule.onNodeWithText("e.g. Upper / Lower").performTextInput("Upper / Lower"); rule.onNodeWithText("Create plan").performClick(); settle(); shot("plans-new")
            }
            if (name == "exercises") { rule.onNodeWithText("Olympic").performScrollTo().performClick(); settle(); shot("exercises-open") }
        }
    }
}