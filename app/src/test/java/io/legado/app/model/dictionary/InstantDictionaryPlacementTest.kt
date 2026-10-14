package io.legado.app.model.dictionary

import org.junit.Assert.*
import org.junit.Test

class InstantDictionaryPlacementTest {
    @Test fun insufficientSpaceCollapsesTextAndChoosesRegionAwayFromSelection() {
        val compact = InstantDictionaryPlacements.plan(320, 130, 190, 220, 12)
        assertFalse(compact.showText)
        assertTrue(compact.above)
        val below = InstantDictionaryPlacements.plan(800, 120, 170, 200, 12)
        assertTrue(below.showText)
        assertFalse(below.above)
    }
}
