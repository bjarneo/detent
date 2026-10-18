package app.detent.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import app.detent.launcher.Geo
import app.detent.launcher.LauncherState
import app.detent.launcher.Layout
import app.detent.launcher.Markers
import app.detent.launcher.Palette
import app.detent.launcher.Step
import app.detent.launcher.THEME_NAMES
import app.detent.launcher.W
import app.detent.launcher.fitScale
import app.detent.launcher.labelPose
import app.detent.launcher.mod
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun DialCanvas(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val measurer = rememberTextMeasurer(cacheSize = 160)
    val regular = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Normal, spacing = .01f) }
    val medium = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Medium, spacing = .01f) }

    val scrub = s.scrub
    val themeStep = s.ob == Step.THEME
    val apps = s.dialApps
    val names = scrub ?: if (themeStep) THEME_NAMES else remember(apps) { apps.map { it.display } }
    val dots = if (scrub == null && !themeStep) apps.map { s.hasDot(it) } else null
    val o = s.open
    val hide = if (o != null && o.fromDial && dots != null) apps.indexOfFirst { it.key == o.key } else -1
    val layout = s.layout
    val left = s.left
    val alpha by animateFloatAsState(
        if (s.ob == Step.WELCOME || s.ob == Step.APPS) 0f else 1f, tween(500), label = "dialAlpha",
    )

    val pull by animateFloatAsState(s.pullProgress, tween(90), label = "pullProgress")
    val hint = measurer.measure("release to open", regular)
    Canvas(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        val g = Geo(s.radius, u.cy)
        drawMarks(s.pos, g, u.k, pal, layout, left)
        drawLabels(s.pos, g, u.k, pal, layout, left, names, dots, hide, scrub != null, measurer, regular, medium)
        drawNotch(s.pawl, g, u.k, pal, layout.markers, left)
        if (pull > .01f) {
            val x = (if (left) W - 226f else 226f) * u.k
            val r = 16f * u.k
            drawArc(
                pal.accent, -90f, (if (left) 1f else -1f) * 360f * pull, false,
                Offset(x - r, u.cy * u.k - r), Size(r * 2, r * 2),
                alpha = if (s.pullArmed) 1f else .5f, style = Stroke(1.5f * u.k),
            )
            if (s.pullArmed) {
                drawText(
                    hint, pal.accent,
                    Offset(if (left) (W - 28f) * u.k - hint.size.width else 28f * u.k, (u.cy + 42f) * u.k),
                )
            }
        }
    }
}

private fun DrawScope.drawMarks(pos: Float, g: Geo, k: Float, pal: Palette, layout: Layout, left: Boolean) {
    val m = layout.markers
    fun x(v: Float) = (if (left) W - v else v) * k
    if (m == Markers.Ticks || m == Markers.Nodes) {
        drawCircle(pal.rim, g.r * k, Offset(x(g.cx), g.cy * k), style = Stroke(k))
    }
    val tpa = layout.ticksPerApp
    val first = ceil((pos - g.span) * tpa).toInt()
    val last = floor((pos + g.span) * tpa).toInt()
    for (i in first..last) {
        val d = i.toFloat() / tpa - pos
        val a = abs(d)
        if (a > g.span) continue
        val ang = d * g.step
        val px = g.cx - g.r * cos(ang)
        val py = g.cy + g.r * sin(ang)
        val major = mod(i, tpa) == 0
        val hot = major && a < .5f
        val fade = (1 - a / g.span).pow(.8f)
        when (m) {
            Markers.Ticks -> {
                val w = if (major) 14f else 6f
                val sg = if (left) -1f else 1f
                drawLine(
                    if (major) pal.tickMaj else pal.tickMin,
                    Offset(x(px), py * k),
                    Offset(x(px) + sg * w * cos(ang) * k, (py - w * sin(ang)) * k),
                    strokeWidth = k,
                    alpha = fade,
                )
            }
            Markers.Double -> {
                // Outer dots swell toward the notch. A fainter inner arc marks each app.
                val f = max(0f, 1 - a / 3.6f)
                val size = if (hot) 9f else if (major) 3 + 5 * f else 2 + 2.6f * f
                drawCircle(
                    if (hot) pal.accent else pal.dotMaj, size / 2 * k, Offset(x(px), py * k),
                    alpha = if (hot) 1f else (.3f + .7f * f) * fade,
                )
                if (major) {
                    val ri = g.r - 16
                    val s2 = 2.5f + 3 * f
                    drawCircle(
                        pal.dotMaj, s2 / 2 * k, Offset(x(g.cx - ri * cos(ang)), (g.cy + ri * sin(ang)) * k),
                        alpha = (.25f + .5f * f) * fade,
                    )
                }
            }
            else -> {
                val size = if (m == Markers.Nodes) (if (major) 6f else 2f) else (if (major) 4f else 2f)
                val c = when {
                    hot -> pal.accent
                    major -> pal.dotMaj
                    m == Markers.Dots -> pal.dotMin
                    else -> pal.nodeMin
                }
                drawCircle(c, size / 2 * k, Offset(x(px), py * k), alpha = fade)
            }
        }
    }
}

