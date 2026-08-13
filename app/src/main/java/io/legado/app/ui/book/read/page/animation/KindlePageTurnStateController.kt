package io.legado.app.ui.book.read.page.animation

internal enum class KindlePageTurnState {
    Idle,
    Animating,
    Returning,
    Committing,
}

internal data class KindlePageTurnSnapshot(
    val state: KindlePageTurnState,
    val direction: KindlePageTurnDirection?,
    val progress: Float,
    val generation: Long,
    val pendingSteps: Int,
)

internal sealed interface KindlePageTurnEffect {

    data class Animate(
        val generation: Long,
        val direction: KindlePageTurnDirection,
        val fromProgress: Float,
        val toProgress: Float,
        val requiresTargetPreparation: Boolean,
    ) : KindlePageTurnEffect

    data class Commit(
        val generation: Long,
        val direction: KindlePageTurnDirection,
    ) : KindlePageTurnEffect

    data object None : KindlePageTurnEffect
}

internal class KindlePageTurnStateController {

    private var state = KindlePageTurnState.Idle
    private var activeDirection: KindlePageTurnDirection? = null
    private var progress = 0f
    private var generation = 0L
    private var pendingSteps = 0

    val snapshot: KindlePageTurnSnapshot
        get() = KindlePageTurnSnapshot(
            state = state,
            direction = activeDirection,
            progress = progress,
            generation = generation,
            pendingSteps = pendingSteps,
        )

    fun request(direction: KindlePageTurnDirection): KindlePageTurnEffect {
        pendingSteps = (pendingSteps + direction.step).coerceIn(
            -MAX_PENDING_STEPS,
            MAX_PENDING_STEPS,
        )
        return when (state) {
            KindlePageTurnState.Idle -> startFromPending()
            KindlePageTurnState.Animating -> requestWhileAnimating()
            KindlePageTurnState.Returning -> requestWhileReturning()
            KindlePageTurnState.Committing -> KindlePageTurnEffect.None
        }
    }

    fun updateProgress(expectedGeneration: Long, value: Float): Boolean {
        if (generation != expectedGeneration ||
            state != KindlePageTurnState.Animating &&
            state != KindlePageTurnState.Returning
        ) {
            return false
        }
        progress = value.coerceIn(0f, 1f)
        return true
    }

    fun animationFinished(expectedGeneration: Long): KindlePageTurnEffect {
        if (generation != expectedGeneration) return KindlePageTurnEffect.None
        return when (state) {
            KindlePageTurnState.Animating -> {
                progress = 1f
                beginCommit()
            }

            KindlePageTurnState.Returning -> {
                progress = 0f
                state = KindlePageTurnState.Idle
                activeDirection = null
                generation += 1
                startFromPending()
            }

            else -> KindlePageTurnEffect.None
        }
    }

    fun forceFinish(expectedGeneration: Long): KindlePageTurnEffect {
        if (generation != expectedGeneration) return KindlePageTurnEffect.None
        return when (state) {
            KindlePageTurnState.Animating -> beginCommit()
            KindlePageTurnState.Returning -> {
                progress = 0f
                state = KindlePageTurnState.Idle
                activeDirection = null
                generation += 1
                startFromPending()
            }

            else -> KindlePageTurnEffect.None
        }
    }

    fun commitCompleted(
        expectedGeneration: Long,
        success: Boolean,
    ): KindlePageTurnEffect {
        if (generation != expectedGeneration || state != KindlePageTurnState.Committing) {
            return KindlePageTurnEffect.None
        }
        val committedDirection = activeDirection
        if (success && committedDirection != null) {
            pendingSteps -= committedDirection.step
        } else {
            pendingSteps = 0
        }
        progress = 0f
        state = KindlePageTurnState.Idle
        activeDirection = null
        return startFromPending()
    }

    fun targetUnavailable(expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        clear()
    }

    fun settleForShutdown(): KindlePageTurnEffect {
        pendingSteps = when (state) {
            KindlePageTurnState.Animating -> activeDirection?.step ?: 0
            else -> 0
        }
        return when (state) {
            KindlePageTurnState.Animating -> beginCommit()
            else -> {
                clear()
                KindlePageTurnEffect.None
            }
        }
    }

    fun clear() {
        generation += 1
        state = KindlePageTurnState.Idle
        activeDirection = null
        progress = 0f
        pendingSteps = 0
    }

