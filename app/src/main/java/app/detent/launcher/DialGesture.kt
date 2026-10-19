package app.detent.launcher

import kotlin.math.abs
import kotlin.math.hypot

/** A tap never changes the dial position. Only deliberate vertical movement starts a drag. */
internal class DialGesture(private val x0: Float, private val y0: Float, private val slop: Float) {
    var moved = false
        private set
    var dragging = false
        private set
    private var lastY = y0

    fun move(x: Float, y: Float): Float {
        val dx = x - x0
        val dy = y - y0
        if (hypot(dx, dy) > slop) moved = true
        if (!dragging) {
            if (abs(dy) <= slop || abs(dy) <= abs(dx)) return 0f
            dragging = true
            lastY = y
            // Discard the dead zone rather than jumping when it is crossed.
            return 0f
        }
        return (y - lastY).also { lastY = y }
    }
}
