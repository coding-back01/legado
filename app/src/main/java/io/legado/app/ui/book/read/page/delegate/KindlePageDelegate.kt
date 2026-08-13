package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.animation.KindlePageTurnAnimationPolicy
import io.legado.app.ui.book.read.page.animation.KindlePageTurnDirection
import io.legado.app.ui.book.read.page.animation.KindlePageTurnEffect
import io.legado.app.ui.book.read.page.animation.KindlePageTurnGestureTracker
import io.legado.app.ui.book.read.page.animation.KindlePageTurnInputLimiter
import io.legado.app.ui.book.read.page.animation.KindlePageTurnRenderer
import io.legado.app.ui.book.read.page.animation.KindlePageTurnState
import io.legado.app.ui.book.read.page.animation.KindlePageTurnStateController
import io.legado.app.ui.book.read.page.animation.KindlePageTurnTimeline
import io.legado.app.ui.book.read.page.entities.PageDirection
import kotlin.math.abs
import kotlin.math.roundToLong

internal class KindlePageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {

    private val renderer = KindlePageTurnRenderer()
    private val controller = KindlePageTurnStateController()
    private val gestureTracker = KindlePageTurnGestureTracker()
    private val keyRepeatLimiter = KindlePageTurnInputLimiter(ANIMATION_DURATION_MILLIS)
    private var animationRun: AnimationRun? = null
    private var gestureDirection: PageDirection = PageDirection.NONE
    private var internalPageCommit = false
    private var destroyed = false
    private var postedFallbackGeneration = Long.MIN_VALUE

    override fun onDown() {
        gestureTracker.reset()
        gestureDirection = PageDirection.NONE
        if (controller.snapshot.state == KindlePageTurnState.Idle) {
            super.onDown()
        } else {
            isMoved = false
            noNext = false
            isCancel = false
        }
    }

    override fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> gestureDirection = PageDirection.NONE
            MotionEvent.ACTION_MOVE -> detectGesture(event)
            MotionEvent.ACTION_UP -> {
                gestureTracker.release()?.let { requestTurn(it.toPageDirection()) }
                gestureDirection = PageDirection.NONE
            }

