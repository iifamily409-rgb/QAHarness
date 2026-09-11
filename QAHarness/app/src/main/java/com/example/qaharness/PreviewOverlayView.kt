package com.example.qaharness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class PreviewOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var analysisResult: AnalysisResult? = null

    private val pieceStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 186, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val pieceFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 0, 186, 255)
        style = Paint.Style.FILL
    }

    private val pocketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val powerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 94, 255, 120)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
    }

    fun setAnalysisResult(result: AnalysisResult) {
        analysisResult = result
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val result = analysisResult ?: return
        val boardWidth = result.boardWidth.toFloat().coerceAtLeast(1f)
        val boardHeight = result.boardHeight.toFloat().coerceAtLeast(1f)

        val scale = min(width.toFloat() / boardWidth, height.toFloat() / boardHeight)
        val offsetX = (width.toFloat() - boardWidth * scale) / 2f
        val offsetY = (height.toFloat() - boardHeight * scale) / 2f

        for (pocket in result.pockets) {
            val cx = offsetX + pocket.centerX.toFloat() * scale
            val cy = offsetY + pocket.centerY.toFloat() * scale
            val radius = pocket.radius.toFloat() * scale
            canvas.drawCircle(cx, cy, radius, pocketPaint)
        }

        for (piece in result.pieces) {
            val cx = offsetX + piece.centerX.toFloat() * scale
            val cy = offsetY + piece.centerY.toFloat() * scale
            val radius = piece.radius.toFloat() * scale
            canvas.drawCircle(cx, cy, radius, pieceFillPaint)
            canvas.drawCircle(cx, cy, radius, pieceStrokePaint)
        }

        val shot = result.shot ?: return
        val cueX = offsetX + (boardWidth * 0.22f)
        val cueY = offsetY + (boardHeight * 0.58f)
        val lineEndX = cueX + (shot.aimVectorX.toFloat() * 220f * scale)
        val lineEndY = cueY + (shot.aimVectorY.toFloat() * 220f * scale)
        canvas.drawLine(cueX, cueY, lineEndX, lineEndY, aimPaint)

        val barBg = RectF(
            16f,
            16f,
            180f,
            42f
        )
        canvas.drawRoundRect(barBg, 8f, 8f, Paint().apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.FILL
        })

        val barValue = shot.power.coerceIn(0f, 1f)
        val barFg = RectF(
            barBg.left,
            barBg.top,
            barBg.left + (barBg.width() * barValue),
            barBg.bottom
        )
        canvas.drawRoundRect(barFg, 8f, 8f, powerPaint)

        canvas.drawText("Power ${String.format("%.2f", shot.power)}", 20f, 68f, textPaint)
    }
}
