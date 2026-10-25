package com.wilfrombdn.heavynt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun state(days: List<String?>, start: String = "2026-10-19") = AppState(
        settings = Settings(bodyweight = 180.0, heightCm = 180.0),
        exercises = LIBRARY, plans = listOf(Plan("a", "A"), Plan("b", "B")),
        schedule = Schedule(start, days),
    )

    @Test fun weeklyScheduleRepeatsEveryTwoWeeksAcrossMonths() {
        val days = MutableList<String?>(14) { null }
        listOf("a", "b", "a", "b", "a").forEachIndexed { i, id -> days[2 + i] = id }
        val s = state(days)
        assertEquals("a", planForDate(s, "2026-10-23")?.id)  // Friday, week 1
        assertNull(planForDate(s, "2026-10-20"))              // Tuesday rest
        assertNull(planForDate(s, "2026-10-28"))              // week 2 off
        assertEquals("a", planForDate(s, "2026-11-04")?.id)   // two weeks later, next month
        assertEquals("a", planForDate(s, "2026-12-02")?.id)
        assertEquals(0, weekdayIdx("2026-10-19")); assertEquals("2026-10-19", mondayOf("2026-10-23"))
    }

    @Test fun switchingTodayOverridesOnlyToday() {
        val days = MutableList<String?>(14) { null }
        days[4] = "a"; days[5] = "b"
        val s = state(days).copy(overrides = mapOf("2026-10-23" to Override("b", "a")))
        assertEquals("b", planForDate(s, "2026-10-23")?.id)
        assertEquals("b", planForDate(s, "2026-10-24")?.id)
        assertEquals("a", scheduledPlan(s, "2026-10-23")?.id)
    }

    private fun lib(id: String) = LIBRARY.first { it.id == id }
    private fun near(expected: Double, actual: Double, pct: Double = 5.0) =
        assertTrue("expected ~$expected, got $actual", kotlin.math.abs(actual - expected) / expected * 100 <= pct)

    @Test fun targetsMatchPublishedStandards() {
        // strengthlevel.com intermediate 1RM tables (lb), no height so the BMI rule stays out of it
        val man = Settings(unit = "lb", bodyweight = 180.0, age = 30)
        val woman = man.copy(sex = "female", bodyweight = 140.0)
        near(220.0, targetFor(lib("bench"), man))
        near(183.0, targetFor(lib("bench"), man.copy(bodyweight = 150.0)))
        near(295.0, targetFor(lib("bench"), man.copy(bodyweight = 250.0)))
        near(298.0, targetFor(lib("squat"), man))
        near(348.0, targetFor(lib("deadlift"), man))
        near(140.0, targetFor(lib("ohp"), man))
        near(108.0, targetFor(lib("bench"), woman))
        near(131.0, targetFor(lib("bench"), woman.copy(bodyweight = 180.0)))
        near(174.0, targetFor(lib("squat"), woman.copy(bodyweight = 150.0)), 6.0)
        near(205.0, targetFor(lib("deadlift"), woman.copy(bodyweight = 150.0)), 6.0)
        near(89.0 * 2, targetFor(lib("db-bench"), man))  // dumbbells are entered as both added together
        // age curve: flat to 40, then down
        assertEquals(targetFor(lib("squat"), man), targetFor(lib("squat"), man.copy(age = 40)), 0.01)
        near(0.887, targetFor(lib("squat"), man.copy(age = 50)) / targetFor(lib("squat"), man), 1.0)
        // levels and BMI rule still behave
        assertTrue(targetFor(lib("bench"), man.copy(level = "advanced")) > targetFor(lib("bench"), man))
        val heavyShort = targetFor(lib("bench"), man.copy(bodyweight = 260.0, heightCm = 170.0))
        val heavyTall = targetFor(lib("bench"), man.copy(bodyweight = 260.0, heightCm = 195.0))
        assertTrue(heavyShort < heavyTall)
    }

    @Test fun womensBodyweightTargetsNeedAtLeastOneRep() {
        val woman = Settings(unit = "lb", bodyweight = 140.0, sex = "female")
        for (id in listOf("pullup", "chinup", "dip", "pushup")) {
            val ex = lib(id)
            assertTrue(id, targetFor(ex, woman) >= ex.bw * 140.0 * 1.1 - 0.01)
        }
    }

    @Test fun comparisonToOtherLifters() {
        val man = Settings(unit = "lb", bodyweight = 180.0)
        val bench = lib("bench")
        assertEquals(50, percentileFor(bench, man, targetFor(bench, man, "intermediate")))
        assertEquals(80, percentileFor(bench, man, targetFor(bench, man, "advanced")))
        assertTrue(percentileFor(bench, man, 60.0) < 5)
        assertEquals("Intermediate", levelReached(bench, man, targetFor(bench, man, "intermediate") + 1))
        assertEquals("Below beginner", levelReached(bench, man, 20.0))
    }

    @Test fun strongChestIsBlueWeakQuadsAreRed() {
        val now = nowIso()
        val s = state(List(14) { null }).copy(logs = listOf(Log("1", now, null, null, "x", listOf(
            Entry("bench", 3, 5, 270.0, now), Entry("squat", 3, 5, 95.0, now)))))
        val r = regionScores(s)
        assertTrue(r.getValue("chest").score > 1.2)
        assertTrue(r.getValue("quads").score < 0.7)
        assertNull(r["calves"])
        val (rr, _, rb) = scoreRGB(0.5); assertTrue(rr > rb)
        val (br, _, bb) = scoreRGB(1.0); assertTrue(bb > br)
    }

    @Test fun plateauAfterThreeSessionsAtSameWeightUnlessSnoozed() {
        val days = (1..3).map { n ->
            val at = java.time.Instant.now().minusSeconds(86400L * (n * 7)).toString()
            Log("l$n", at, null, null, "x", listOf(Entry("bench", 3, 8, 135.0, at)))
        }
        val s = state(List(14) { null }).copy(logs = days)
        assertEquals(3, plateauInfo(s, "bench", 135.0)?.sessions)
        assertNull(plateauInfo(s, "bench", 140.0))
        assertNull(plateauInfo(s.copy(snooze = mapOf("bench" to 3)), "bench", 135.0))
    }

    @Test fun pairedDumbbellLiftsUseCombinedWeight() {
        val db = LIBRARY.first { it.id == "db-bench" }
        assertTrue(db.pair)
        assertEquals(0.98, db.std, 0.0001) // 0.49 per dumbbell -> 0.98 combined
        assertEquals(0.62, db.stdF, 0.0001)
    }

    @Test fun supersetsGroupConsecutiveLinkedItems() {
        val g = supersetGroups(listOf(false, false, true, false, true, true, false))
        assertEquals(listOf(SsGroup(1, 2, 'A'), SsGroup(3, 3, 'B')), g)
        assertTrue(supersetGroups(listOf(false, false)).isEmpty())
    }

    @Test fun oldA2NotesBecomeSupersets() {
        val p = inferSupersets(Plan("p", "P", listOf(PlanItem("a"), PlanItem("b", note = "A1 · superset with c"), PlanItem("c", note = "A2 · rest"))))
        assertEquals(listOf(false, false, true), p.items.map { it.ss })
    }

    @Test fun weighInIsAskedOnceAMonthUnlessTheWeightWasJustUpdated() {
        val nov1 = java.time.LocalDate.parse("2026-11-01")
        val s = state(emptyList()).let { it.copy(settings = it.settings.copy(bodyweightSetAt = "2026-10-24")) }
        assertTrue(weighInDue(s, nov1))
        assertTrue(!weighInDue(s, java.time.LocalDate.parse("2026-10-31")))
        assertTrue(!weighInDue(s.copy(settings = s.settings.copy(weighInAsked = "2026-11")), nov1))
        assertTrue(!weighInDue(s.copy(settings = s.settings.copy(bodyweightSetAt = "2026-11-01")), nov1))
        assertTrue(!weighInDue(s.copy(settings = s.settings.copy(bodyweight = 0.0)), nov1))
    }

    @Test fun newPlanKeepsTheOldOneAndSwitchingBackRestoresIt() {
        val days = MutableList<String?>(7) { null }; days[0] = "a"; days[2] = "b"
        val old = state(days).copy(planName = "Old")
        val fresh = old.startNewPlan("New", copyCurrent = false)
        assertEquals("New", fresh.planName)
        assertTrue(fresh.plans.isEmpty() && fresh.schedule.days.all { it == null })
        assertEquals(listOf("Old"), fresh.saved.map { it.name })

        val back = fresh.switchToSaved(fresh.saved[0].id)
        assertEquals("Old", back.planName)
        assertEquals(old.plans, back.plans)
        assertEquals(old.schedule, back.schedule)
        assertEquals(listOf("New"), back.saved.map { it.name })

        val copy = old.startNewPlan("Copy", copyCurrent = true)
        assertEquals(listOf("A", "B"), copy.plans.map { it.name })
        assertTrue(copy.plans.none { it.id == "a" || it.id == "b" })  // own ids, so edits don't touch the saved one
        assertEquals(copy.plans[0].id, copy.schedule.days[0])
        assertEquals(copy.plans[1].id, copy.schedule.days[2])
    }

    @Test fun updatingRetagsBuiltInExercisesButNotCustomOnes() {
        val oldBench = LIBRARY.first { it.id == "bench" }.copy(secondary = listOf("shoulders", "triceps", "upperBack"))
        val mine = Exercise("c-1", "My Raise", "Shoulders", listOf("shoulders"), custom = true)
        val s = normalize(AppState(v = 5, exercises = listOf(oldBench, mine)))
        assertEquals(LIBRARY.first { it.id == "bench" }.secondary, s.ex("bench")!!.secondary)
        assertEquals(listOf("shoulders"), s.ex("c-1")!!.primary)
        assertTrue(LIBRARY.all { e -> (e.primary + e.secondary).all { it in REGIONS } })
    }

    @Test fun everyMuscleIsDrawnAndTrained() {
        for (female in listOf(false, true)) assertEquals("female=$female", REGIONS.keys, drawnRegions(female))
        val trained = LIBRARY.flatMap { it.primary }.toSet()
        assertEquals(emptySet<String>(), REGIONS.keys - trained)
        assertEquals(REGIONS.keys, REGION_GROUPS.flatMap { it.second }.toSet())
    }

    @Test fun demoLightsEveryMuscleDeepBlueOrDeepRed() {
        val upper = setOf("upperChest", "chest", "shoulders", "sideDelts", "rearDelts", "traps", "upperBack", "lats",
            "biceps", "triceps", "forearms", "abs", "obliques", "hipFlexors", "neck")
        for (sex in listOf("male", "female")) {
            val demo = demoState(AppState(settings = Settings(sex = sex)))
            val scores = regionScores(demo)
            assertEquals(sex, REGIONS.keys, scores.keys)
            for ((k, sc) in scores) {
                if (k in upper) assertTrue("$sex $k ${sc.score}", sc.score >= 1.5)
                else assertTrue("$sex $k ${sc.score}", sc.score <= 0.55)
            }
            assertTrue(demo.logs.size >= 20)
        }
    }
}
