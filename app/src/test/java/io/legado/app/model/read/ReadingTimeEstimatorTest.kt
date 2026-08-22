package io.legado.app.model.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class ReadingTimeEstimatorTest {

    @Test
    fun `evidence confidence naturally unlocks estimate`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = IntArray(10) { 1_000 },
                visibleLengths = IntArray(10) { 1_000 },
            )
        )
        estimator.resume(ReadingTimePosition(0, 0.0))

        var firstReadyAt: Int? = null
        repeat(10) { index ->
            now += 12_000L
            val result = estimator.onForward(ReadingTimePosition(0, (index + 1) / 10.0))
            assertTrue(result.sampleAccepted)
            if (firstReadyAt == null && result.estimate is ReadingTimeEstimate.Ready) {
                firstReadyAt = index + 1
            }
        }

        assertTrue(firstReadyAt != null)
        assertEquals(10, estimator.stateSnapshot().acceptedSampleCount)
        assertTrue(estimator.stateSnapshot().totalEffectiveReadingMillis >= 60_000L)
    }

    @Test
    fun `short and long forward evidence train while backward and paused moves do not`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))

        now += 4_999L
        assertTrue(estimator.onForward(ReadingTimePosition(0, 0.1)).sampleAccepted)
        now += 120_001L
        assertTrue(estimator.onForward(ReadingTimePosition(0, 0.2)).sampleAccepted)
        now += 10_000L
        assertFalse(estimator.onForward(ReadingTimePosition(0, 0.1)).sampleAccepted)
        estimator.pause()
        now += 10_000L
        assertFalse(estimator.onForward(ReadingTimePosition(0, 0.2)).sampleAccepted)
        assertEquals(2, estimator.stateSnapshot().acceptedSampleCount)
    }

    @Test
    fun `unknown remaining content stays learning`() {
        val state = qualifiedState()
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))

        assertTrue(estimator.estimate(ReadingTimePosition(2, 0.5)) is ReadingTimeEstimate.Learning)
    }

    @Test
    fun `full content mode uses actual remaining visible units`() {
        val state = qualifiedState(secondsPerVisibleUnit = 0.1)
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = intArrayOf(100, 200, 300),
                visibleLengths = intArrayOf(100, 200, 300),
            )
        )

        val estimate = estimator.estimate(ReadingTimePosition(1, 0.5)) as ReadingTimeEstimate.Ready

        assertEquals(ReadingTimeEstimateMode.FULL_CONTENT, estimate.mode)
        assertEquals(40.0, estimate.remainingSeconds, 0.001)
    }

    @Test
    fun `hybrid mode fills unknown chapters with median`() {
        val lengths = IntArray(25) { index ->
            if (index < 25) 100 else ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
        }
        val visibleLengths = IntArray(25) { index ->
            if (index < 20) 100 else ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
        }
        val state = qualifiedState(secondsPerVisibleUnit = 0.1)
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(lengths, visibleLengths))

        val estimate = estimator.estimate(ReadingTimePosition(20, 0.0)) as ReadingTimeEstimate.Ready

        assertEquals(ReadingTimeEstimateMode.HYBRID_CONTENT, estimate.mode)
        assertEquals(50.0, estimate.remainingSeconds, 0.001)
    }

    @Test
    fun `estimate waits for remaining amount confidence`() {
        val state = qualifiedState(secondsPerVisibleUnit = 0.1)
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(intArrayOf(100, 200, 300)))

        assertTrue(estimator.estimate(ReadingTimePosition(1, 0.5)) is ReadingTimeEstimate.Learning)
    }

    @Test
    fun `finished book reports zero`() {
        val estimator = ReadingTimeEstimator(qualifiedState())
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(2))

        val estimate = estimator.estimate(ReadingTimePosition(1, 1.0)) as ReadingTimeEstimate.Ready

        assertEquals(0.0, estimate.remainingSeconds, 0.0)
    }

    @Test
    fun `duration rounds remaining time upward`() {
        assertEquals(0L, ReadingTimeDuration.remainingMinutes(0.0))
        assertEquals(1L, ReadingTimeDuration.remainingMinutes(0.1))
        assertEquals(1L, ReadingTimeDuration.remainingMinutes(60.0))
        assertEquals(2L, ReadingTimeDuration.remainingMinutes(60.1))
        assertEquals(80L, ReadingTimeDuration.accumulatedMinutes(4_800_999L))
        assertEquals(
            ReadingTimeDuration.HoursMinutes(1L, 20),
            ReadingTimeDuration.splitMinutes(80L),
        )
        assertEquals(
            ReadingTimeDuration.HoursMinutes(55L, 24),
            ReadingTimeDuration.splitMinutes(3_324L),
        )
    }

    @Test
    fun `robust model continuously downweights a single extreme sample`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))
        repeat(60) { step ->
            now += 10_000L
            estimator.onForward(ReadingTimePosition(0, (step + 1) / 100.0))
        }
        val firstRate = estimator.diagnostics().secondsPerVisibleUnit

        now += 40_000L
        estimator.onForward(ReadingTimePosition(0, 0.61))
        val robustRate = estimator.diagnostics().secondsPerVisibleUnit

        assertEquals(1.0, firstRate, 0.000_001)
        assertTrue(abs(robustRate / firstRate - 1.0) <= 0.03)
    }

    @Test
    fun `adjacent chapter advance trains while chapter jump does not`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.9))

        now += 12_000L
        assertTrue(estimator.onForward(ReadingTimePosition(1, 0.1)).sampleAccepted)
        now += 12_000L
        assertFalse(estimator.onForward(ReadingTimePosition(3, 0.1)).sampleAccepted)

        assertEquals(1, estimator.stateSnapshot().acceptedSampleCount)
        assertTrue(estimator.stateSnapshot().totalEffectiveReadingMillis > 0L)
    }

    @Test
    fun `reanchor discards time spent before jump or obstruction`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))

        now += 30_000L
        estimator.reanchor(ReadingTimePosition(0, 0.5))
        now += 12_000L
        val result = estimator.onForward(ReadingTimePosition(0, 0.6))

        assertTrue(result.sampleAccepted)
        assertTrue(estimator.stateSnapshot().totalEffectiveReadingMillis > 0L)
        assertEquals(0.12, estimator.diagnostics().secondsPerVisibleUnit, 0.000_001)
    }

    @Test
    fun `reset clears learned speed but retains the current index`() {
        val estimator = ReadingTimeEstimator(
            qualifiedState(secondsPerVisibleUnit = 0.1)
        )
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = intArrayOf(100, 200, 300),
                visibleLengths = intArrayOf(100, 200, 300),
            )
        )
        assertTrue(estimator.estimate(ReadingTimePosition(0, 0.5)) is ReadingTimeEstimate.Ready)

        estimator.reset(ReadingTimePosition(0, 0.5))

        assertEquals(ReadingTimeState(), estimator.stateSnapshot())
        assertTrue(estimator.estimate(ReadingTimePosition(0, 0.5)) is ReadingTimeEstimate.Learning)
    }

    @Test
    fun `exact visible index immediately replaces uncertain remaining amount`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(
            qualifiedState(secondsPerVisibleUnit = 0.1),
            elapsedRealtime = { now },
        )
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))
        val uncertainEstimate = estimator.estimate(ReadingTimePosition(0, 0.0))
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = IntArray(10) { 100 },
                visibleLengths = IntArray(10) { 100 },
            )
        )

        val estimates = (1..5).map { step ->
            now += 12_000L
            estimator.onForward(ReadingTimePosition(0, step / 10.0)).estimate
                    as ReadingTimeEstimate.Ready
        }

        assertTrue(uncertainEstimate is ReadingTimeEstimate.Learning)
        assertTrue(estimates.all { it.mode == ReadingTimeEstimateMode.FULL_CONTENT })
        assertTrue(estimates.zipWithNext().all { (before, after) ->
            before.remainingSeconds > after.remainingSeconds
        })
        assertEquals(
            estimates.last().remainingSeconds,
            (estimator.estimate(ReadingTimePosition(0, 0.5)) as ReadingTimeEstimate.Ready)
                .remainingSeconds,
            0.001,
        )
    }

    private fun qualifiedState(
        secondsPerVisibleUnit: Double = 0.1,
    ): ReadingTimeState {
        return ReadingTimeState(
            recentLogSecondsPerUnit = ln(secondsPerVisibleUnit),
            recentLogMad = 0.08,
            recentEvidenceMillis = 600_000L,
            longTermLogSecondsPerUnit = ln(secondsPerVisibleUnit),
            longTermLogMad = 0.08,
            longTermEvidenceMillis = 3_600_000L,
            acceptedSampleCount = 50,
            totalEffectiveReadingMillis = 3_600_000L,
        )
    }
}
