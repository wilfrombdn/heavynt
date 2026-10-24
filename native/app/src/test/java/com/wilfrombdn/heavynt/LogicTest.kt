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

    @Test fun targetsMatchTheWebVersion() {
        val bench = LIBRARY.first { it.id == "bench" }
        val base = Settings(unit = "lb", bodyweight = 180.0, heightCm = 180.0)
        val t = targetFor(bench, base)
        assertEquals(1.25 * 82 * 2.20462, t, 15.0)
        assertTrue(targetFor(bench, base.copy(sex = "female")) < t)
        assertTrue(targetFor(bench, base.copy(age = 55)) < t)
        assertTrue(targetFor(bench, base.copy(level = "advanced")) > t)
        val heavyShort = targetFor(bench, base.copy(bodyweight = 260.0, heightCm = 170.0))
        val heavyTall = targetFor(bench, base.copy(bodyweight = 260.0, heightCm = 195.0))
        assertTrue(heavyShort < heavyTall)
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
        assertEquals(1.0, db.std, 0.0001) // 0.5 per dumbbell -> 1.0 combined
    }
}
