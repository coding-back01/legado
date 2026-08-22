package io.legado.app.model.read

import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.floor

@RunWith(AndroidJUnit4::class)
class ReadingTimeEstimatorDevicePerformanceTest {

    @Test
    fun steadySampleUpdateMeetsDeviceLatencyGate() {
        val fixture = Fixture(WARM_UP_SAMPLES + MEASURED_SAMPLES)
        repeat(WARM_UP_SAMPLES) { fixture.advance() }

        val timings = LongArray(MEASURED_SAMPLES)
        repeat(MEASURED_SAMPLES) { index ->
            val started = System.nanoTime()
            fixture.advance()
            timings[index] = System.nanoTime() - started
        }
        timings.sort()
        val p50Nanos = percentile(timings, 0.50)
        val p95Nanos = percentile(timings, 0.95)

        Log.i(TAG, "steady P50=${p50Nanos / 1_000.0}us P95=${p95Nanos / 1_000.0}us")
        assertTrue("P95 was ${p95Nanos / 1_000.0}us", p95Nanos <= P95_GATE_NANOS)
    }

    @Test
    fun bufferFoldStaysWithinDeviceLatencyGate() {
        val fixture = Fixture(FOLD_MEASURE_END)
        repeat(FOLD_MEASURE_START) { fixture.advance() }

        val timings = LongArray(FOLD_MEASURE_END - FOLD_MEASURE_START)
        repeat(timings.size) { index ->
            val started = System.nanoTime()
            fixture.advance()
            timings[index] = System.nanoTime() - started
        }
        timings.sort()
        val p95Nanos = percentile(timings, 0.95)

        Log.i(TAG, "fold-window P95=${p95Nanos / 1_000.0}us")
        assertTrue("Fold-window P95 was ${p95Nanos / 1_000.0}us", p95Nanos <= P95_GATE_NANOS)
    }

    @Test
    @Suppress("DEPRECATION")
    fun steadySampleUpdateDoesNotAllocate() {
        val fixture = Fixture(WARM_UP_SAMPLES + ALLOCATION_SAMPLES)
        repeat(WARM_UP_SAMPLES) { fixture.advance() }

        val control = Fixture(ALLOCATION_SAMPLES)
        Debug.startAllocCounting()
        val controlBefore = Debug.getThreadAllocCount()
        repeat(ALLOCATION_SAMPLES) { control.consumeAnchor() }
        val controlObjects = Debug.getThreadAllocCount() - controlBefore
        Debug.stopAllocCounting()

        val noTraining = Fixture(ALLOCATION_SAMPLES)
        Debug.startAllocCounting()
        val noTrainingBefore = Debug.getThreadAllocCount()
        repeat(ALLOCATION_SAMPLES) { noTraining.advance(allowTraining = false) }
        val noTrainingObjects = Debug.getThreadAllocCount() - noTrainingBefore
        Debug.stopAllocCounting()

        Debug.startAllocCounting()
        val before = Debug.getThreadAllocCount()
        repeat(ALLOCATION_SAMPLES) { fixture.advance() }
        val allocatedObjects = Debug.getThreadAllocCount() - before
        Debug.stopAllocCounting()

        println(
            "ReadingTimePerf allocations control=$controlObjects " +
                    "noTraining=$noTrainingObjects steady=$allocatedObjects " +
                    "samples=$ALLOCATION_SAMPLES"
        )
        assertEquals(
            "Steady sample updates allocated objects; control=$controlObjects, " +
                    "noTraining=$noTrainingObjects",
            0,
            allocatedObjects,
        )
    }

    private class Fixture(sampleCount: Int) {
        private var nowMillis = 0L
        private var next = 0
        private val anchors = Array(sampleCount + 1) { index ->
            anchorAt(index * COORDINATE_STEP)
        }
        private val estimator = ReadingTimeEstimator(elapsedRealtime = { nowMillis }).apply {
            updateIndex(
                ReadingTimeIndexSnapshot.create(
                    rawLengths = IntArray(100) { 10_000 },
                    visibleLengths = IntArray(100) { 10_000 },
                )
            )
            resume(anchors[0], nowMillis)
        }

        fun advance(allowTraining: Boolean = true): Boolean {
            nowMillis += SAMPLE_MILLIS
            next++
            return estimator.advance(
                anchors[next],
                allowTraining = allowTraining,
                nowMillis = nowMillis,
            )
        }

        fun consumeAnchor(): Int {
            next++
            return anchors[next].visibleTextUnits
        }

    }

    companion object {
        private const val TAG = "ReadingTimePerf"
        private const val WARM_UP_SAMPLES = 1_000
        private const val MEASURED_SAMPLES = 2_000
        private const val ALLOCATION_SAMPLES = 2_000
        private const val FOLD_MEASURE_START = 480
        private const val FOLD_MEASURE_END = 560
        private const val SAMPLE_MILLIS = 10_000L
        private const val COORDINATE_STEP = 0.001
        private const val P95_GATE_NANOS = 500_000L

        private fun anchorAt(coordinate: Double): ReadingTimeAnchor {
            val chapter = floor(coordinate).toInt()
            return ReadingTimeAnchor(
                position = ReadingTimePosition(chapter, coordinate - chapter),
                visibleTextUnits = 10,
            )
        }

        private fun percentile(sorted: LongArray, fraction: Double): Long {
            return sorted[(sorted.size * fraction).toInt().coerceAtMost(sorted.lastIndex)]
        }
    }
}