private fun DrawScope.drawLabels(
    pos: Float,
    g: Geo,
    k: Float,
    pal: Palette,
    layout: Layout,
    left: Boolean,
    names: List<String>,
    dots: List<Boolean>?,
    hide: Int,
    scrubbing: Boolean,
    measurer: TextMeasurer,
    regular: TextStyle,
    medium: TextStyle,
) {
    val n = names.size
    if (n == 0) return
    for (i in 0 until n) {
        var d = mod(i - pos, n.toFloat())
        if (d > n / 2f) d -= n
        val a = abs(d)
        if (a >= g.span) continue
        val cl = max(0f, 1 - a)
        val sel = cl > .5f
        val name = names[i]
        val s = if (scrubbing) 1 + .9f * cl * cl else min(1 + .45f * cl * cl, fitScale(name.length))
        val p = labelPose(d, name.length, s, g, layout.markers, left)
        val op = if (i == hide && a < .5f) 0f else (1 - a / g.span).pow(1.35f)
        if (op < .004f) continue
        val text = measurer.measure(name, if (sel) medium else regular)
        withTransform({
            translate(p.x * k, p.y * k)
            rotate(Math.toDegrees(p.rot.toDouble()).toFloat(), Offset.Zero)
            scale(s, s, Offset.Zero)
        }) {
            drawText(
                text,
                color = if (sel) pal.accent else pal.fg,
                topLeft = Offset(p.tx * text.size.width, -text.size.height / 2f),
                alpha = op,
            )
        }
        if (dots != null && dots[i]) drawCircle(pal.accent, 2.5f * k, Offset(p.bx * k, p.by * k), alpha = op)
    }
}

/** The fixed notch at the selected row. It kicks 4 units outward on every detent. */
private fun DrawScope.drawNotch(pawl: Float, g: Geo, k: Float, pal: Palette, m: Markers, left: Boolean) {
    val off = (if (left) 1f else -1f) * pawl * 4f
    fun x(v: Float) = ((if (left) W - v else v) + off) * k
    val cy = g.cy * k
    when (m) {
        Markers.Ticks -> drawRect(pal.accent, Offset(x(if (left) 242f else 204f), cy - k), Size(38 * k, 2 * k))
        Markers.Nodes -> drawCircle(pal.accent, 9.25f * k, Offset(x(226f), cy), style = Stroke(1.5f * k))
        Markers.Double -> {
            drawCircle(pal.bg, 8f * k, Offset(x(226f), cy))
            drawCircle(pal.accent, 5f * k, Offset(x(226f), cy))
        }
        Markers.Dots -> {
            val centres = floatArrayOf(191.5f, 200f, 209.5f)
            val sizes = floatArrayOf(3f, 4f, 5f)
            val alphas = floatArrayOf(.45f, .7f, 1f)
            for (i in 0..2) drawCircle(pal.accent, sizes[i] / 2 * k, Offset(x(centres[i]), cy), alpha = alphas[i])
        }
    }
}
