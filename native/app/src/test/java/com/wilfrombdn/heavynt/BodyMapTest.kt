package com.wilfrombdn.heavynt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BodyMapTest {
    @Test fun musclesDontOverlap() {
        for (female in listOf(false, true)) assertEquals("female=$female", emptyList<Triple<String, String, Float>>(), regionOverlaps(female))
    }

    @Test fun eachMuscleOnTheRightSide() {
        for (female in listOf(false, true)) {
            val (front, back) = viewRegions(female)
            assertTrue("tibialis" in front && "tibialis" !in back)    // shins: front only
            assertTrue("calves" in back && "calves" !in front)        // calves: back only
            assertTrue("abductors" in back && "abductors" !in front)  // outer hips: back only
            assertTrue("adductors" in front && "adductors" !in back)  // inner thighs: front only
        }
    }
}
