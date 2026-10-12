package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test

class DictionaryRequestFenceTest {
    @Test fun newWordSourceBookOrViewMakesOldResultsInvalid() {
        val fence = DictionaryRequestFence()
        val first = fence.begin("book-a", 1, 1)
        assertTrue(fence.isCurrent(first))
        val second = fence.begin("book-a", 2, 1)
        assertFalse(fence.isCurrent(first))
        assertTrue(fence.isCurrent(second))
        val nextBook = fence.begin("book-b", 2, 1)
        assertFalse(fence.isCurrent(second))
        val nextView = fence.begin("book-b", 2, 2)
        assertFalse(fence.isCurrent(nextBook))
        fence.invalidate()
        assertFalse(fence.isCurrent(nextView))
    }
}
