package de.dvld.heartthrum

import org.junit.Assert.*
import org.junit.Test

class LiveBpmDisplayTest {
    @Test fun firstEstimateAppearsImmediately() {
        val display = LiveBpmDisplay()
        assertEquals(74, display.update(123_456, 73.8))
    }

    @Test fun holdsTheNumberForOneSecondThenUsesTheLatestEstimate() {
        val display = LiveBpmDisplay()
        assertEquals(73, display.update(0, 73.49))
        for (time in 1L..999L) {
            assertEquals(73, display.update(time, 73.51))
        }
        assertEquals(75, display.update(1_000, 75.1))
        assertEquals(75, display.update(1_999, 76.2))
        assertEquals(76, display.update(2_000, 76.2))
    }

    @Test fun signalLossClearsImmediatelyAndReacquisitionDoesNotWait() {
        val display = LiveBpmDisplay()
        assertEquals(74, display.update(1_000, 74.0))
        assertNull(display.update(1_010, null))
        assertNull(display.update(1_020, null))
        assertEquals(80, display.update(1_030, 80.0))
        assertEquals(80, display.update(2_029, 81.0))
        assertEquals(81, display.update(2_030, 81.0))
    }

    @Test fun resetDoesNotCarryThePreviousValueOrDeadlineIntoANewMeasurement() {
        val display = LiveBpmDisplay()
        assertEquals(74, display.update(10_000, 74.0))
        display.reset()
        assertEquals(80, display.update(10_001, 80.0))
        assertEquals(80, display.update(11_000, 82.0))
        assertEquals(82, display.update(11_001, 82.0))
    }

    @Test fun longPausesPublishOnlyTheNewestValueWithoutCatchingUp() {
        val display = LiveBpmDisplay()
        assertEquals(74, display.update(0, 74.0))
        assertEquals(90, display.update(10_000, 90.0))
        assertEquals(90, display.update(10_001, 91.0))
        assertEquals(90, display.update(10_999, 92.0))
        assertEquals(93, display.update(11_000, 93.0))
    }

    @Test fun unchangedNumbersStillRespectThePublicationCadence() {
        val display = LiveBpmDisplay()
        assertEquals(74, display.update(0, 73.8))
        assertEquals(74, display.update(1_000, 74.2))
        assertEquals(74, display.update(1_001, 75.0))
        assertEquals(75, display.update(2_000, 75.0))
    }

    @Test fun queuedSamplesAtTheSameWallClockTimeDoNotReplayIntermediateValues() {
        val display = LiveBpmDisplay()
        assertEquals(74, display.update(0, 74.0))
        assertEquals(80, display.update(1_000, 80.0))
        for (bpm in 81..100) {
            assertEquals(80, display.update(1_000, bpm.toDouble()))
        }
        assertEquals(90, display.update(2_000, 90.0))
    }

    @Test fun supportedFrameRatesNeverChangeTheNumberMoreThanOncePerSecond() {
        for (fps in listOf(15, 24, 30, 60)) {
            val display = LiveBpmDisplay()
            var previous: Int? = null
            var lastChangeAt: Long? = null
            var changes = 0
            for (frame in 0..10 * fps) {
                val time = frame * 1_000L / fps
                val shown = display.update(time, 60.0 + frame.toDouble() / fps)
                assertEquals("$fps FPS at $time ms", 60 + (time / 1_000).toInt(), shown)
                if (shown != previous) {
                    lastChangeAt?.let {
                        assertTrue("$fps FPS changed after ${time - it} ms", time - it >= 1_000)
                        changes++
                    }
                    lastChangeAt = time
                    previous = shown
                }
            }
            assertEquals("$fps FPS changes over ten seconds", 10, changes)
        }
    }
}
