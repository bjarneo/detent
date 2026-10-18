package app.detent.launcher

import kotlin.math.abs

/** Per-touch intent detection, independent of the dial engine. Distances use design units. */
internal class PullToLaunch(
    x: Float,
    y: Float,
    time: Long,
    private val left: Boolean,
    private val slop: Float,
    private val threshold: Float,
) {
    private var anchorX = x
    private var anchorY = y
    private var selected: String? = null
    private var stableSince = time
    private var started: Long? = null
    private var peak = 0f
    private val samples = ArrayDeque<Sample>().apply { add(Sample(x, y, time)) }
    var key: String? = null
        private set
    var armed = false
        private set
    var progress = 0f
        private set

    private data class Sample(val x: Float, val y: Float, val time: Long)

    /** Release may disarm, but must never arm a previously unarmed touch. */
    fun update(x: Float, y: Float, time: Long, selection: String?, release: Boolean = false) {
        samples.addLast(Sample(x, y, time))
        while (samples.size > 2 && time - samples.first().time > 100) samples.removeFirst()
        val first = samples.first()
        val dt = (time - first.time).toFloat().coerceIn(16f, 100f)
        val vx = abs(x - first.x) / dt
        val vy = abs(y - first.y) / dt
        // 350dp/s vertically and 900dp/s horizontally; the UI supplies a 40dp threshold.
        val unitsPerDp = threshold / 40f
        val slow = vy < .35f * unitsPerDp
        if (selection == null || selection != selected || !slow) {
            selected = selection
            stableSince = time
            reset(x, y)
            return
        }
        val inward = (x - anchorX) * if (left) 1f else -1f
        val vertical = abs(y - anchorY)
        if (started == null) {
            // Follow scrolling with the anchor: accumulated diagonal drift cannot become a pull.
            if (vertical > slop || inward < -slop || time - stableSince < 120) {
                reset(x, y)
                return
            }
            if (inward <= slop || inward < vertical * 2f || release) return
            started = time
            key = selection
        }
        if (vertical > slop || inward < slop || key != selection) {
            reset(x, y)
            return
        }
        if (armed && peak - inward > slop) {
            reset(x, y)
            return
        }
        peak = maxOf(peak, inward)
        progress = (inward / threshold).coerceIn(0f, 1f)
        // Once armed, tolerate sub-slop jitter around the threshold; retreat above cancels.
        val ready = (armed || inward >= threshold) && time - started!! >= 100 && vx < .9f * unitsPerDp
        armed = ready && (!release || armed)
        if (armed) progress = 1f
    }

    private fun reset(x: Float, y: Float) {
        anchorX = x
        anchorY = y
        started = null
        peak = 0f
        key = null
        progress = 0f
        armed = false
    }
}
