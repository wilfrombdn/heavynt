package com.wilfrombdn.heavynt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunMathTest {
    private val mPerDegLat = 2 * Math.PI * 6_371_000.0 / 360

    /** Heading north at [speed] m/s, a fix every [stepSec] s. */
    private fun line(metres: Double, speed: Double = 3.0, stepSec: Int = 5, startT: Long = 0, seg: Int = 0, startM: Double = 0.0): List<RunPoint> {
        val n = (metres / (speed * stepSec)).toInt()
        return (0..n).map { i ->
            val m = startM + i * speed * stepSec
            RunPoint(49.0 + m / mPerDegLat, -123.0, 0.0, startT + i * stepSec * 1000L, seg)
        }
    }

    @Test fun straightKilometre() {
        val pts = line(1500.0)
        assertEquals(1500.0, trackDistanceM(pts), 5.0)
        val splits = kmSplits(pts)
        assertEquals(2, splits.size)
        assertEquals(1000.0 / 3, splits[0].seconds, 1.0)   // 3 m/s → 5:33 per km
        assertEquals(500.0, splits[1].metres, 5.0)
        assertEquals("5:33", fmtPace(paceSecPerKm(1000.0, 1000.0 / 3)))
    }

    @Test fun pauseIsNotDistanceOrTime() {
        // 600 m, stop for 5 minutes and walk 200 m away while paused, then 600 m more
        val a = line(600.0)
        val b = line(600.0, startT = a.last().t + 300_000, seg = 1, startM = 800.0)
        val pts = a + b
        assertEquals(1200.0, trackDistanceM(pts), 5.0)
        val splits = kmSplits(pts)
        assertEquals(1000.0 / 3, splits[0].seconds, 2.0)  // the 5-minute pause isn't in the split
    }

    @Test fun filterDropsBadFixes() {
        val f = TrackFilter()
        val p = line(100.0)
        assertFalse(f.accept(p[0], 50f))   // poor accuracy
        assertFalse(f.accept(p[0], 5f))    // warming up
        assertFalse(f.accept(p[1], 5f))
        assertTrue(f.accept(p[2], 5f))
        // a 500 m jump in 5 seconds is a GPS spike
        val spike = p[3].copy(lat = p[3].lat + 500 / mPerDegLat)
        assertFalse(f.accept(spike, 5f))
        assertTrue(f.accept(p[3], 5f))
        // standing still: a 1 m wobble isn't kept
        assertFalse(f.accept(p[3].copy(t = p[3].t + 5000, lat = p[3].lat + 1 / mPerDegLat), 5f))
    }

    @Test fun currentPaceAndStanding() {
        val pts = line(300.0)
        assertEquals(1000.0 / 3, currentPaceSecPerKm(pts)!!, 2.0)
        val still = pts + pts.last().copy(t = pts.last().t + 40_000)
        assertNull(currentPaceSecPerKm(still))
        assertEquals("--:--", fmtPace(null))
    }

    @Test fun elevationIgnoresNoise() {
        val base = line(200.0)
        val wobble = base.mapIndexed { i, p -> p.copy(alt = 100.0 + if (i % 2 == 0) 2.0 else -2.0) }
        assertEquals(0.0, elevationGainM(wobble), 0.01)
        val climb = base.mapIndexed { i, p -> p.copy(alt = 100.0 + i * 1.0) }
        assertEquals((base.size - 1).toDouble(), elevationGainM(climb), 5.0)
    }

    @Test fun clockAndKm() {
        assertEquals("5:07", fmtClock(307))
        assertEquals("1:02:03", fmtClock(3723))
        assertEquals("5.24", fmtKm(5240.0))
    }
}
