package io.github.russianranger.wurmlauncher

/** FIT_CENTER geometry, in normalized top-left game coordinates. */
object FramePointerGeometry {
    fun point(x: Float, y: Float, viewWidth: Int, viewHeight: Int, frameWidth: Int, frameHeight: Int,
              clamp: Boolean = false): Pair<Float, Float>? {
        if (!x.isFinite() || !y.isFinite() || minOf(viewWidth, viewHeight, frameWidth, frameHeight) <= 0) return null
        val scale = minOf(viewWidth.toFloat() / frameWidth, viewHeight.toFloat() / frameHeight)
        val width = frameWidth * scale; val height = frameHeight * scale
        val px = (x - (viewWidth - width) / 2) / width
        val py = (y - (viewHeight - height) / 2) / height
        if (!clamp && (px !in 0f..1f || py !in 0f..1f)) return null
        return px.coerceIn(0f, 1f) to py.coerceIn(0f, 1f)
    }
}

/** Ordered position/press/release; cancellation never leaves a touch button held. */
class TouchPointer(private val send: (String) -> Boolean) {
    var held = false
        private set
    fun down(point: Pair<Float, Float>): Boolean {
        cancel()
        if (!move(point)) return false
        held = send("BUTTON 0 1")
        return held
    }
    fun move(point: Pair<Float, Float>): Boolean = send("POINT ${point.first} ${point.second}")
    fun up(point: Pair<Float, Float>?) {
        if (!held) return
        if (point != null) move(point)
        cancel()
    }
    fun cancel() { if (held) { send("BUTTON 0 0"); held = false } }
}
