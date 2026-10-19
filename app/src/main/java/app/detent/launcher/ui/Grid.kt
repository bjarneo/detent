package app.detent.launcher.ui

import android.graphics.Paint
import app.detent.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import app.detent.launcher.CH
import app.detent.launcher.G
import app.detent.launcher.GAP
import app.detent.launcher.GO
import app.detent.launcher.Geo
import app.detent.launcher.Kind
import app.detent.launcher.LENS_GAP
import app.detent.launcher.LX
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import app.detent.launcher.Phase
import app.detent.launcher.RC
import app.detent.launcher.RF
import app.detent.launcher.RR
import app.detent.launcher.RX
import app.detent.launcher.Step
import app.detent.launcher.THEME_NAMES
import app.detent.launcher.W
import app.detent.launcher.labelPoseAt
import app.detent.launcher.mod
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val InOutCubic = Easing { p -> if (p < .5f) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2 }

private fun c01(v: Float) = v.coerceIn(0f, 1f)

/** Dot positions in batches, so that one call draws all the dots of the same size and colour. */
private class Dots {
    var pts = FloatArray(0)
    var n = 0

    fun reset(cap: Int) {
        if (pts.size < cap) pts = FloatArray(cap)
        n = 0
    }

    fun add(vararg v: Float) {
        for (x in v) pts[n++] = x
    }
}

