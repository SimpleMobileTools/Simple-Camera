package com.simplemobiletools.camera.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.view.ViewGroup
import com.simplemobiletools.camera.extensions.config

class FocusRectView(context: Context) : ViewGroup(context) {
    private val RECT_SIZE = 50
    private val RECT_DURATION = 500

    private var mDrawRect = false
    private var mHandler: Handler

    lateinit var mPaint: Paint
    lateinit var mRect: Rect

    init {
        setWillNotDraw(false)
        mHandler = Handler()
        setupPaint()
    }

    private fun setupPaint() {
        mPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = context.config.primaryColor
            strokeWidth = 2f
        }
    }

    fun setStrokeColor(color: Int) {
        mPaint.color = color
    }

    fun drawFocusRect(x: Int, y: Int) {
        mRect = Rect(x - RECT_SIZE, y - RECT_SIZE, x + RECT_SIZE, y + RECT_SIZE)
        toggleRect(true)

        mHandler.removeCallbacksAndMessages(null)
        mHandler.postDelayed({
            toggleRect(false)
        }, RECT_DURATION.toLong())
    }

    private fun toggleRect(show: Boolean) {
        mDrawRect = show
        invalidate()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mDrawRect) {
            canvas.drawRect(mRect, mPaint)
        }
    }
}
