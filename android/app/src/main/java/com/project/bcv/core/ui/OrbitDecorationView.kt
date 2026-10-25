package com.project.bcv.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Decorative orbit shared by onboarding screens; its parent crops the right side. */
class OrbitDecorationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(233, 243, 231) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(214, 232, 213)
        style = Paint.Style.STROKE
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(233, 161, 94) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = min(width, height) / 2f
        val centerX = width / 2f
        val centerY = height / 2f
        ring.strokeWidth = radius * 0.0125f
        canvas.drawCircle(centerX, centerY, radius, disc)
        canvas.drawCircle(centerX, centerY, radius * 0.65f, ring)
        canvas.drawCircle(centerX, centerY, radius * 0.325f, ring)
        val angle = Math.toRadians(232.0)
        val orbitRadius = radius * 0.65f
        canvas.drawCircle(
            centerX + orbitRadius * cos(angle).toFloat(),
            centerY + orbitRadius * sin(angle).toFloat(),
            radius * 0.0625f,
            dot
        )
    }
}
