package com.simplemobiletools.camera.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import com.simplemobiletools.camera.R
import kotlin.math.ceil
import kotlin.math.roundToInt

class ShadowDrawable(context: Context, private val drawable: Drawable, @StyleRes styleResId: Int) : Drawable() {

    companion object {
        private const val SHARED_BITMAP_BUFFER_SIZE = 640
        private val sharedDrawableBitmapBuffer = ThreadLocal<Bitmap>()
        private val sharedDrawableCanvasBuffer = ThreadLocal<Canvas>()
    }

    private val bitmapPaint = Paint()
    private val contentBounds = Rect()
    private val destRect = Rect()
    private val noRadiusShadowBounds = Rect()
    private val shadowBounds = Rect()
    private val shadowPaint = Paint()
    private val srcRect = Rect()
    private val unionBounds = Rect()

    private var outputBuffer: Bitmap? = null
    private var outputBufferCanvas: Canvas? = null
    private var paddingBottom = 0
    private var paddingEnd = 0
    private var paddingStart = 0
    private var paddingTop = 0
    private var shadowColor = 0
    private var shadowDx = 0
    private var shadowDy = 0
    private var shadowRadiusCeiling = 0

    constructor(context: Context, @DrawableRes drawableRes: Int, @StyleRes styleResId: Int) : this(
        context,
        ContextCompat.getDrawable(
            context,
            drawableRes,
        )!!,
        styleResId
    )

    init {
        drawable.callback = object : Callback {
            override fun unscheduleDrawable(drawable2: Drawable, runnable: Runnable) {
                unscheduleSelf(runnable)
            }

            override fun scheduleDrawable(drawable2: Drawable, runnable: Runnable, j: Long) {
                scheduleSelf(runnable, j)
            }

            override fun invalidateDrawable(drawable2: Drawable) {
                invalidateSelf()
            }
        }
        if (styleResId != 0) {
            val obtainStyledAttributes = context.obtainStyledAttributes(styleResId, R.styleable.ShadowDrawable)
            shadowColor =
                obtainStyledAttributes.getColor(R.styleable.ShadowDrawable_android_shadowColor, ContextCompat.getColor(context, com.simplemobiletools.commons.R.color.md_grey_400_dark))
            shadowDx = obtainStyledAttributes.getFloat(R.styleable.ShadowDrawable_android_shadowDx, 0f).toInt()
            shadowDy = obtainStyledAttributes.getFloat(R.styleable.ShadowDrawable_android_shadowDy, 0f).toInt()
            val shadowRadius = obtainStyledAttributes.getFloat(R.styleable.ShadowDrawable_android_shadowRadius, 0.0f).coerceAtLeast(0.0f)
            shadowRadiusCeiling = ceil(shadowRadius).toInt()
            val alpha = Color.alpha(shadowColor)
            if (shadowRadius > 0.0f && alpha > 0) {
                val blurMaskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
                shadowPaint.alpha = alpha
                shadowPaint.maskFilter = blurMaskFilter
            } else {
                shadowPaint.alpha = 0
            }
            obtainStyledAttributes.recycle()
        }
        shadowPaint.isAntiAlias = true
        shadowPaint.isFilterBitmap = true
    }

