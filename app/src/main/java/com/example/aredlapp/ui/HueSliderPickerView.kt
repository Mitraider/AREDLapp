package com.example.aredlapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class HueSliderPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    private val indicatorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val rect = RectF()
    private var hue: Float = 32f
    private var onHueChanged: ((Float) -> Unit)? = null

    fun setHue(newHue: Float) {
        hue = newHue.coerceIn(0f, 360f)
        invalidate()
    }

    fun getHue(): Float = hue

    fun setOnHueChangedListener(listener: (Float) -> Unit) {
        onHueChanged = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientPaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
            ),
            null,
            Shader.TileMode.CLAMP
        )
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cornerRadius = height / 2f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, gradientPaint)

        val x = width * (hue / 360f)
        val radius = max(14f, height * 0.42f)
        val cy = height / 2f
        canvas.drawCircle(x, cy, radius, indicatorFillPaint)
        canvas.drawCircle(x, cy, radius, indicatorStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                updateHue(event.x)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateHue(rawX: Float) {
        if (width <= 0) return
        hue = ((rawX / width).coerceIn(0f, 1f)) * 360f
        onHueChanged?.invoke(hue)
        invalidate()
    }
}
