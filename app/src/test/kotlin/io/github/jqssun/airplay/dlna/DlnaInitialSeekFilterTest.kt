package io.github.jqssun.airplay.dlna

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaInitialSeekFilterTest {
    @Test fun initialZeroDoesNotReplayOpeningButNextRewindIsHonored() {
        val filter = DlnaInitialSeekFilter()
        filter.onPlaybackStarted(10000)
        assertFalse(filter.shouldApply(0, 1990, 12000, true))
        assertTrue(filter.shouldApply(0, 2500, 12500, true))
    }

    @Test fun nonzeroInitialResumeIsAlwaysHonored() {
        val filter = DlnaInitialSeekFilter()
        filter.onPlaybackStarted(10000)
        assertTrue(filter.shouldApply(15000, 1349, 11500, true))
        assertTrue(filter.shouldApply(0, 15000, 11600, true))
    }

    @Test fun pausedInitialSeekIsHonored() {
        val filter = DlnaInitialSeekFilter()
        filter.onPlaybackStarted(10000)
        assertTrue(filter.shouldApply(0, 1000, 11500, false))
    }

    @Test fun seekBeforePlaybackIsHonored() {
        assertTrue(DlnaInitialSeekFilter().shouldApply(0, 0, 10000, false))
    }

    @Test fun firstRewindAfterStartupIsHonored() {
        val filter = DlnaInitialSeekFilter()
        filter.onPlaybackStarted(10000)
        assertTrue(filter.shouldApply(0, 3100, 13100, true))
    }

    @Test fun earlyPositionAfterLongBufferDoesNotExtendStartupWindow() {
        val filter = DlnaInitialSeekFilter()
        filter.onPlaybackStarted(10000)
        filter.onPlaybackStarted(20000)
        assertTrue(filter.shouldApply(0, 1000, 21000, true))
    }
}
