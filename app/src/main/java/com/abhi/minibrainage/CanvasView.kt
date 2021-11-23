package com.abhi.minibrainage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.util.TypedValue

class CanvasView(context: Context): View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var path = Path()
    private var touchCount = 0

    init {
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        // Scale the stroke width with the device's size
        paint.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(event.x, event.y)
            } MotionEvent.ACTION_MOVE -> {
                path.lineTo(event.x, event.y)
            }
        }

        touchCount++
        invalidate()
        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    fun clear() {
        path = Path()
        touchCount = 0
        invalidate()
    }

    fun skipped(): Boolean {
        // Returns true if the user tapped submit without drawing anything
        return touchCount < 10
    }
}
