package io.legado.app.model.read

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

class ReadingTimeEstimatorReplayTest {

    @Test
    fun `mature model limits one four-times slow page to three percent`() {
        val replay = Replay()
        repeat(60) { replay.advance(10_000L) }
        val baseline = replay.snapshots.last().secondsPerChapter

        val afterOutlier = replay.advance(40_000L).secondsPerChapter

        assertTrue(
            "single outlier changed speed by ${(afterOutlier / baseline - 1.0) * 100}%",
            abs(afterOutlier / baseline - 1.0) <= 0.03,
        )
    }

    @Test
    fun `ten percent slow-page contamination stays within five percent`() {
        val replay = Replay()
        repeat(60) { replay.advance(10_000L) }
        val baseline = replay.snapshots.last().secondsPerChapter

        repeat(100) { index ->
            replay.advance(if ((index + 1) % 10 == 0) 40_000L else 10_000L)
        }
        val contaminated = replay.snapshots.last().secondsPerChapter

        assertTrue(
            "contamination error was ${(contaminated / baseline - 1.0) * 100}%",
            abs(contaminated / baseline - 1.0) <= 0.05,
        )
    }

    @Test
    fun `one hour of ordinary jitter keeps adjacent speed changes below two percent`() {
        val replay = Replay()
        repeat(60) { replay.advance(10_000L) }

        val rates = buildList {
            repeat(360) { index ->
                add(replay.advance(if (index % 2 == 0) 9_000L else 11_000L).secondsPerChapter)
            }
        }
        val largestChange = rates.zipWithNext().maxOf { (before, after) ->
            abs(after / before - 1.0)
        }

        assertTrue("largest adjacent speed change was ${largestChange * 100}%", largestChange <= 0.02)
    }

    @Test
    fun `sustained twenty-five-percent changes follow symmetrically`() {
        listOf(12_500L, 7_500L).forEach { changedMillis ->
            val replay = Replay()
            repeat(60) { replay.advance(10_000L) }
            val baseline = replay.snapshots.last().secondsPerChapter
            val target = baseline * changedMillis / 10_000.0
            val halfTarget = baseline + (target - baseline) * 0.5

            var atFifteenMinutes = baseline
            repeat(210) { index ->
                val snapshot = replay.advance(changedMillis)
                if (index == 71) atFifteenMinutes = snapshot.secondsPerChapter
            }
            val atThirtyFiveMinutes = replay.snapshots.last().secondsPerChapter
            val followedHalf = if (target > baseline) {
                atFifteenMinutes >= halfTarget
            } else {
                atFifteenMinutes <= halfTarget
            }

            assertTrue("did not follow half of $changedMillis ms/page", followedHalf)
            assertTrue(
                "35-minute error for $changedMillis ms/page was ${abs(atThirtyFiveMinutes / target - 1.0) * 100}%",
                abs(atThirtyFiveMinutes / target - 1.0) <= 0.05,
            )
        }
    }

    @Test
    fun `fixed window rollover does not create a visible speed boundary`() {
        val replay = Replay()
        repeat(511) { replay.advance(10_000L) }
        val beforeRollover = replay.snapshots.last().secondsPerChapter

        val afterRollover = replay.advance(10_000L).secondsPerChapter
        val afterReplacement = replay.advance(10_000L).secondsPerChapter

        assertTrue(abs(afterRollover / beforeRollover - 1.0) <= 0.005)
        assertTrue(abs(afterReplacement / afterRollover - 1.0) <= 0.005)
    }

    @Test
    fun `exceptionally long stay has continuous bounded evidence influence`() {
        val replay = Replay()
        repeat(60) { replay.advance(10_000L) }
        val before = replay.snapshots.last().secondsPerChapter

        val after = replay.advance(3_600_000L).secondsPerChapter

        assertTrue(abs(after / before - 1.0) <= 0.03)
    }

    @Test
    fun `normal reread contributes less long-term evidence than novel reading`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(ReadingTimeIndexSnapshot.empty(10))
        estimator.resume(ReadingTimePosition(0, 0.0), now)
        repeat(60) { step ->
            now += 10_000L
            estimator.onForward(ReadingTimePosition(0, (step + 1) / 100.0), nowMillis = now)
        }
        val beforeNovel = estimator.diagnostics().longTermEvidenceMillis
        now += 10_000L
        estimator.onForward(ReadingTimePosition(0, 0.61), nowMillis = now)
        val novelIncrement = estimator.diagnostics().longTermEvidenceMillis - beforeNovel

        estimator.reanchor(ReadingTimePosition(0, 0.2), nowMillis = now)
        val beforeReread = estimator.diagnostics().longTermEvidenceMillis
        now += 10_000L
        estimator.onForward(ReadingTimePosition(0, 0.21), nowMillis = now)
        val rereadIncrement = estimator.diagnostics().longTermEvidenceMillis - beforeReread

        assertTrue(novelIncrement > 0L)
        assertTrue(rereadIncrement in 1 until novelIncrement)
    }

    private data class ReplaySnapshot(
        val secondsPerChapter: Double,
        val remainingChapterUnits: Double,
        val speedConfident: Boolean,
        val acceptedWeight: Double,
        val etaSeconds: Double?,
    )

    private class Replay {
        var nowMillis = 0L
        var coordinate = 0.0
        val snapshots = ArrayList<ReplaySnapshot>()
        private val estimator = ReadingTimeEstimator(elapsedRealtime = { nowMillis }).apply {
            updateIndex(ReadingTimeIndexSnapshot.empty(100))
            resume(positionFor(0.0), 0L)
        }

        fun advance(elapsedMillis: Long, units: Double = 0.01): ReplaySnapshot {
            nowMillis += elapsedMillis
            coordinate += units
            val position = positionFor(coordinate)
            val result = estimator.onForward(position, nowMillis = nowMillis)
            val ready = result.estimate as? ReadingTimeEstimate.Ready
            return ReplaySnapshot(
                secondsPerChapter = estimator.diagnostics().secondsPerVisibleUnit,
                remainingChapterUnits = 100.0 - coordinate,
                speedConfident = ready != null,
                acceptedWeight = result.sampleWeight,
                etaSeconds = ready?.remainingSeconds,
            ).also(snapshots::add)
        }

        private fun positionFor(value: Double): ReadingTimePosition {
            val chapter = floor(value).toInt()
            return ReadingTimePosition(chapter, value - chapter)
        }
    }
}
