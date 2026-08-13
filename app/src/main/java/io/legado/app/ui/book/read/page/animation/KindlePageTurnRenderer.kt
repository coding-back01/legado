package io.legado.app.ui.book.read.page.animation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader

class KindlePageTurnRenderer(
    private val transitionFraction: Float =
        KindlePageTurnTimeline.DEFAULT_TRANSITION_FRACTION,
) {

    private val maskPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }

    fun draw(
        canvas: Canvas,
        viewWidth: Int,
        viewHeight: Int,
        direction: KindlePageTurnDirection,
        progress: Float,
        drawStartPage: (Canvas) -> Unit,
        drawTargetPage: (Canvas) -> Unit,
    ) {
        val safeProgress = progress.coerceIn(0f, 1f)
        if (safeProgress <= 0f) {
            drawStartPage(canvas)
            return
        }
        if (safeProgress >= 1f || viewWidth <= 0 || viewHeight <= 0) {
            drawTargetPage(canvas)
            return
        }

        val frame = KindlePageTurnTimeline.frame(
            viewWidth = viewWidth,
            direction = direction,
            progress = safeProgress,
            transitionFraction = transitionFraction,
        )
        drawStartPage(canvas)
        val layer = canvas.saveLayer(
            0f,
            0f,
            viewWidth.toFloat(),
            viewHeight.toFloat(),
            null,
        )
        drawTargetPage(canvas)
        val maskColors = when (direction) {
            KindlePageTurnDirection.NEXT -> MASK_COLORS
            KindlePageTurnDirection.PREVIOUS -> MASK_COLORS_REVERSED
        }
        maskPaint.shader = LinearGradient(
            frame.transitionStartX,
            0f,
            frame.transitionEndX,
            0f,
            maskColors,
            MASK_POSITIONS,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(
            0f,
            0f,
            viewWidth.toFloat(),
            viewHeight.toFloat(),
            maskPaint,
        )
        maskPaint.shader = null
        canvas.restoreToCount(layer)
    }

    private companion object {
        val MASK_COLORS = intArrayOf(
            Color.TRANSPARENT,
            Color.argb(40, 255, 255, 255),
            Color.argb(128, 255, 255, 255),
            Color.argb(215, 255, 255, 255),
            Color.WHITE,
        )
        val MASK_COLORS_REVERSED = MASK_COLORS.reversedArray()
        val MASK_POSITIONS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    }
}
