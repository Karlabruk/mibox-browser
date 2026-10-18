package com.mibox.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Ponteiro de "mouse" desenhado sobre a página, controlado pelas setas do controle remoto. */
class CursorView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    var cx = 200f
        private set
    var cy = 200f
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    }
    private val path = Path()

    init {
        isFocusable = false
        isClickable = false
    }

    fun moveTo(x: Float, y: Float) {
        cx = x
        cy = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        path.reset()
        path.moveTo(cx, cy)
        path.lineTo(cx, cy + 22 * d)
        path.lineTo(cx + 5 * d, cy + 17 * d)
        path.lineTo(cx + 9 * d, cy + 26 * d)
        path.lineTo(cx + 13 * d, cy + 24 * d)
        path.lineTo(cx + 9 * d, cy + 15 * d)
        path.lineTo(cx + 16 * d, cy + 15 * d)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)
    }
}
