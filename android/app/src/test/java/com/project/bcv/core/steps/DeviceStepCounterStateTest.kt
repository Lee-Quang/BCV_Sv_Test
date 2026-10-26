package com.project.bcv.core.steps

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceStepCounterStateTest {
    private fun started() = DeviceStepCounterState().forJourney(1, 100, 0)
    private fun walkedFour() = started().reading(4_000, 7, 1, 100).reading(4_004, 7, 2, 101)

    @Test fun firstReadingDoesNotCountStepsTakenBeforeTracking() {
        assertEquals(0L, started().reading(4_000, 7, 1, 100).totalSteps)
        assertEquals(4L, walkedFour().totalSteps)
    }

    @Test fun repeatedAndOutOfOrderEventsDoNotAddStepsTwice() {
        val state = walkedFour()
        assertEquals(4L, state.reading(4_004, 7, 3, 102).totalSteps)
        assertEquals(state, state.reading(8_000, 7, 1, 102))
    }

    @Test fun restoredStateContinuesFromTheLastPersistedHardwareReading() {
        val persisted = walkedFour().copy()
        assertEquals(12L, persisted.reading(4_012, 7, 3, 103).totalSteps)
    }

    @Test fun rebootKeepsPreviousProgressAndCountsStepsSinceTheNewBoot() {
        assertEquals(7L, walkedFour().reading(3, 8, 1, 200).totalSteps)
    }

    @Test fun unexpectedHardwareResetNeverSubtractsProgressOrAddsOldSteps() {
        val reset = walkedFour().reading(2, 7, 3, 102)
        assertEquals(4L, reset.totalSteps)
        assertEquals(7L, reset.reading(5, 7, 4, 103).totalSteps)
    }

    @Test fun newJourneyGetsItsOwnBaselineAndTotal() {
        val next = walkedFour().forJourney(2, 200, 0).reading(4_100, 7, 4, 201)
        assertEquals(0L, next.totalSteps)
        assertEquals(3L, next.reading(4_103, 7, 5, 202).totalSteps)
    }

    @Test fun serverRawTotalIsTheFloorWhenResumingAnExistingJourney() {
        val resumed = DeviceStepCounterState().forJourney(1, 100, 80)
            .reading(4_000, 7, 1, 101).reading(4_003, 7, 2, 102)
        assertEquals(83L, resumed.totalSteps)
        assertEquals(83L, resumed.forJourney(1, 100, 80).totalSteps)
        assertEquals(90L, resumed.forJourney(1, 100, 90).totalSteps)
    }

    @Test fun delayedReadingsFromBeforeTheJourneyOnlyUpdateTheBaseline() {
        val state = started().reading(4_000, 7, 1, 90).reading(4_005, 7, 2, 99)
        assertEquals(0L, state.totalSteps)
        assertEquals(2L, state.reading(4_007, 7, 3, 101).totalSteps)
    }

    @Test fun pausingKeepsProgressWithoutCountingThePausedInterval() {
        val stopped = walkedFour().stopped()
        assertEquals(stopped, stopped.reading(5_000, 7, 3, 105))
        val resumed = stopped.forJourney(1, 100, 4).reading(5_000, 7, 4, 106)
        assertEquals(4L, resumed.totalSteps)
        assertEquals(6L, resumed.reading(5_002, 7, 5, 107).totalSteps)
    }
}
