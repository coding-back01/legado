package io.legado.app.ui.book.read.page.animation

import org.junit.Assert.assertEquals
import org.junit.Test

class KindlePageTurnTimelineTest {

    @Test
    fun `production duration is the selected 200 milliseconds`() {
        assertEquals(200L, KindlePageTurnTimeline.DURATION_MILLIS)
    }

    @Test
    fun `next page transition moves from right to left`() {
        val frame = KindlePageTurnTimeline.frame(
            viewWidth = 1000,
            direction = KindlePageTurnDirection.NEXT,
            progress = 0.5f,
        )

        assertEquals(325f, frame.transitionStartX)
        assertEquals(675f, frame.transitionEndX)
        assertEquals(0f, frame.targetAlphaAt(0f))
        assertEquals(0.5f, frame.targetAlphaAt(500f))
        assertEquals(1f, frame.targetAlphaAt(1000f))
    }

    @Test
    fun `previous page transition moves from left to right`() {
        val frame = KindlePageTurnTimeline.frame(
            viewWidth = 1000,
            direction = KindlePageTurnDirection.PREVIOUS,
            progress = 0.5f,
        )

        assertEquals(325f, frame.transitionStartX)
        assertEquals(675f, frame.transitionEndX)
        assertEquals(1f, frame.targetAlphaAt(0f))
        assertEquals(0.5f, frame.targetAlphaAt(500f))
        assertEquals(0f, frame.targetAlphaAt(1000f))
    }

    @Test
    fun `transition begins and ends outside viewport`() {
        val nextStart = KindlePageTurnTimeline.frame(
            800,
            KindlePageTurnDirection.NEXT,
            0f,
        )
        val nextEnd = KindlePageTurnTimeline.frame(
            800,
            KindlePageTurnDirection.NEXT,
            1f,
        )
        val previousStart = KindlePageTurnTimeline.frame(
            800,
            KindlePageTurnDirection.PREVIOUS,
            0f,
        )
        val previousEnd = KindlePageTurnTimeline.frame(
            800,
            KindlePageTurnDirection.PREVIOUS,
            1f,
        )

        assertEquals(0f, nextStart.targetAlphaAt(400f))
        assertEquals(1f, nextEnd.targetAlphaAt(400f))
        assertEquals(0f, previousStart.targetAlphaAt(400f))
        assertEquals(1f, previousEnd.targetAlphaAt(400f))
    }

    @Test
    fun `content blend is continuous within wide transition`() {
        val frame = KindlePageTurnTimeline.frame(
            viewWidth = 1000,
            direction = KindlePageTurnDirection.NEXT,
            progress = 0.5f,
        )

        assertEquals(0.15625f, frame.targetAlphaAt(412.5f))
        assertEquals(0.84375f, frame.targetAlphaAt(587.5f))
    }

    @Test
    fun `progress is linear and clamped to animation duration`() {
        assertEquals(0f, KindlePageTurnTimeline.progress(-10, 200))
        assertEquals(0.25f, KindlePageTurnTimeline.progress(50, 200))
        assertEquals(0.5f, KindlePageTurnTimeline.progress(100, 200))
        assertEquals(0.75f, KindlePageTurnTimeline.progress(150, 200))
        assertEquals(1f, KindlePageTurnTimeline.progress(200, 200))
        assertEquals(1f, KindlePageTurnTimeline.progress(300, 220))
        assertEquals(1f, KindlePageTurnTimeline.progress(0, 0))
    }

    @Test
    fun `content blend alpha is symmetric and non linear`() {
        assertEquals(0f, KindlePageTurnTimeline.blendAlpha(-1f))
        assertEquals(0.15625f, KindlePageTurnTimeline.blendAlpha(0.25f))
        assertEquals(0.5f, KindlePageTurnTimeline.blendAlpha(0.5f))
        assertEquals(0.84375f, KindlePageTurnTimeline.blendAlpha(0.75f))
        assertEquals(1f, KindlePageTurnTimeline.blendAlpha(2f))
    }
}
