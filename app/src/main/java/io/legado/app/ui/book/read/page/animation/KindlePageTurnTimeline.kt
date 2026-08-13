package io.legado.app.ui.book.read.page.animation

enum class KindlePageTurnDirection {
    NEXT,
    PREVIOUS,
}

data class KindlePageTurnFrame(
    val progress: Float,
    val direction: KindlePageTurnDirection,
    val transitionStartX: Float,
    val transitionEndX: Float,
) {

    fun targetAlphaAt(viewX: Float): Float {
        val transitionWidth = transitionEndX - transitionStartX
        if (transitionWidth <= 0f) {
            return when (direction) {
                KindlePageTurnDirection.NEXT -> if (viewX >= transitionStartX) 1f else 0f
                KindlePageTurnDirection.PREVIOUS -> if (viewX <= transitionStartX) 1f else 0f
            }
        }
        val leftToRightAlpha =
            ((viewX - transitionStartX) / transitionWidth).coerceIn(0f, 1f)
        val linearAlpha = when (direction) {
            KindlePageTurnDirection.NEXT -> leftToRightAlpha
            KindlePageTurnDirection.PREVIOUS -> 1f - leftToRightAlpha
        }
        return KindlePageTurnTimeline.blendAlpha(linearAlpha)
    }
}

object KindlePageTurnTimeline {

    const val DURATION_MILLIS = 200L
    const val DEFAULT_TRANSITION_FRACTION = 0.35f

    fun progress(elapsedMillis: Long, durationMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        return (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    }

    fun blendAlpha(linearAlpha: Float): Float {
        val alpha = linearAlpha.coerceIn(0f, 1f)
        return alpha * alpha * (3f - 2f * alpha)
    }

    fun frame(
        viewWidth: Int,
        direction: KindlePageTurnDirection,
        progress: Float,
        transitionFraction: Float = DEFAULT_TRANSITION_FRACTION,
    ): KindlePageTurnFrame {
        val width = viewWidth.coerceAtLeast(0).toFloat()
        val safeProgress = progress.coerceIn(0f, 1f)
        val transitionWidth = width * transitionFraction.coerceIn(0f, 1f)
        val travelDistance = width + transitionWidth
        val transitionStartX = when (direction) {
            KindlePageTurnDirection.NEXT -> width - safeProgress * travelDistance
            KindlePageTurnDirection.PREVIOUS -> -transitionWidth + safeProgress * travelDistance
        }
        return KindlePageTurnFrame(
            progress = safeProgress,
            direction = direction,
            transitionStartX = transitionStartX,
            transitionEndX = transitionStartX + transitionWidth,
        )
    }
}
