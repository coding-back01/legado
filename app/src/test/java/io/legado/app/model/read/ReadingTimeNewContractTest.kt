package io.legado.app.model.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.ln

class ReadingTimeNewContractTest {

    @Test
    fun `visible unicode units ignore structural whitespace and keep punctuation`() {
        assertEquals(5, VisibleTextUnits.count("你 好，\nA😀"))
        assertEquals(0, VisibleTextUnits.count(" \t\r\n　"))
    }

    @Test
    fun `elapsed time is paired with the page that was actually visible`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = intArrayOf(1_000, 1_000),
                visibleLengths = intArrayOf(500, 500),
            )
        )
        estimator.resume(anchor(chapter = 0, progress = 0.0, visibleUnits = 100))

        now += 10_000L
        val result = estimator.onForward(
            anchor(chapter = 0, progress = 0.2, visibleUnits = 400),
            nowMillis = now,
        )

        assertTrue(result.sampleAccepted)
        assertEquals(0.1, estimator.diagnostics().secondsPerVisibleUnit, 0.000_001)
    }

    @Test
    fun `short forward evidence is accumulated instead of hard rejected`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = intArrayOf(1_000, 1_000),
                visibleLengths = intArrayOf(500, 500),
            )
        )
        estimator.resume(anchor(0, 0.0, 20))

        now += 1_000L
        val first = estimator.onForward(anchor(0, 0.04, 20), nowMillis = now)
        now += 4_000L
        val second = estimator.onForward(anchor(0, 0.08, 80), nowMillis = now)

        assertTrue(first.sampleAccepted || second.sampleAccepted)
        assertTrue(estimator.diagnostics().effectiveEvidenceMillis > 0L)
    }

    @Test
    fun `adjacent chapter anchor trains while jump and obstruction only reanchor`() {
        var now = 0L
        val estimator = ReadingTimeEstimator(elapsedRealtime = { now })
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = IntArray(5) { 1_000 },
                visibleLengths = IntArray(5) { 500 },
            )
        )
        estimator.resume(anchor(0, 0.9, 50))

        now += 5_000L
        assertTrue(estimator.onForward(anchor(1, 0.0, 100), nowMillis = now).sampleAccepted)
        now += 30_000L
        estimator.reanchor(anchor(3, 0.0, 100), nowMillis = now)
        now += 10_000L
        assertTrue(estimator.onForward(anchor(3, 0.2, 100), nowMillis = now).sampleAccepted)
        val evidenceBeforePause = estimator.diagnostics().effectiveEvidenceMillis
        estimator.pause()
        now += 10_000L
        assertFalse(estimator.onForward(anchor(3, 0.4, 100), nowMillis = now).sampleAccepted)
        assertEquals(evidenceBeforePause, estimator.diagnostics().effectiveEvidenceMillis)
    }

    @Test
    fun `raw proxy is calibrated from observed chapters and uncertainty is exposed`() {
        val snapshot = ReadingTimeIndexSnapshot.create(
            rawLengths = intArrayOf(1_000, 2_000, 4_000),
            visibleLengths = intArrayOf(500, 1_000, ReadingTimeIndexSnapshot.UNKNOWN_LENGTH),
        )

        assertEquals(2_000.0, snapshot.estimatedVisibleLength(2), 0.001)
        assertEquals(
            2_000.0,
            snapshot.remainingContentUnits(ReadingTimePosition(2, 0.0)),
            0.001,
        )
        assertTrue(snapshot.remainingConfidence in 0.0..<1.0)
    }

    @Test
    fun `fully observed visible lengths have full remaining confidence`() {
        val snapshot = ReadingTimeIndexSnapshot.create(
            rawLengths = intArrayOf(1_000, 2_000),
            visibleLengths = intArrayOf(500, 1_000),
        )

        assertEquals(1.0, snapshot.remainingConfidence, 0.0)
        assertEquals(ReadingTimeEstimateMode.FULL_CONTENT, snapshot.mode)
    }

    @Test
    fun `learning diagnostics distinguish speed and remaining uncertainty`() {
        val estimator = ReadingTimeEstimator()
        estimator.updateIndex(ReadingTimeIndexSnapshot.create(intArrayOf(1_000, 1_000)))

        val diagnostics = estimator.diagnostics()

        assertEquals(ReadingTimeConfidenceReason.NO_EVIDENCE, diagnostics.speedReason)
        assertEquals(ReadingTimeConfidenceReason.NO_EVIDENCE, diagnostics.remainingReason)
        assertTrue(estimator.estimate(ReadingTimePosition(0, 0.0)) is ReadingTimeEstimate.Learning)
    }

    @Test
    fun `legacy sidecar retains raw lengths and starts visible lengths unknown`() {
        val decoded = requireNotNull(ReadingTimeIndexCodec.decode(legacyV1Bytes(intArrayOf(100, -1, 300))))

        assertTrue(intArrayOf(100, -1, 300).contentEquals(decoded.rawLengths))
        assertTrue(
            IntArray(3) { ReadingTimeIndexSnapshot.UNKNOWN_LENGTH }
                .contentEquals(decoded.visibleLengths)
        )
    }

    @Test
    fun `compatible compact summary restores mature speed without raw history`() {
        val state = ReadingTimeState(
            recentLogSecondsPerUnit = ln(0.1),
            recentLogMad = 0.05,
            recentEvidenceMillis = 600_000L,
            longTermLogSecondsPerUnit = ln(0.1),
            longTermLogMad = 0.05,
            longTermEvidenceMillis = 3_600_000L,
        )
        val estimator = ReadingTimeEstimator(state)
        estimator.updateIndex(
            ReadingTimeIndexSnapshot.create(
                rawLengths = intArrayOf(1_000, 1_000),
                visibleLengths = intArrayOf(500, 500),
            )
        )

        assertEquals(0.1, estimator.diagnostics().secondsPerVisibleUnit, 0.000_001)
        assertTrue(estimator.estimate(ReadingTimePosition(0, 0.0)) is ReadingTimeEstimate.Ready)
    }

    private fun anchor(
        chapter: Int,
        progress: Double,
        visibleUnits: Int,
    ): ReadingTimeAnchor {
        return ReadingTimeAnchor(
            position = ReadingTimePosition(chapter, progress),
            visibleTextUnits = visibleUnits,
            textReliability = 1.0,
        )
    }

    private fun legacyV1Bytes(lengths: IntArray): ByteArray {
        val bytes = ByteArray(44 + lengths.size * Int.SIZE_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(0x52544931)
        buffer.putInt(1)
        buffer.putLong(11L)
        buffer.putLong(22L)
        buffer.putLong(33L)
        buffer.putInt(lengths.size)
        buffer.putInt(lengths.count { it > 0 })
        lengths.forEach(buffer::putInt)
        val crc = CRC32().apply { update(bytes, 0, bytes.size - Int.SIZE_BYTES) }.value.toInt()
        buffer.putInt(crc)
        return bytes
    }
}
