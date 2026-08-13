package io.legado.app.constant

import org.junit.Assert.assertEquals
import org.junit.Test

class PageAnimTest {

    @Test
    fun `existing protocol values remain stable and Kindle is appended`() {
        assertEquals(0, PageAnim.coverPageAnim)
        assertEquals(1, PageAnim.slidePageAnim)
        assertEquals(2, PageAnim.simulationPageAnim)
        assertEquals(3, PageAnim.scrollPageAnim)
        assertEquals(4, PageAnim.noAnim)
        assertEquals(5, PageAnim.kindlePageAnim)
    }

    @Test
    fun `known values remain unchanged and unknown values use no animation`() {
        (0..5).forEach { value ->
            assertEquals(value, PageAnim.normalize(value))
        }
        assertEquals(PageAnim.noAnim, PageAnim.normalize(-1))
        assertEquals(PageAnim.noAnim, PageAnim.normalize(6))
        assertEquals(PageAnim.noAnim, PageAnim.normalize(Int.MAX_VALUE))
    }
}
