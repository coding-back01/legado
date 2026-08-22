package io.legado.app.model.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadingTimeMigrationContractTest {

    @Test
    fun `legacy ewma state is discarded without losing identity`() {
        val legacy = ReadingTimeState(
            version = 1,
            bookIdentityHash = 11L,
            tocChapterCount = 20,
            tocPrefixHash = 22L,
            sourceLastModified = 33L,
        )

        val restored = ReadingTimeEstimator(legacy).stateSnapshot()

        assertNotEquals(1, ReadingTimeState.CURRENT_VERSION)
        assertEquals(ReadingTimeState.CURRENT_VERSION, restored.version)
        assertFalse(restored.isChapterQualified())
        assertFalse(restored.isContentQualified())
        assertEquals(11L, restored.bookIdentityHash)
        assertEquals(20, restored.tocChapterCount)
        assertEquals(22L, restored.tocPrefixHash)
        assertEquals(33L, restored.sourceLastModified)
    }
}
