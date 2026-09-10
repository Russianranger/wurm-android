package io.github.russianranger.wurmlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.Toast

/** Actual JVM image with a software pointer, positioned by game-thread metadata. */
class GameFrameView(context: Context, private val interactive: Boolean) : ImageView(context) {
    private var frameWidth = 0
    private var frameHeight = 0
    private var pointer: GraphicsFrame.Pointer? = null
    private var pointerId = -1
    private var bottomUp = false
    private val touch = TouchPointer { event ->
        val queued = ClientSession.send(event)
        if (!event.startsWith("POINT ")) ClientSession.log("[touch] TRANSLATE $event queued=$queued")
        queued
    }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    init {
        scaleType = ScaleType.FIT_CENTER
        contentDescription = "Wurm game view. Touch to select; right stick moves pointer; A or RT clicks."
        isClickable = interactive
    }
    fun frame(data: GraphicsFrame, rawBottomUp: Boolean = false) {
        bottomUp = rawBottomUp
        frameWidth = data.width; frameHeight = data.height; pointer = data.pointer
        invalidate()
    }
    fun clearFrame() { cancelTouch(); frameWidth = 0; frameHeight = 0; pointer = null; setImageDrawable(null) }
    fun cancelTouch() { touch.cancel(); pointerId = -1; parent?.requestDisallowInterceptTouchEvent(false) }
    private fun point(event: MotionEvent, index: Int, clamp: Boolean = false) =
        FramePointerGeometry.point(event.getX(index), event.getY(index), width, height, frameWidth, frameHeight, clamp)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return true
        if (!interactive || drawable == null) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = point(event, 0) ?: return false
                if (!ClientSession.inputReady()) {
                    Toast.makeText(context, "Game input is not ready yet.", Toast.LENGTH_SHORT).show()
                    return true
                }
                pointerId = event.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
                touch.down(p)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (touch.held && index >= 0) point(event, index, true)?.let(touch::move)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) {
                    touch.up(point(event, event.actionIndex, true)); cancelTouch()
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(canvas: Canvas) {
        if (bottomUp) {
            val saved=canvas.save()
            canvas.translate(0f,height.toFloat()); canvas.scale(1f,-1f)
            super.onDraw(canvas); canvas.restoreToCount(saved)
        } else super.onDraw(canvas)
        val p = pointer?.takeIf { interactive && it.visible && frameWidth > 1 && frameHeight > 1 } ?: return
        val scale = minOf(width.toFloat() / frameWidth, height.toFloat() / frameHeight)
        val x = (width - frameWidth * scale) / 2 + p.x * scale
        val y = (height - frameHeight * scale) / 2 + p.y * scale
        val arm = 7 * resources.displayMetrics.density
        // Contrasting crosshair outside the center leaves small menu labels readable.
        for ((color, stroke) in listOf(Color.BLACK to 3f, Color.CYAN to 1.5f)) {
            pen.color = color; pen.strokeWidth = stroke * resources.displayMetrics.density
            canvas.drawCircle(x, y, arm * .45f, pen)
            canvas.drawLine(x-arm,y,x-arm*.6f,y,pen); canvas.drawLine(x+arm*.6f,y,x+arm,y,pen)
            canvas.drawLine(x,y-arm,x,y-arm*.6f,pen); canvas.drawLine(x,y+arm*.6f,x,y+arm,pen)
        }
    }
}
