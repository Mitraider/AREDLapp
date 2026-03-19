package com.example.aredlapp.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class ColorSquarePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    private val indicatorStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private var hue: Float = 32f
    private var saturation: Float = 1f
    private var value: Float = 1f
    private var onSelectionChanged: ((Float, Float) -> Unit)? = null

    fun setHue(newHue: Float) {
        hue = newHue.coerceIn(0f, 360f)
        invalidateShaders()
        invalidate()
    }

    fun setSelection(newSaturation: Float, newValue: Float) {
        saturation = newSaturation.coerceIn(0f, 1f)
        value = newValue.coerceIn(0f, 1f)
        invalidate()
    }

    fun getSaturation(): Float = saturation

    fun getValue(): Float = value

    fun setOnSelectionChangedListener(listener: (Float, Float) -> Unit) {
        onSelectionChanged = listener
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateShaders()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), valuePaint)

        val x = saturation * width
        val y = (1f - value) * height
        val radius = max(16f, width.coerceAtMost(height) * 0.045f)
        canvas.drawCircle(x, y, radius, indicatorFillPaint)
        canvas.drawCircle(x, y, radius, indicatorStrokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_UP -> {
                updateSelection(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSelection(rawX: Float, rawY: Float) {
        if (width <= 0 || height <= 0) return
        saturation = (rawX / width).coerceIn(0f, 1f)
        value = (1f - (rawY / height)).coerceIn(0f, 1f)
        onSelectionChanged?.invoke(saturation, value)
        invalidate()
    }

    private fun invalidateShaders() {
        if (width <= 0 || height <= 0) return
        val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
        basePaint.shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            0f,
            Color.WHITE,
            hueColor,
            Shader.TileMode.CLAMP
        )
        valuePaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            Color.TRANSPARENT,
            Color.BLACK,
            Shader.TileMode.CLAMP
        )
    }
}
