package io.legado.app.model.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ReadingTimeIndexCodecTest {

    @Test
    fun `codec round trip preserves header and lengths`() {
        val data = ReadingTimeIndexData(
            bookIdentityHash = 11L,
            tocPrefixHash = 22L,
            sourceLastModified = 33L,
            rawLengths = intArrayOf(100, 0, -1, 250),
        )

        val decoded = ReadingTimeIndexCodec.decode(ReadingTimeIndexCodec.encode(data))

        requireNotNull(decoded)
        assertEquals(data.bookIdentityHash, decoded.bookIdentityHash)
        assertEquals(data.tocPrefixHash, decoded.tocPrefixHash)
        assertEquals(data.sourceLastModified, decoded.sourceLastModified)
        assertTrue(data.rawLengths.contentEquals(decoded.rawLengths))
    }

    @Test
    fun `codec rejects truncation corruption and unsupported version`() {
        val bytes = ReadingTimeIndexCodec.encode(
            ReadingTimeIndexData(1L, 2L, 3L, intArrayOf(100, -1, 200))
        )

        assertNull(ReadingTimeIndexCodec.decode(bytes.copyOf(bytes.size - 1)))
        assertNull(ReadingTimeIndexCodec.decode(bytes.copyOf().also { it[20] = (it[20] + 1).toByte() }))
        assertNull(ReadingTimeIndexCodec.decode(bytes.copyOf().also { it[7] = 2 }))
    }

    @Test
    fun `file write replaces old data and ignores broken file`() {
        val directory = createTempDirectory("reading-time-index-").toFile()
        val file = File(directory, "reading_time_index.bin")
        try {
            val first = ReadingTimeIndexData(1L, 2L, 3L, intArrayOf(100))
            val second = ReadingTimeIndexData(4L, 5L, 6L, intArrayOf(200, 300))

            assertTrue(ReadingTimeIndexCodec.write(file, first))
            assertTrue(ReadingTimeIndexCodec.write(file, second))
            assertTrue(second.rawLengths.contentEquals(requireNotNull(ReadingTimeIndexCodec.read(file)).rawLengths))

            file.writeBytes(byteArrayOf(1, 2, 3))
            assertNull(ReadingTimeIndexCodec.read(file))
        } finally {
            assertTrue(directory.deleteRecursively())
        }
    }

    @Test
    fun `snapshot prefix queries do not mutate caller array`() {
        val lengths = intArrayOf(100, -1, 0, 300)
        val snapshot = ReadingTimeIndexSnapshot.create(lengths)
        lengths[0] = 999

        assertEquals(100, snapshot.rawLengths[0])
        assertEquals(2, snapshot.knownContentCount)
        assertEquals(3, snapshot.contentChapterCount)
        assertFalse(snapshot.hasUnknownContentBetween(2, 3))
        assertTrue(snapshot.hasUnknownContentBetween(0, 1))
    }

    @Test
    fun `toc append preserves lengths while reorder resets model`() {
        val oldEntries = listOf(
            ReadingTimeTocEntry("0|a"),
            ReadingTimeTocEntry("1|b"),
        )
        val stored = ReadingTimeIndexData(
            bookIdentityHash = 7L,
            tocPrefixHash = ReadingTimeIndexReconciler.tocHash(oldEntries),
            sourceLastModified = 0L,
            rawLengths = intArrayOf(100, 200),
        )

        val appended = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = 7L,
            sourceLastModified = 0L,
            entries = oldEntries + ReadingTimeTocEntry("2|c"),
        )
        assertFalse(appended.resetSpeedModel)
        assertTrue(intArrayOf(100, 200, -1).contentEquals(appended.rawLengths))

        val reordered = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = 7L,
            sourceLastModified = 0L,
            entries = listOf(oldEntries[1], oldEntries[0]),
        )
        assertTrue(reordered.resetSpeedModel)
        assertTrue(intArrayOf(-1, -1).contentEquals(reordered.rawLengths))
    }

    @Test
    fun `direct local lengths override stale sidecar`() {
        val oldEntries = listOf(ReadingTimeTocEntry("0|a"))
        val stored = ReadingTimeIndexData(
            bookIdentityHash = 7L,
            tocPrefixHash = ReadingTimeIndexReconciler.tocHash(oldEntries),
            sourceLastModified = 9L,
            rawLengths = intArrayOf(100),
        )

        val result = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = 7L,
            sourceLastModified = 9L,
            entries = listOf(ReadingTimeTocEntry("0|a", 250)),
        )

        assertFalse(result.resetSpeedModel)
        assertTrue(intArrayOf(250).contentEquals(result.rawLengths))
    }

    @Test
    fun `source identity modification and directory shrink reset the model`() {
        val entries = listOf(
            ReadingTimeTocEntry("0|a"),
            ReadingTimeTocEntry("1|b"),
        )
        val stored = ReadingTimeIndexData(
            bookIdentityHash = 7L,
            tocPrefixHash = ReadingTimeIndexReconciler.tocHash(entries),
            sourceLastModified = 9L,
            rawLengths = intArrayOf(100, 200),
        )

        val modifiedFile = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = 7L,
            sourceLastModified = 10L,
            entries = entries,
        )
        val changedSource = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = 8L,
            sourceLastModified = 9L,
            entries = entries,
        )
        val shrunkDirectory = ReadingTimeIndexReconciler.reconcile(
            stored = stored,
            bookIdentityHash = 7L,
            sourceLastModified = 9L,
            entries = entries.take(1),
        )

        assertTrue(modifiedFile.resetSpeedModel)
        assertTrue(changedSource.resetSpeedModel)
        assertTrue(shrunkDirectory.resetSpeedModel)
    }

    @Test
    fun `hybrid mode requires both chapter count and coverage thresholds`() {
        val tooFewKnown = IntArray(100) { index ->
            if (index < 19) 100 else ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
        }
        val tooLittleCoverage = IntArray(101) { index ->
            if (index < 20) 100 else ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
        }
        val thresholdReached = IntArray(100) { index ->
            if (index < 20) 100 else ReadingTimeIndexSnapshot.UNKNOWN_LENGTH
        }

        assertEquals(
            ReadingTimeEstimateMode.CHAPTER,
            ReadingTimeIndexSnapshot.create(tooFewKnown).mode,
        )
        assertEquals(
            ReadingTimeEstimateMode.CHAPTER,
            ReadingTimeIndexSnapshot.create(tooLittleCoverage).mode,
        )
        assertEquals(
            ReadingTimeEstimateMode.HYBRID_CONTENT,
            ReadingTimeIndexSnapshot.create(thresholdReached).mode,
        )
    }

    @Test
    fun `speed state rejects changed local file even without a sidecar`() {
        val entries = listOf(ReadingTimeTocEntry("0|a"))
        val state = ReadingTimeState(
            chapterSecondsPerUnit = 600.0,
            sampleCount = 5,
            validReadingMillis = 60_000L,
            bookIdentityHash = 7L,
            tocChapterCount = 1,
            tocPrefixHash = ReadingTimeIndexReconciler.tocHash(entries),
            sourceLastModified = 9L,
        )

        assertFalse(
            ReadingTimeIndexReconciler.shouldResetSpeedState(state, 7L, 9L, entries)
        )
        assertTrue(
            ReadingTimeIndexReconciler.shouldResetSpeedState(state, 7L, 10L, entries)
        )
    }
}
