package io.legado.app.model.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTimeEstimatorTest {

    @Test
    fun `five samples and sixty seconds unlock estimate`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))

        repeat(4) { index ->
            now += 12_000L
            val result = estimator.onForward(ReadingTimePosition(0, (index + 1) / 10.0))
            assertTrue(result.sampleAccepted)
            assertTrue(result.estimate is ReadingTimeEstimate.Learning)
        }

        now += 12_000L
        val result = estimator.onForward(ReadingTimePosition(0, 0.5))
        assertTrue(result.sampleAccepted)
        assertTrue(result.estimate is ReadingTimeEstimate.Ready)
        assertEquals(5, estimator.stateSnapshot().sampleCount)
        assertEquals(60_000L, estimator.stateSnapshot().validReadingMillis)
    }

    @Test
    fun `short long backward and paused moves do not train`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))

        now += 4_999L
        assertFalse(estimator.onForward(ReadingTimePosition(0, 0.1)).sampleAccepted)
        now += 120_001L
        assertFalse(estimator.onForward(ReadingTimePosition(0, 0.2)).sampleAccepted)
        now += 10_000L
        assertFalse(estimator.onForward(ReadingTimePosition(0, 0.1)).sampleAccepted)
        estimator.pause()
        now += 10_000L
        assertFalse(estimator.onForward(ReadingTimePosition(0, 0.2)).sampleAccepted)
        assertEquals(0, estimator.stateSnapshot().sampleCount)
    }

    @Test
    fun `chapter mode estimates by chapter coordinate`() {
        val state = qualifiedState(chapterSecondsPerUnit = 600.0)
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))

        val estimate = estimator.estimate(ReadingTimePosition(2, 0.5)) as ReadingTimeEstimate.Ready

        assertEquals(ReadingTimeEstimateMode.CHAPTER, estimate.mode)
        assertEquals(4_500.0, estimate.remainingSeconds, 0.001)
    }

    @Test
    fun `full content mode uses actual remaining bytes`() {
        val state = qualifiedState(
            chapterSecondsPerUnit = 600.0,
            contentSecondsPerByte = 0.1,
        )
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(intArrayOf(100, 200, 300)))

        val estimate = estimator.estimate(ReadingTimePosition(1, 0.5)) as ReadingTimeEstimate.Ready

        assertEquals(ReadingTimeEstimateMode.FULL_CONTENT, estimate.mode)
        assertEquals(40.0, estimate.remainingSeconds, 0.001)
    }

    @Test
    fun `hybrid mode fills unknown chapters with median`() {
        val lengths = IntArray(25) { index ->
            if (index < 20) 100 else ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
        }
        val state = qualifiedState(
            chapterSecondsPerUnit = 600.0,
            contentSecondsPerByte = 0.1,
        )
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(lengths))

        val estimate = estimator.estimate(ReadingTimePosition(20, 0.0)) as ReadingTimeEstimate.Ready

        assertEquals(ReadingTimeEstimateMode.HYBRID_CONTENT, estimate.mode)
        assertEquals(50.0, estimate.remainingSeconds, 0.001)
    }

    @Test
    fun `content mode waits for its own evidence`() {
        val state = qualifiedState(chapterSecondsPerUnit = 600.0).copy(
            contentSecondsPerByte = 0.1,
            contentSampleCount = 4,
            contentValidReadingMillis = 59_000L,
        )
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(intArrayOf(100, 200, 300)))

        val estimate = estimator.estimate(ReadingTimePosition(1, 0.5)) as ReadingTimeEstimate.Ready

        assertEquals(ReadingTimeEstimateMode.CHAPTER, estimate.mode)
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
    fun `ewma clips a single extreme sample`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))
        now += 10_000L
        estimator.onForward(ReadingTimePosition(0, 0.1))
        val firstRate = estimator.stateSnapshot().chapterSecondsPerUnit

        now += 120_000L
        estimator.onForward(ReadingTimePosition(0, 0.2))
        val clippedRate = estimator.stateSnapshot().chapterSecondsPerUnit

        assertEquals(100.0, firstRate, 0.001)
        assertEquals(160.0, clippedRate, 0.001)
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

        assertEquals(1, estimator.stateSnapshot().sampleCount)
        assertEquals(12_000L, estimator.stateSnapshot().validReadingMillis)
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
        assertEquals(12_000L, estimator.stateSnapshot().validReadingMillis)
        assertEquals(120.0, estimator.stateSnapshot().chapterSecondsPerUnit, 0.001)
    }

    @Test
    fun `reset clears learned speed but retains the current index`() {
        val estimator = ReadingTimeEstimator(
            qualifiedState(chapterSecondsPerUnit = 600.0, contentSecondsPerByte = 0.1)
        )
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(intArrayOf(100, 200, 300)))
        assertTrue(estimator.estimate(ReadingTimePosition(0, 0.5)) is ReadingTimeEstimate.Ready)

        estimator.reset(ReadingTimePosition(0, 0.5))

        assertEquals(ReadingTimeState(), estimator.stateSnapshot())
        assertTrue(estimator.estimate(ReadingTimePosition(0, 0.5)) is ReadingTimeEstimate.Learning)
    }

    @Test
    fun `content mode transition completes after five accepted samples`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(
            qualifiedState(chapterSecondsPerUnit = 600.0, contentSecondsPerByte = 0.1),
            elapsedRealtime = { now },
        )
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0))
        val chapterEstimate = estimator.estimate(ReadingTimePosition(0, 0.0))
                as ReadingTimeEstimate.Ready
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(IntArray(10) { 100 }))

        val estimates = (1..5).map { step ->
            now += 12_000L
            estimator.onForward(ReadingTimePosition(0, step / 10.0)).estimate
                    as ReadingTimeEstimate.Ready
        }

        assertEquals(ReadingTimeEstimateMode.CHAPTER, chapterEstimate.mode)
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
        chapterSecondsPerUnit: Double = 600.0,
        contentSecondsPerByte: Double = 0.0,
    ): ReadingTimeState {
        return ReadingTimeState(
            chapterSecondsPerUnit = chapterSecondsPerUnit,
            contentSecondsPerByte = contentSecondsPerByte,
            sampleCount = 5,
            contentSampleCount = if (contentSecondsPerByte > 0.0) 5 else 0,
            validReadingMillis = 60_000L,
            contentValidReadingMillis = if (contentSecondsPerByte > 0.0) 60_000L else 0L,
        )
    }
}
