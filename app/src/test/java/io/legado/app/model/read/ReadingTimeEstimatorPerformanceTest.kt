package io.legado.app.model.read

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

class ReadingTimeEstimatorPerformanceTest {

    @Test
    fun `steady sample update p95 stays below half a millisecond`() {
        var nowMillis = 0L
        var coordinate = 0.0
        val estimator = ReadingTimeEstimator(elapsedRealtime = { nowMillis }).apply {
            updateIndex(
                ReadingTimeIndexSnapshot.create(
                    rawLengths = IntArray(100) { 10_000 },
                    visibleLengths = IntArray(100) { 10_000 },
                )
            )
            resume(anchorAt(coordinate), nowMillis)
        }

        repeat(WARM_UP_SAMPLES) {
            nowMillis += 10_000L
            coordinate += 0.001
            estimator.advance(anchorAt(coordinate), nowMillis = nowMillis)
        }

        val timings = LongArray(MEASURED_SAMPLES)
        repeat(MEASURED_SAMPLES) { index ->
            nowMillis += 10_000L
            coordinate += 0.001
            val started = System.nanoTime()
            estimator.advance(anchorAt(coordinate), nowMillis = nowMillis)
            timings[index] = System.nanoTime() - started
        }
        timings.sort()
        val p50Nanos = timings[(timings.size * 0.50).toInt()]
        val p95Nanos = timings[(timings.size * 0.95).toInt()]

        println("ReadingTimeEstimator P50=${p50Nanos / 1_000.0}us P95=${p95Nanos / 1_000.0}us")
        assertTrue("P95 was ${p95Nanos / 1_000.0}us", p95Nanos <= 500_000L)
    }

    private fun anchorAt(coordinate: Double): ReadingTimeAnchor {
        val chapter = floor(coordinate).toInt()
        return ReadingTimeAnchor(
            position = ReadingTimePosition(chapter, coordinate - chapter),
            visibleTextUnits = 10,
        )
    }

    companion object {
        private const val WARM_UP_SAMPLES = 1_000
        private const val MEASURED_SAMPLES = 2_000
    }
}
