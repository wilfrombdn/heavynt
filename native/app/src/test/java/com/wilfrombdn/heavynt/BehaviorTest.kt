package com.wilfrombdn.heavynt

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class BehaviorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun vmWithActiveWorkout(): AppViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        android.provider.Settings.Global.putFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val vm = AppViewModel(app)
        vm.update { s -> s.copy(settings = s.settings.copy(bodyweight = 180.0, profileAsked = true)).startWorkout(s.plans.first().id) }
        return vm
    }

    @Test
    fun tapCompletesAndHoldEditsThenCompletes() {
        val vm = vmWithActiveWorkout()
        val items = vm.state.value.active!!.items
        val first = vm.state.value.ex(items[0].ex)!!.name
        val second = vm.state.value.ex(items[1].ex)!!.name
        rule.setContent { HeavyntTheme { App(vm) } }
        rule.waitForIdle()

        rule.onNodeWithText(first).performClick()
        rule.waitForIdle()
        assertTrue("tap should complete the first exercise", vm.state.value.active!!.items[0].done)
        rule.onNodeWithText(first).performClick()
        rule.waitForIdle()
        assertTrue("tapping again should undo it", !vm.state.value.active!!.items[0].done)

        rule.onNodeWithText(second).performTouchInput { longClick() }
        rule.waitForIdle()
        assertTrue("hold must not complete by itself", !vm.state.value.active!!.items[1].done)
        rule.onNodeWithText("Save & complete").assertExists()
        rule.onNodeWithText("Save & complete").performClick()
        rule.waitForIdle()
        assertTrue("saving the edit completes it", vm.state.value.active!!.items[1].done)
    }

    @Test
    fun finishingSavesALogWithDateAndTime() {
        val vm = vmWithActiveWorkout()
        vm.update { it.toggleDone(0).finishWorkout() }
        val s = vm.state.value
        assertEquals(null, s.active)
        assertEquals(1, s.logs.size)
        assertTrue(s.logs[0].start.endsWith("Z"))
        assertEquals(1, s.logs[0].entries.size)
    }

    @Test
    fun importsABackupFromTheWebApp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = AppViewModel(app)
        val web = """
        {"v":4,"settings":{"unit":"lb","bodyweight":182,"heightCm":177.8,"age":31,"sex":"male","level":"intermediate","plateauSessions":3,
          "reminderOn":false,"reminderTime":"07:00","profileAsked":true,"openOnLaunch":true},
         "exercises":[{"id":"bench","name":"Barbell Bench Press","cat":"Chest","primary":["chest"],"secondary":["shoulders","triceps"],"std":1.25,"bw":0,"custom":false,"pair":false},
                      {"id":"c-1","name":"My Lift","cat":"Arms","primary":["biceps"],"secondary":[],"std":0.5,"bw":0,"pair":false,"custom":true}],
         "plans":[{"id":"p1","name":"Push","items":[{"ex":"bench","sets":5,"reps":5,"weight":185,"note":"Heavy"}]}],
         "schedule":{"start":"2026-10-19","days":[null,null,"p1",null,null,null,null,null,null,null,null,null,null,null]},
         "logs":[{"id":"l1","start":"2026-10-21T18:00:00.000Z","end":"2026-10-21T19:00:00.000Z","planId":"p1","planName":"Push",
                  "entries":[{"ex":"bench","sets":5,"reps":5,"weight":185,"at":"2026-10-21T18:30:00.000Z"}]}],
         "active":null,"snooze":{"bench":3},"overrides":{"2026-10-23":{"plan":null,"skipped":"p1"}}}
        """.trimIndent()
        val r = vm.importBackup(web)
        assertTrue(r.exceptionOrNull()?.toString(), r.isSuccess)
        val s = vm.state.value
        assertEquals(182.0, s.settings.bodyweight, 0.0)
        assertEquals(1, s.logs.size)
        assertEquals("Heavy", s.plans[0].items[0].note)
        assertEquals("p1", s.overrides["2026-10-23"]?.skipped)
        assertTrue("library additions merged in", s.exercises.size > 100)
        assertTrue("custom exercise kept", s.ex("c-1")?.custom == true)
        assertTrue(vm.importBackup("not json").isFailure)
    }
}
