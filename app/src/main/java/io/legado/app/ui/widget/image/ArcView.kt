package io.legado.app.ui.widget.image

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.toColorInt
import io.legado.app.R

/**
 * 弧形View
 */
class ArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private var mWidth = 0
    private var mHeight = 0

    //弧形高度
    private val mArcHeight: Int

    //背景颜色
    private var mBgColor: Int
    private val mPaint: Paint = Paint().apply {
        isAntiAlias = true
    }
    private val mDirectionTop: Boolean
    val rect = Rect()
    val path = Path()

    init {
        var parsedArcHeight = 0
        var parsedBgColor = "#303F9F".toColorInt()
        var parsedDirectionTop = false
        context.withStyledAttributes(attrs, R.styleable.ArcView) {
            parsedArcHeight = getDimensionPixelSize(R.styleable.ArcView_arcHeight, parsedArcHeight)
            parsedBgColor = getColor(
                R.styleable.ArcView_bgColor,
                parsedBgColor
            )
            parsedDirectionTop = getBoolean(
                R.styleable.ArcView_arcDirectionTop,
                parsedDirectionTop
            )
        }
        mArcHeight = parsedArcHeight
        mBgColor = parsedBgColor
        mDirectionTop = parsedDirectionTop
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mPaint.style = Paint.Style.FILL
        mPaint.color = mBgColor
        if (mDirectionTop) {
            rect.set(0, mArcHeight, mWidth, mHeight)
            canvas.drawRect(rect, mPaint)
            path.reset()
            path.moveTo(0f, mArcHeight.toFloat())
            path.quadTo(mWidth / 2.toFloat(), 0f, mWidth.toFloat(), mArcHeight.toFloat())
            canvas.drawPath(path, mPaint)
        } else {
            rect.set(0, 0, mWidth, mHeight - mArcHeight)
            canvas.drawRect(rect, mPaint)
            path.reset()
            path.moveTo(0f, mHeight - mArcHeight.toFloat())
            path.quadTo(
                mWidth / 2.toFloat(),
                mHeight.toFloat(),
                mWidth.toFloat(),
                mHeight - mArcHeight.toFloat()
            )
            canvas.drawPath(path, mPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        if (widthMode == MeasureSpec.EXACTLY) {
            mWidth = widthSize
        }
        if (heightMode == MeasureSpec.EXACTLY) {
            mHeight = heightSize
        }
        setMeasuredDimension(mWidth, mHeight)
    }

    fun setBgColor(color: Int) {
        mBgColor = color
        invalidate()
    }
}
