package id.local.pencatatan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.min

data class ExpenseSlice(
    val label: String,
    val value: Long,
    val color: Int
)

data class CenterTextLine(
    val text: String,
    val color: Int,
    val textSizeSp: Float,
    val typefaceStyle: Int
)

class ExpensePieChartView(context: Context) : View(context) {
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val oval = RectF()
    private var slices: List<ExpenseSlice> = emptyList()
    private var centerLines: List<CenterTextLine> = emptyList()

    fun setChartData(slices: List<ExpenseSlice>, centerLines: List<CenterTextLine>) {
        this.slices = slices
        this.centerLines = centerLines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        val chartSize = min(availableWidth, availableHeight).toFloat()
        if (chartSize <= 0f) return

        val centerX = paddingLeft + availableWidth / 2f
        val centerY = paddingTop + availableHeight / 2f
        val strokeWidth = chartSize * 0.16f
        val radius = chartSize * 0.34f
        arcPaint.strokeWidth = strokeWidth
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        val total = slices.sumOf { it.value }
        if (total <= 0L) {
            arcPaint.color = Color.rgb(219, 225, 232)
            canvas.drawArc(oval, -90f, 360f, false, arcPaint)
        } else {
            var startAngle = -90f
            slices.forEach { slice ->
                val sweepAngle = (slice.value.toFloat() / total.toFloat()) * 360f
                arcPaint.color = slice.color
                canvas.drawArc(oval, startAngle, sweepAngle, false, arcPaint)
                startAngle += sweepAngle
            }
        }

        drawCenterText(canvas, centerX, centerY)
    }

    private fun drawCenterText(canvas: Canvas, centerX: Float, centerY: Float) {
        if (centerLines.isEmpty()) return

        val lineHeight = sp(18f)
        val firstBaseline = centerY - (lineHeight * (centerLines.size - 1) / 2f)
        centerLines.forEachIndexed { index, line ->
            textPaint.color = line.color
            textPaint.textSize = sp(line.textSizeSp)
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, line.typefaceStyle)
            val metrics = textPaint.fontMetrics
            val baseline = firstBaseline + (lineHeight * index) - ((metrics.ascent + metrics.descent) / 2f)
            canvas.drawText(line.text, centerX, baseline, textPaint)
        }
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }
}