            MotionEvent.ACTION_CANCEL -> {
                gestureTracker.reset()
                gestureDirection = PageDirection.NONE
                isMoved = false
            }
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        requestTurn(PageDirection.NEXT)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        requestTurn(PageDirection.PREV)
    }

    override fun keyTurnPage(direction: PageDirection) {
        keyTurnPage(direction, isRepeat = false)
    }

    fun keyTurnPage(direction: PageDirection, isRepeat: Boolean) {
        if (direction == PageDirection.NONE ||
            !keyRepeatLimiter.shouldAccept(isRepeat, SystemClock.uptimeMillis())
        ) {
            return
        }
        requestTurn(direction)
    }

    fun onKeyReleased() {
        keyRepeatLimiter.reset()
    }

    override fun computeScroll() {
        val run = animationRun ?: return
        val snapshot = controller.snapshot
        if (snapshot.generation != run.generation ||
            snapshot.state != KindlePageTurnState.Animating &&
            snapshot.state != KindlePageTurnState.Returning
        ) {
            animationRun = null
            syncFlags()
            return
        }
        val elapsed = SystemClock.uptimeMillis() - run.startedAtMillis
        val fraction = (elapsed.toFloat() / run.durationMillis).coerceIn(0f, 1f)
        val progress = run.fromProgress + (run.toProgress - run.fromProgress) * fraction
        if (!controller.updateProgress(run.generation, progress)) {
            animationRun = null
            syncFlags()
            return
        }
        if (fraction >= 1f) {
            animationRun = null
            handleEffect(controller.animationFinished(run.generation))
        } else {
            readView.postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val snapshot = controller.snapshot
        val direction = snapshot.direction ?: return
        if (snapshot.state != KindlePageTurnState.Animating &&
            snapshot.state != KindlePageTurnState.Returning
        ) {
            return
        }
        try {
            renderer.draw(
                canvas = canvas,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                direction = direction,
                progress = snapshot.progress,
                drawStartPage = { curRecorder.draw(it) },
                drawTargetPage = { targetRecorder(direction).draw(it) },
            )
        } catch (_: Throwable) {
            postRenderFallback(snapshot.generation)
        }
    }

    override fun onAnimStart(animationSpeed: Int) {
        requestTurn(mDirection)
    }

    override fun onAnimStop() = Unit

    override fun abortAnim() {
        animationRun = null
        val effect = controller.settleForShutdown()
        if (effect is KindlePageTurnEffect.Commit) {
            commitPage(effect, continuePending = false)
        }
        controller.clear()
        syncFlags()
        readView.isAbortAnim = false
        readView.invalidate()
    }

    override fun setViewSize(width: Int, height: Int) {
        val sizeChanged = viewWidth > 0 && viewHeight > 0 &&
            (viewWidth != width || viewHeight != height)
        if (sizeChanged && controller.snapshot.state != KindlePageTurnState.Idle) {
            abortAnim()
        }
        super.setViewSize(width, height)
    }

    override fun onCurrentPageChanged() {
        if (internalPageCommit || controller.snapshot.state == KindlePageTurnState.Idle) return
        animationRun = null
        controller.clear()
        syncFlags()
        readView.invalidate()
    }

    override fun onDestroy() {
        if (!destroyed) {
            destroyed = true
            animationRun = null
            controller.clear()
        }
        super.onDestroy()
    }

    private fun detectGesture(event: MotionEvent) {
        val deltaX = event.x - startX
        val deltaY = event.y - startY
        val crossedThreshold = gestureTracker.move(
            deltaX = deltaX,
            deltaY = deltaY,
            slopSquare = readView.pageSlopSquare2,
        ) { direction ->
            when (direction) {
                KindlePageTurnDirection.PREVIOUS -> hasPrev()
                KindlePageTurnDirection.NEXT -> hasNext()
            }
        }
        if (!crossedThreshold) return
        isMoved = true
        gestureDirection = gestureTracker.pendingDirection?.toPageDirection()
            ?: PageDirection.NONE
        if (gestureDirection == PageDirection.NONE) {
            noNext = true
        }
    }

    private fun requestTurn(direction: PageDirection) {
        if (destroyed || direction == PageDirection.NONE) return
        isCancel = false
        handleEffect(controller.request(direction.toKindleDirection()))
    }

    private fun handleEffect(initialEffect: KindlePageTurnEffect) {
        var effect = initialEffect
        var chainedEffects = 0
        while (effect != KindlePageTurnEffect.None && chainedEffects < MAX_CHAINED_EFFECTS) {
            chainedEffects += 1
            effect = when (effect) {
                is KindlePageTurnEffect.Animate -> handleAnimate(effect)
                is KindlePageTurnEffect.Commit -> commitPage(effect, continuePending = true)
                KindlePageTurnEffect.None -> KindlePageTurnEffect.None
            }
        }
        if (chainedEffects >= MAX_CHAINED_EFFECTS) {
            controller.clear()
            animationRun = null
        }
        syncFlags()
        readView.invalidate()
    }

    private fun handleAnimate(effect: KindlePageTurnEffect.Animate): KindlePageTurnEffect {
        if (effect.requiresTargetPreparation) {
            when (prepareTarget(effect)) {
                TargetPreparation.Missing -> {
                    controller.targetUnavailable(effect.generation)
                    return KindlePageTurnEffect.None
                }

                TargetPreparation.Failed -> return controller.forceFinish(effect.generation)
                TargetPreparation.Ready -> Unit
            }
        }
        val shouldAnimate = KindlePageTurnAnimationPolicy.shouldAnimate(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            recordersReady = recordersReady(effect.direction),
        )
        if (!shouldAnimate) {
            return controller.forceFinish(effect.generation)
        }
        launchAnimation(effect)
        return KindlePageTurnEffect.None
    }

    private fun prepareTarget(effect: KindlePageTurnEffect.Animate): TargetPreparation {
        val pageDirection = effect.direction.toPageDirection()
        val hasTarget = when (pageDirection) {
            PageDirection.PREV -> hasPrev()
            PageDirection.NEXT -> hasNext()
            PageDirection.NONE -> false
        }
        if (!hasTarget) return TargetPreparation.Missing
        return try {
            super.setDirection(pageDirection)
            if (recordersReady(effect.direction)) {
                TargetPreparation.Ready
            } else {
                TargetPreparation.Failed
            }
        } catch (_: Throwable) {
            TargetPreparation.Failed
        }
    }

    private fun launchAnimation(effect: KindlePageTurnEffect.Animate) {
        val distance = abs(effect.toProgress - effect.fromProgress)
        val duration = (ANIMATION_DURATION_MILLIS * distance)
            .roundToLong()
            .coerceAtLeast(1L)
        animationRun = AnimationRun(
            generation = effect.generation,
            fromProgress = effect.fromProgress,
            toProgress = effect.toProgress,
            startedAtMillis = SystemClock.uptimeMillis(),
            durationMillis = duration,
        )
        mDirection = effect.direction.toPageDirection()
        isStarted = true
        isRunning = true
        readView.postInvalidateOnAnimation()
    }

    private fun commitPage(
        effect: KindlePageTurnEffect.Commit,
        continuePending: Boolean,
    ): KindlePageTurnEffect {
        animationRun = null
        isStarted = false
        isRunning = false
        internalPageCommit = true
        val success = try {
            readView.fillPage(effect.direction.toPageDirection())
        } finally {
            internalPageCommit = false
        }
        if (!continuePending) return KindlePageTurnEffect.None
        return controller.commitCompleted(effect.generation, success)
    }

    private fun postRenderFallback(generation: Long) {
        if (postedFallbackGeneration == generation) return
        postedFallbackGeneration = generation
        readView.post {
            if (postedFallbackGeneration == generation) {
                postedFallbackGeneration = Long.MIN_VALUE
                handleEffect(controller.forceFinish(generation))
            }
        }
    }

    private fun recordersReady(direction: KindlePageTurnDirection): Boolean {
        val target = targetRecorder(direction)
        return curRecorder.width == viewWidth &&
            curRecorder.height == viewHeight &&
            target.width == viewWidth &&
            target.height == viewHeight
    }

    private fun targetRecorder(direction: KindlePageTurnDirection) = when (direction) {
        KindlePageTurnDirection.NEXT -> nextRecorder
        KindlePageTurnDirection.PREVIOUS -> prevRecorder
    }

    private fun syncFlags() {
        val state = controller.snapshot.state
        val active = state == KindlePageTurnState.Animating ||
            state == KindlePageTurnState.Returning
        isRunning = active
        isStarted = active && animationRun != null
        if (state == KindlePageTurnState.Idle) {
            isMoved = false
            isCancel = false
        }
    }

    private fun PageDirection.toKindleDirection() = when (this) {
        PageDirection.NEXT -> KindlePageTurnDirection.NEXT
        PageDirection.PREV -> KindlePageTurnDirection.PREVIOUS
        PageDirection.NONE -> error("NONE cannot start a Kindle page turn")
    }

    private fun KindlePageTurnDirection.toPageDirection() = when (this) {
        KindlePageTurnDirection.NEXT -> PageDirection.NEXT
        KindlePageTurnDirection.PREVIOUS -> PageDirection.PREV
    }

    private data class AnimationRun(
        val generation: Long,
        val fromProgress: Float,
        val toProgress: Float,
        val startedAtMillis: Long,
        val durationMillis: Long,
    )

    private enum class TargetPreparation {
        Ready,
        Missing,
        Failed,
    }

    companion object {
        const val ANIMATION_DURATION_MILLIS = KindlePageTurnTimeline.DURATION_MILLIS
        private const val MAX_CHAINED_EFFECTS = 1_002
    }
}