    private fun requestWhileAnimating(): KindlePageTurnEffect {
        val direction = activeDirection ?: return clearAndReturnNone()
        return when {
            pendingSteps == 0 -> {
                state = KindlePageTurnState.Returning
                generation += 1
                KindlePageTurnEffect.Animate(
                    generation = generation,
                    direction = direction,
                    fromProgress = progress,
                    toProgress = 0f,
                    requiresTargetPreparation = false,
                )
            }

            pendingSteps.sign == direction.step -> beginCommit()
            else -> KindlePageTurnEffect.None
        }
    }

    private fun requestWhileReturning(): KindlePageTurnEffect {
        val direction = activeDirection ?: return clearAndReturnNone()
        return if (pendingSteps.sign == direction.step) {
            state = KindlePageTurnState.Animating
            generation += 1
            KindlePageTurnEffect.Animate(
                generation = generation,
                direction = direction,
                fromProgress = progress,
                toProgress = 1f,
                requiresTargetPreparation = false,
            )
        } else {
            KindlePageTurnEffect.None
        }
    }

    private fun beginCommit(): KindlePageTurnEffect {
        val direction = activeDirection ?: return clearAndReturnNone()
        state = KindlePageTurnState.Committing
        progress = 1f
        generation += 1
        return KindlePageTurnEffect.Commit(generation, direction)
    }

    private fun startFromPending(): KindlePageTurnEffect {
        if (state != KindlePageTurnState.Idle || pendingSteps == 0) {
            return KindlePageTurnEffect.None
        }
        val direction = if (pendingSteps > 0) {
            KindlePageTurnDirection.NEXT
        } else {
            KindlePageTurnDirection.PREVIOUS
        }
        activeDirection = direction
        progress = 0f
        state = KindlePageTurnState.Animating
        generation += 1
        return KindlePageTurnEffect.Animate(
            generation = generation,
            direction = direction,
            fromProgress = 0f,
            toProgress = 1f,
            requiresTargetPreparation = true,
        )
    }

    private fun clearAndReturnNone(): KindlePageTurnEffect {
        clear()
        return KindlePageTurnEffect.None
    }

    private val Int.sign: Int
        get() = when {
            this > 0 -> 1
            this < 0 -> -1
            else -> 0
        }

    private val KindlePageTurnDirection.step: Int
        get() = when (this) {
            KindlePageTurnDirection.NEXT -> 1
            KindlePageTurnDirection.PREVIOUS -> -1
        }

    private companion object {
        const val MAX_PENDING_STEPS = 1_000
    }
}

internal class KindlePageTurnInputLimiter(
    private val minimumRepeatIntervalMillis: Long,
) {

    private var lastAcceptedAtMillis = Long.MIN_VALUE

    fun shouldAccept(isRepeat: Boolean, nowMillis: Long): Boolean {
        if (!isRepeat) {
            lastAcceptedAtMillis = nowMillis
            return true
        }
        val elapsed = if (lastAcceptedAtMillis == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            nowMillis - lastAcceptedAtMillis
        }
        return if (elapsed < 0L || elapsed >= minimumRepeatIntervalMillis) {
            lastAcceptedAtMillis = nowMillis
            true
        } else {
            false
        }
    }

    fun reset() {
        lastAcceptedAtMillis = Long.MIN_VALUE
    }
}

internal class KindlePageTurnGestureTracker {

    var isMoved = false
        private set

    var pendingDirection: KindlePageTurnDirection? = null
        private set

    fun move(
        deltaX: Float,
        deltaY: Float,
        slopSquare: Int,
        targetAvailable: (KindlePageTurnDirection) -> Boolean,
    ): Boolean {
        if (isMoved) return true
        if (deltaX * deltaX + deltaY * deltaY <= slopSquare) return false
        isMoved = true
        val direction = if (deltaX > 0f) {
            KindlePageTurnDirection.PREVIOUS
        } else {
            KindlePageTurnDirection.NEXT
        }
        pendingDirection = direction.takeIf(targetAvailable)
        return true
    }

    fun release(): KindlePageTurnDirection? {
        val direction = pendingDirection
        reset()
        return direction
    }

    fun reset() {
        isMoved = false
        pendingDirection = null
    }
}

internal object KindlePageTurnAnimationPolicy {

    fun shouldAnimate(
        viewWidth: Int,
        viewHeight: Int,
        recordersReady: Boolean,
    ): Boolean {
        return viewWidth > 0 &&
            viewHeight > 0 &&
            recordersReady
    }
}