/** The grid layouts: field, lens and readout. The whole screen is one grid of dots. */
@Composable
fun DotGrid(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val measurer = rememberTextMeasurer(cacheSize = 160)
    val regular = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Normal, spacing = .01f) }
    val medium = remember(u) { textStyle(u, 15f, pal.fg, FontWeight.Medium, spacing = .01f) }

    val kind = s.kind
    val scrub = s.scrub
    val themeStep = s.ob == Step.THEME
    val apps = s.dialApps
    val names = scrub ?: if (themeStep) THEME_NAMES else remember(apps) { apps.map { it.display } }
    val unread = if (scrub == null && !themeStep) apps.map { s.hasDot(it) } else null
    val o = s.open

    val dial by animateFloatAsState(
        if (s.ob == Step.WELCOME || s.ob == Step.APPS) 0f else 1f, tween(500), label = "gridDial",
    )
    val labels by animateFloatAsState(if (o != null) 0f else 1f, tween(200), label = "gridLabels")
    val read by animateFloatAsState(
        when {
            s.ob != null -> 0f
            scrub != null && kind != Kind.Readout -> .25f
            else -> 1f
        },
        tween(400), label = "gridReadout",
    )

    // Opening an app: a ripple of dots runs out from the notch and floods the screen.
    val rip = remember { Animatable(0f) }
    LaunchedEffect(o?.id, o?.phase) {
        when {
            o == null || !o.ripple || o.phase == Phase.From -> rip.snapTo(0f)
            o.phase == Phase.In -> rip.animateTo(1f, tween(640, easing = InOutCubic))
            else -> rip.animateTo(0f, tween(540, delayMillis = 140, easing = InOutCubic))
        }
    }

    val base = remember { Dots() }
    val dim = remember { Dots() }
    val unlit = remember { Dots() }
    val flood = remember { Dots() }
    val floodRead = remember { Dots() }
    val vary = remember { Dots() }
    val rg = remember { FloatArray(3) }
    val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val point = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND } }

    Canvas(Modifier.fillMaxSize()) {
        s.readTick
        val k = u.k
        val hd = u.hd
        val left = s.left
        val pos = s.pos
        val pawl = s.pawl
        fun mir(x: Float) = if (left) W - x else x

        val rows = ((hd - GO) / G).toInt() + 2
        val cap = 34 * rows * 2
        base.reset(cap)
        dim.reset(cap)
        unlit.reset(RC * RR * 2)
        flood.reset(cap)
        floodRead.reset(RC * RR * 2)
        vary.reset((34 * rows + RC * RR) * 5)

        val rr = if (o != null && o.ripple) rip.value * (maxOf(
            hypot(o.rx, o.ry), hypot(W - o.rx, o.ry), hypot(o.rx, hd - o.ry), hypot(W - o.rx, hd - o.ry),
        ) + 150) else -1f
        val rx = o?.rx ?: 0f
        val ry = o?.ry ?: 0f

        /**
         * Grows a dot inside the ripple: an accent ring at the front, then the sheet colour. Writes the new
         * radius, alpha and colour to [rg]. Returns 0 outside the ripple, 1 inside it, and 2 when the dot is flooded.
         */
        fun ripple(x: Float, y: Float, r: Float, a: Float, front: Float, full: Float): Int {
            val g = rr - hypot(x - rx, y - ry)
            if (g <= 0) return 0
            if (g < 30) {
                rg[0] = max(r, front)
                rg[1] = max(a, .85f)
                rg[2] = 1f
                return 1
            }
            val q = InOutCubic.transform(c01((g - 30) / 110))
            if (q >= 1f) return 2
            rg[0] = r + (full - r) * q
            rg[1] = a + (1 - a) * q
            rg[2] = 2f
            return 1
        }

        val readY = s.readY
        val n = names.size
        val cur = pos.roundToInt()

        // Dial geometry for field and readout.
        val r0 = s.radius
        val ax = s.layout.ax
        val cy = s.cy
        val cx = ax + r0
        val step = GAP / r0
        val tmax = min(6.5f, 1.55f / step) * step

        // Lens geometry. The lens sits at the notch and the column scrolls through it. The lens fades with the dial.
        val lsE = s.ls * dial
        val lensL = 70 + 60 * lsE
        val lensK = .9f * lsE
        val top = s.lensTop
        val bottom = s.lensBottom

        var j = 0
        while (j < rows) {
            val y0 = GO + j * G
            j++
            for (i in 0 until 34) {
                val x0 = GO + i * G
                val sx = mir(x0)
                if (y0 > readY - 4 && y0 < readY + 80 && sx > 16 && sx < 390) continue
                var x = x0
                var y = y0
                var r = 1.2f
                var a = .16f
                var col = 0f
                var special = false
                var inner = false
                if (kind == Kind.Lens) {
                    // The column moves, so its dots are drawn below.
                    if (x0 == LX && y0 > top - 6 && y0 < bottom + 6) continue
                    val dx = x0 - LX
                    val dy = y0 - cy
                    val dd = sqrt(dx * dx + dy * dy)
                    if (dd < lensL) {
                        val t = 1 - dd / lensL
                        val m = 1 + lensK * t * t
                        x = LX + dx * m
                        y = cy + dy * m
                        val tt = t * t * lsE
                        if (tt > .002f) {
                            r = 1.2f * (1 + 1.5f * tt)
                            a = .16f + .3f * tt
                            special = true
                        }
                    }
                } else {
                    val dx = cx - x0
                    val dy = y0 - cy
                    val du = sqrt(dx * dx + dy * dy) - r0
                    if (kind == Kind.Field && du < -34) {
                        inner = true
                    } else if (du > -32 && du <= 9 && (du >= -9 || du < -14)) {
                        val th = atan2(dy, dx)
                        val env = c01(1 - abs(th) / tmax)
                        if (env > 0) {
                            // A swell travels along the double arc as the dial turns.
                            val aa = pos + th / step
                            val dl = abs(aa - aa.roundToInt())
                            val bump = cos(PI.toFloat() * dl).pow(8)
                            val f = c01(1 - abs(th) / .8f)
                            val e2 = env.pow(.7f)
                            if (du >= -9) {
                                r = 1.2f + e2 * (.4f + 1.9f * f * f + 1.8f * bump)
                                a = .16f + e2 * (.18f + .5f * f + .45f * bump)
                                // The readout dial has no names, so an unread app tints its swell.
                                if (kind == Kind.Readout && unread != null && n > 0 && bump > .35f &&
                                    unread.getOrElse(mod(aa.roundToInt(), n)) { false }
                                ) col = 1f
                            } else {
                                r = 1.1f + e2 * (.25f + 1f * f * f + 1f * bump)
                                a = .12f + e2 * (.1f + .3f * f + .3f * bump)
                            }
                            a = min(a, 1f)
                            r = 1.2f + (r - 1.2f) * dial
                            a = .16f + (a - .16f) * dial
                            special = true
                        }
                    }
                    if (x0 == ax && y0 == cy) {
                        r = 1.2f + (3.4f + pawl * 1.3f) * dial
                        a = .16f + .84f * dial
                        col = if (dial > .5f) 1f else 0f
                        special = true
                        inner = false
                    }
                }
                val px = mir(x)
                if (rr > 0) {
                    val g = ripple(px, y, r, a, 2.4f, 8.9f)
                    if (g == 2) {
                        flood.add(px, y)
                        continue
                    }
                    if (g == 1) {
                        r = rg[0]
                        a = rg[1]
                        col = rg[2]
                        special = true
                        inner = false
                    }
                }
                when {
                    special -> vary.add(px, y, r, a, col)
                    inner -> dim.add(px, y)
                    else -> base.add(px, y)
                }
            }
        }

        // The lens column: one dot per app and a small dot between two apps. The rows repeat without end, like
        // the dial, and flow through the lens. The selected dot is the notch. The column fades out at both ends.
        if (kind == Kind.Lens && n > 0) {
            val first = floor(pos + (top - cy) / LENS_GAP).toInt() - 1
            val last = ceil(pos + (bottom - cy) / LENS_GAP).toInt() + 1
            for (i in first..last) for (half in 0..1) {
                val y0 = cy + (i - pos + half * .5f) * LENS_GAP
                if (y0 < top || y0 > bottom) continue
                val dy = y0 - cy
                var y = y0
                var tt = 0f
                val ad = abs(dy)
                if (ad < lensL) {
                    val t = 1 - ad / lensL
                    y = cy + dy * (1 + lensK * t * t)
                    tt = t * t * lsE
                }
                var r: Float
                var a: Float
                var col = 0f
                if (half == 1) {
                    r = 1.2f * (1 + 1.5f * tt)
                    a = .16f + .3f * tt
                } else {
                    if (i == cur) {
                        r = 3.4f + 1.6f * s.ls + pawl
                        a = 1f
                        col = 1f
                    } else {
                        r = 1.8f * (1 + 1.3f * tt)
                        a = .5f + .4f * tt
                        if (unread?.getOrNull(mod(i, n)) == true) col = 1f
                    }
                    r = 1.2f + (r - 1.2f) * dial
                    a = .16f + (a - .16f) * dial
                    if (dial < .5f) col = 0f
                }
                a *= c01((y0 - top) / 36) * c01((bottom - y0) / 36)
                val px = mir(LX)
                if (rr > 0) {
                    val g = ripple(px, y, r, a, 2.4f, 8.9f)
                    if (g == 2) {
                        flood.add(px, y)
                        continue
                    }
                    if (g == 1) {
                        r = rg[0]
                        a = rg[1]
                        col = rg[2]
                    }
                }
                vary.add(px, y, r, a, col)
            }
        }

        // The dot-matrix readout.
        val rv = s.readout.v
        val racc = s.readout.accent
        for (jj in 0 until RR) for (ii in 0 until RC) {
            val idx = jj * RC + ii
            val x = RX + ii * RF
            val y = readY + jj * RF
            val v = rv[idx]
            var r = .85f + 1.75f * v
            var a = (.08f + .92f * v) * read
            var col = if (racc[idx]) 1f else 0f
            var lit = v > .02f
            if (rr > 0) {
                val g = ripple(x, y, r, a, 1.6f, 4.5f)
                if (g == 2) {
                    floodRead.add(x, y)
                    continue
                }
                if (g == 1) {
                    r = rg[0]
                    a = rg[1]
                    col = rg[2]
                    lit = true
                }
            }
            if (lit) vary.add(x, y, r, a, col) else unlit.add(x, y)
        }

        val colors = arrayOf(pal.fg, pal.accent, pal.sheet)
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            fun batch(d: Dots, r: Float, a: Float, color: Color) {
                if (d.n == 0 || a <= 0f) return
                point.color = color.copy(alpha = a).toArgb()
                nc.save()
                nc.scale(k, k)
                point.strokeWidth = 2 * r
                nc.drawPoints(d.pts, 0, d.n, point)
                nc.restore()
            }
            batch(base, 1.2f, .16f, pal.fg)
            batch(dim, 1.1f + .1f * (1 - dial), .08f + .08f * (1 - dial), pal.fg)
            batch(unlit, .85f, .08f * read, pal.fg)
            batch(flood, 8.9f, 1f, pal.sheet)
            batch(floodRead, 4.5f, 1f, pal.sheet)
            var q = 0
            val vp = vary.pts
            while (q < vary.n) {
                fill.color = colors[vp[q + 4].toInt()].copy(alpha = c01(vp[q + 3])).toArgb()
                nc.drawCircle(vp[q] * k, vp[q + 1] * k, vp[q + 2] * k, fill)
                q += 5
            }
        }

        val la = labels * dial
        if (la > .004f && n > 0) {
            if (kind == Kind.Field) {
                drawFieldLabels(names, unread, pos, Geo(r0, cy, ax), left, scrub != null, la, pal, k, measurer, regular, medium)
            } else if (kind == Kind.Lens) {
                for (i in cur - 8..cur + 8) {
                    val dy = (i - pos) * LENS_GAP
                    val ad = abs(dy)
                    if (ad >= lensL) continue
                    val t = 1 - ad / lensL
                    val y = cy + dy * (1 + lensK * t * t)
                    val on = i == cur
                    val sc = 1 + .7f * t * t * lsE + if (on) .15f else 0f
                    val op = min(1f, t * 1.8f) * (if (on) 1f else .55f + .45f * lsE) * la
                    if (op < .004f) continue
                    val xr = LX - 18 - 8 * t * lsE
                    val text = measurer.measure(names[mod(i, n)], if (on) medium else regular)
                    withTransform({
                        translate(mir(xr) * k, y * k)
                        scale(sc, sc, Offset.Zero)
                    }) {
                        val tx = if (left) 0f else -text.size.width.toFloat()
                        drawText(text, if (on) pal.accent else pal.fg, Offset(tx, -text.size.height / 2f), alpha = op)
                    }
                }
            }
        }
    }

    GridDate(s, pal, read * labels)
}

