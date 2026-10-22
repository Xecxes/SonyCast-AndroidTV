package io.github.jqssun.airplay.ui

import org.junit.Assert.*
import org.junit.Test

class SeekAccelerationTest {
    @Test fun firstTapAlwaysHasNormalSpeed() {
        val step = SeekAcceleration().press(22, 100, 100)!!
        assertEquals(1, step.multiplier)
        assertFalse(step.repeated)
    }

    @Test fun holdsCrossExactTwoThreeFourFiveSecondThresholdsAndCapAtFive() {
        listOf(1999L to 1, 2000L to 2, 2999L to 2, 3000L to 3,
            4000L to 4, 5000L to 5, 30000L to 5).forEach { (elapsed, expected) ->
            val ramp = SeekAcceleration()
            ramp.press(22, 100, 100)
            assertEquals("held $elapsed ms", expected, ramp.press(22, 100, 100 + elapsed)!!.multiplier)
        }
    }

    @Test fun repeatFloodIsThrottledWithoutRestartingTheHoldTimer() {
        val ramp = SeekAcceleration()
        ramp.press(22, 0, 0)
        assertNull(ramp.press(22, 0, 100))
        assertNotNull(ramp.press(22, 0, 250))
        assertNull(ramp.press(22, 0, 400))
        assertEquals(2, ramp.press(22, 0, 2000)!!.multiplier)
    }

    @Test fun releaseThenAnotherPressOfSameKeyStartsAtOne() {
        val ramp = SeekAcceleration()
        ramp.press(22, 0, 0)
        assertEquals(5, ramp.press(22, 0, 5000)!!.multiplier)
        assertEquals(5, ramp.release(22, 0)!!.multiplier)
        assertEquals(1, ramp.press(22, 6000, 6000)!!.multiplier)
    }

    @Test fun changingDirectionResetsAndOldKeyUpCannotCancelNewHold() {
        val ramp = SeekAcceleration()
        ramp.press(22, 0, 0)
        ramp.press(22, 0, 5000)
        assertEquals(1, ramp.press(21, 5100, 5100)!!.multiplier)
        assertNull(ramp.release(22, 0))
        assertEquals(2, ramp.press(21, 5100, 7100)!!.multiplier)
    }

    @Test fun newDownTimeResetsEvenWhenOldKeyUpWasLost() {
        val ramp = SeekAcceleration()
        ramp.press(22, 0, 0)
        ramp.press(22, 0, 5000)
        assertEquals(1, ramp.press(22, 6000, 6000)!!.multiplier)
        assertNull(ramp.release(22, 0))
        assertEquals(2, ramp.press(22, 6000, 8000)!!.multiplier)
    }

    @Test fun focusOrMediaResetStartsLateRepeatsAtNormalSpeed() {
        val ramp = SeekAcceleration()
        ramp.press(22, 0, 0)
        ramp.press(22, 0, 5000)
        ramp.reset()
        assertEquals(1, ramp.press(22, 0, 8000)!!.multiplier)
        assertEquals(2, ramp.press(22, 0, 10000)!!.multiplier)
    }

    @Test fun staleTimestampDoesNotMoveOrRestartActiveHold() {
        val ramp = SeekAcceleration()
        ramp.press(22, 100, 100)
        ramp.press(22, 100, 2100)
        assertNull(ramp.press(22, 100, 2000))
        assertNull(ramp.press(22, 5000, 4000))
        assertEquals(3, ramp.press(22, 100, 3100)!!.multiplier)
    }
}
