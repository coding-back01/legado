package io.legado.app.ui.book.read.page.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KindlePageTurnStateControllerTest {

    @Test
    fun `twenty same direction inputs commit twenty pages without loss`() {
        val controller = KindlePageTurnStateController()
        var effect = controller.request(KindlePageTurnDirection.NEXT)
        var commits = 0

        repeat(19) {
            val animation = effect as KindlePageTurnEffect.Animate
            assertTrue(controller.updateProgress(animation.generation, 0.4f))
            effect = controller.request(KindlePageTurnDirection.NEXT)
            val commit = effect as KindlePageTurnEffect.Commit
            commits += 1
            effect = controller.commitCompleted(commit.generation, success = true)
        }

        val lastAnimation = effect as KindlePageTurnEffect.Animate
        assertTrue(controller.updateProgress(lastAnimation.generation, 1f))
        val lastCommit = controller.animationFinished(lastAnimation.generation)
            as KindlePageTurnEffect.Commit
        commits += 1
        effect = controller.commitCompleted(lastCommit.generation, success = true)

        assertEquals(20, commits)
        assertEquals(KindlePageTurnEffect.None, effect)
        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)
        assertEquals(0, controller.snapshot.pendingSteps)
    }

    @Test
    fun `opposite input returns to start with zero net page change`() {
        val controller = KindlePageTurnStateController()
        val animation = controller.request(KindlePageTurnDirection.NEXT)
            as KindlePageTurnEffect.Animate
        controller.updateProgress(animation.generation, 0.65f)

        val returning = controller.request(KindlePageTurnDirection.PREVIOUS)
            as KindlePageTurnEffect.Animate

        assertEquals(KindlePageTurnState.Returning, controller.snapshot.state)
        assertEquals(0.65f, returning.fromProgress)
        assertEquals(0f, returning.toProgress)
        assertEquals(
            KindlePageTurnEffect.None,
            controller.animationFinished(returning.generation),
        )
        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)
        assertEquals(0, controller.snapshot.pendingSteps)
    }

    @Test
    fun `extra opposite input during return starts previous page after return`() {
        val controller = KindlePageTurnStateController()
        val animation = controller.request(KindlePageTurnDirection.NEXT)
            as KindlePageTurnEffect.Animate
        controller.updateProgress(animation.generation, 0.5f)
        val returning = controller.request(KindlePageTurnDirection.PREVIOUS)
            as KindlePageTurnEffect.Animate

        assertEquals(
            KindlePageTurnEffect.None,
            controller.request(KindlePageTurnDirection.PREVIOUS),
        )
        val previousAnimation = controller.animationFinished(returning.generation)
            as KindlePageTurnEffect.Animate

        assertEquals(KindlePageTurnDirection.PREVIOUS, previousAnimation.direction)
        assertTrue(previousAnimation.requiresTargetPreparation)
    }

    @Test
    fun `old generation callbacks cannot mutate current animation`() {
        val controller = KindlePageTurnStateController()
        val first = controller.request(KindlePageTurnDirection.NEXT)
            as KindlePageTurnEffect.Animate
        controller.updateProgress(first.generation, 0.4f)
        val returning = controller.request(KindlePageTurnDirection.PREVIOUS)
            as KindlePageTurnEffect.Animate

        assertFalse(controller.updateProgress(first.generation, 1f))
        assertEquals(
            KindlePageTurnEffect.None,
            controller.animationFinished(first.generation),
        )
        assertEquals(returning.generation, controller.snapshot.generation)
        assertEquals(KindlePageTurnState.Returning, controller.snapshot.state)
    }

    @Test
    fun `missing target and failed commit clear pending intent`() {
        val controller = KindlePageTurnStateController()
        val missing = controller.request(KindlePageTurnDirection.NEXT)
            as KindlePageTurnEffect.Animate
        controller.targetUnavailable(missing.generation)

        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)
        assertEquals(0, controller.snapshot.pendingSteps)

        val animation = controller.request(KindlePageTurnDirection.PREVIOUS)
            as KindlePageTurnEffect.Animate
        val commit = controller.forceFinish(animation.generation)
            as KindlePageTurnEffect.Commit
        assertEquals(
            KindlePageTurnEffect.None,
            controller.commitCompleted(commit.generation, success = false),
        )
        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)
        assertEquals(0, controller.snapshot.pendingSteps)
    }

    @Test
    fun `independent key presses are accepted while repeats are rate limited`() {
        val limiter = KindlePageTurnInputLimiter(200L)

        assertTrue(limiter.shouldAccept(isRepeat = false, nowMillis = 1_000L))
        assertFalse(limiter.shouldAccept(isRepeat = true, nowMillis = 1_100L))
        assertTrue(limiter.shouldAccept(isRepeat = true, nowMillis = 1_200L))
        assertTrue(limiter.shouldAccept(isRepeat = false, nowMillis = 1_230L))
        assertFalse(limiter.shouldAccept(isRepeat = true, nowMillis = 1_429L))
        assertTrue(limiter.shouldAccept(isRepeat = true, nowMillis = 1_430L))
    }

    @Test
    fun `gesture waits for release and respects direction threshold and missing target`() {
        val tracker = KindlePageTurnGestureTracker()
        val controller = KindlePageTurnStateController()

        assertFalse(tracker.move(-3f, 4f, 25) { true })
        assertEquals(null, tracker.release())
        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)

        assertTrue(tracker.move(-10f, 0f, 25) { true })
        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)
        val next = tracker.release()
        assertEquals(KindlePageTurnDirection.NEXT, next)
        val nextAnimation = controller.request(next!!)
        assertTrue(nextAnimation is KindlePageTurnEffect.Animate)

        controller.clear()
        assertTrue(tracker.move(10f, 0f, 25) { true })
        assertEquals(KindlePageTurnDirection.PREVIOUS, tracker.release())

        assertTrue(tracker.move(-10f, 0f, 25) { false })
        assertEquals(null, tracker.release())
        assertEquals(KindlePageTurnState.Idle, controller.snapshot.state)
    }

    @Test
    fun `animation policy fails closed for invalid renderer prerequisites`() {
        assertTrue(KindlePageTurnAnimationPolicy.shouldAnimate(1080, 1440, true))
        assertFalse(KindlePageTurnAnimationPolicy.shouldAnimate(0, 1440, true))
        assertFalse(KindlePageTurnAnimationPolicy.shouldAnimate(1080, 0, true))
        assertFalse(KindlePageTurnAnimationPolicy.shouldAnimate(1080, 1440, false))
    }
}