private fun DrawScope.drawFieldLabels(
    names: List<String>,
    unread: List<Boolean>?,
    pos: Float,
    g: Geo,
    left: Boolean,
    scrubbing: Boolean,
    alpha: Float,
    pal: Palette,
    k: Float,
    measurer: TextMeasurer,
    regular: TextStyle,
    medium: TextStyle,
) {
    val n = names.size
    // The names ride on spokes inside the arc.
    val rl = g.r - 46
    for (i in 0 until n) {
        var d = mod(i - pos, n.toFloat())
        if (d > n / 2f) d -= n
        val a = abs(d)
        if (a >= g.span) continue
        val cl = max(0f, 1 - a)
        val sel = cl > .5f
        val name = names[i]
        val s = if (scrubbing) 1 + .9f * cl * cl
        else 1 + (min(1.4f, max(1f, 116f / (name.length * CH))) - 1) * cl * cl
        val p = labelPoseAt(d, name.length, s, g, rl, left)
        val op = (1 - a / g.span).pow(1.35f) * alpha
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
        if (unread != null && unread.getOrElse(i) { false }) {
            drawCircle(pal.accent, 2.5f * k, Offset(p.bx * k, p.by * k), alpha = op)
        }
    }
}

/** The date under the readout. The readout layout shows the position of the selected app while it reads a name. */
@Composable
private fun GridDate(s: LauncherState, pal: Palette, alpha: Float) {
    val u = LocalUnits.current
    val cal = Calendar.getInstance().apply { timeInMillis = s.now }
    val n = s.dialApps.size
    val text = if (s.readName && s.kind == Kind.Readout && s.scrub == null && n > 0) {
        val i = mod(s.index, n) + 1
        stringResource(R.string.tap_to_open_position, i.toString().padStart(2, '0'), n)
    } else {
        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(cal.time).lowercase(java.util.Locale.getDefault())
    }
    Txt(
        text, 12f, pal.muted2,
        Modifier.place(u, RX, s.readY + 92f).height(u.dp(44f)).graphicsLayer { this.alpha = alpha }
            .combinedClickable(
                enabled = s.ob == null && !s.search && s.open == null && s.scrub == null,
                onClick = { if (s.readName && s.kind == Kind.Readout) s.launchSelected() else s.calendarTap() },
                onLongClick = s::editSetup,
            ),
        spacing = .04f,
    )
}


/** Touch target for the clock in grid layouts. */
@Composable
fun GridClockTarget(s: LauncherState) {
    if (s.ob != null || s.search || s.open != null || s.scrub != null || s.menu != null) return
    val u = LocalUnits.current
    val label = if (s.kind == Kind.Readout && s.readName) s.curApp()?.label.orEmpty() else stringResource(R.string.open_clock)
    androidx.compose.foundation.layout.Box(
        Modifier.place(u, 20f, s.readY).width(u.dp(372f)).height(u.dp(84f))
            .semantics { contentDescription = label }
            .combinedClickable(onClick = {
                if (s.kind == Kind.Readout && s.readName) s.launchSelected() else s.clockTap()
            }, onLongClick = s::editSetup),
    )
}