    override fun draw(canvas: Canvas) {
        val createBitmap: Bitmap
        val canvas2: Canvas
        val bounds = bounds
        contentBounds[bounds.left + paddingStart, bounds.top + paddingTop, bounds.right - paddingEnd] =
            bounds.bottom - paddingBottom
        if (contentBounds.width() <= 0 || contentBounds.height() <= 0) {
            return
        }
        if (shadowPaint.alpha == 0) {
            drawable.bounds = contentBounds
            drawable.draw(canvas)
            return
        }
        if (contentBounds.width() <= SHARED_BITMAP_BUFFER_SIZE && contentBounds.height() <= SHARED_BITMAP_BUFFER_SIZE) {
            var sharedBitmap = sharedDrawableBitmapBuffer.get()
            if (sharedBitmap != null) {
                sharedBitmap.eraseColor(0)
            } else {
                sharedBitmap = Bitmap.createBitmap(SHARED_BITMAP_BUFFER_SIZE, SHARED_BITMAP_BUFFER_SIZE, Bitmap.Config.ARGB_8888)
                sharedDrawableBitmapBuffer.set(sharedBitmap)
                sharedDrawableCanvasBuffer.set(Canvas(sharedBitmap!!))
            }
            createBitmap = sharedBitmap
            canvas2 = sharedDrawableCanvasBuffer.get()!!
        } else {
            createBitmap = Bitmap.createBitmap(
                contentBounds.width(),
                contentBounds.height(),
                Bitmap.Config.ARGB_8888
            )
            canvas2 = Canvas(createBitmap)
        }
        drawable.setBounds(0, 0, contentBounds.width(), contentBounds.height())
        drawable.draw(canvas2)
        shadowBounds.set(contentBounds)
        shadowBounds.offset(shadowDx, shadowDy)
        noRadiusShadowBounds.set(shadowBounds)
        val i = shadowRadiusCeiling
        shadowBounds.inset(-i, -i)
        unionBounds.set(bounds)
        unionBounds.union(contentBounds)
        unionBounds.union(shadowBounds)

        var outputBuffer = this.outputBuffer
        if (outputBuffer == null || unionBounds.width() > outputBuffer.width || unionBounds.height() > outputBuffer.height) {
            outputBuffer = Bitmap.createBitmap(
                unionBounds.width(),
                unionBounds.height(),
                Bitmap.Config.ARGB_8888
            )
            this.outputBuffer = outputBuffer
            this.outputBufferCanvas = Canvas(outputBuffer)
        } else {
            outputBuffer.eraseColor(0)
        }

        srcRect[0, 0, contentBounds.width()] = contentBounds.height()
        destRect.set(noRadiusShadowBounds)
        destRect.offset(-unionBounds.left, -unionBounds.top)
        outputBufferCanvas!!.drawBitmap(createBitmap.extractAlpha(), srcRect, destRect, shadowPaint)
        destRect.set(contentBounds)
        destRect.offset(-unionBounds.left, -unionBounds.top)
        outputBufferCanvas!!.drawBitmap(createBitmap, srcRect, destRect, bitmapPaint)
        srcRect[0, 0, unionBounds.width()] = unionBounds.height()
        destRect.set(unionBounds)
        canvas.drawBitmap(outputBuffer!!, srcRect, destRect, bitmapPaint)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int {
        return drawable.opacity
    }

    override fun setTint(tintColor: Int) {
        drawable.setTint(tintColor)
    }

    override fun setTintList(tint: ColorStateList?) {
        drawable.setTintList(tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        drawable.setTintMode(tintMode)
    }

    override fun getColorFilter(): ColorFilter? {
        return drawable.colorFilter
    }


    override fun getState(): IntArray {
        return drawable.state
    }

    override fun getIntrinsicHeight(): Int {
        return drawable.intrinsicHeight + paddingTop + paddingBottom
    }

    override fun getIntrinsicWidth(): Int {
        return drawable.intrinsicWidth + paddingStart + paddingEnd
    }

    override fun isStateful(): Boolean {
        return drawable.isStateful
    }

    override fun onLevelChange(i: Int): Boolean {
        return drawable.setLevel(i)
    }

    override fun getAlpha(): Int {
        return drawable.alpha
    }

    override fun setAlpha(i: Int) {
        drawable.alpha = i
        shadowPaint.alpha = (Color.alpha(shadowColor) / 255.0f * i).roundToInt()
    }

    override fun setAutoMirrored(z: Boolean) {
        drawable.isAutoMirrored = z
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawable.colorFilter = colorFilter
    }

    fun setPaddings(start: Int, top: Int, end: Int, bottom: Int) {
        paddingStart = start
        paddingTop = top
        paddingEnd = end
        paddingBottom = bottom
        invalidateSelf()
    }

    override fun setState(iArr: IntArray): Boolean {
        return drawable.setState(iArr)
    }
}
