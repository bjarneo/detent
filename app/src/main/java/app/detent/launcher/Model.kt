package app.detent.launcher

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// The design draws a 412 x 892 screen. One design unit is (screen width / 412) px.
const val W = 412f
const val H0 = 892f
const val AX = 226f   // x of the dial rim on the selected row
const val CY0 = 470f  // y of the selected row
const val GAP = 54f   // arc length between two apps
const val CH = 9.15f  // advance of one 15-unit JetBrains Mono glyph, letter spacing included

val Ease = CubicBezierEasing(.2f, .8f, .2f, 1f)

fun mod(a: Int, n: Int) = ((a % n) + n) % n
fun mod(a: Float, n: Float) = ((a % n) + n) % n

enum class Markers { Double, Ticks, Nodes, Dots }
enum class Side { Right, Left }

/**
 * Radial layouts draw a dial over the wallpaper. The other kinds draw the whole screen as one dot grid:
 * field puts the names on a dial of dots, lens puts every app on a column of dots, and readout shows the
 * name in the dot-matrix clock.
 */
enum class Kind { Radial, Field, Lens, Readout }

// Radial labels follow the angle of their row.
class Layout(
    val name: Int,
    val radius: Float,
    val markers: Markers,
    val ticksPerApp: Int,
    val kind: Kind = Kind.Radial,
) {
    val grid get() = kind != Kind.Radial

    /** x of the notch on a right-hand dial. */
    val ax get() = when (kind) {
        Kind.Radial -> AX
        Kind.Field -> 236f
        Kind.Readout -> 224f
        Kind.Lens -> LX
    }

    /** y of the notch. On the grid layouts the notch is a grid dot. */
    fun cy(hd: Float) = if (grid) snap(476f + (hd - H0) / 2f) else CY0 + (hd - H0) / 2f
}

val LAYOUTS = listOf(
    Layout(R.string.soft_radial_double, 330f, Markers.Double, 4),
    Layout(R.string.round_radial_double, 200f, Markers.Double, 5),
    Layout(R.string.soft_radial_ticks, 330f, Markers.Ticks, 4),
    Layout(R.string.flat_radial_dots, 560f, Markers.Dots, 6),
    Layout(R.string.flat_radial_nodes, 560f, Markers.Nodes, 4),
    Layout(R.string.grid_field, 300f, Markers.Double, 1, Kind.Field),
    Layout(R.string.grid_lens, 300f, Markers.Double, 1, Kind.Lens),
    Layout(R.string.grid_readout, 200f, Markers.Double, 1, Kind.Readout),
)

// The grid layouts. Grid dots sit every G units, starting GO units from the top left corner.
const val G = 12f
const val GO = 8f
const val LX = 380f   // x of the lens column
const val LENS_GAP = 24f // distance between two lens rows
const val RX = 20f    // left edge of the dot-matrix readout
const val RF = 6f     // readout dot pitch
const val RC = 62     // readout columns
const val RR = 14     // readout rows

/** The grid dot nearest to [v] on one axis. */
fun snap(v: Float) = GO + G * ((v - GO) / G).roundToInt()

data class Cfg(val layout: Int = 0, val theme: Int = 0, val side: Side = Side.Right)

class ThemeDef(
    val name: String,
    val group: String,
    val light: Boolean,
    bg: Long,
    fg: Long,
    accent: Long,
    /** Asset path of the wallpaper, or null for the tinted dot-hand art. */
    val wall: String?,
    hand: Long = 0xFF504945,
    /** Wallpaper crop for a left-hand dial, when it differs. */
    val wallLeft: String? = null,
) {
    val bg = Color(bg)
    val fg = Color(fg)
    val accent = Color(accent)
    val hand = Color(hand)
}

private fun phone(name: String, light: Boolean, bg: Long, fg: Long, acc: Long, hand: Long) =
    ThemeDef(name, "phone", light, bg, fg, acc, null, hand)

private fun omarchy(name: String, light: Boolean, bg: Long, fg: Long, acc: Long) =
    ThemeDef(name, "omarchy", light, bg, fg, acc, "walls/$name.webp")

val THEMES = listOf(
    phone("dusk", false, 0xFF1D2021, 0xFFEBDBB2, 0xFFFE8019, 0xFF504945),
    phone("ember", false, 0xFF000000, 0xFFEBE5DC, 0xFFDF6124, 0xFF3A3733),
    phone("signal", false, 0xFF000000, 0xFFFFFFFF, 0xFFFF453A, 0xFF3A3A3A),
    phone("paper", true, 0xFFF1EDE4, 0xFF1C1A17, 0xFFC2410C, 0xFFCBC3B4),
    omarchy("catppuccin", false, 0xFF1E1E2E, 0xFFCDD6F4, 0xFF89B4FA),
    omarchy("catppuccin-latte", true, 0xFFEFF1F5, 0xFF4C4F69, 0xFF1E66F5),
    omarchy("ethereal", false, 0xFF060B1E, 0xFFFFCEAD, 0xFF7D82D9),
    omarchy("everforest", false, 0xFF2D353B, 0xFFD3C6AA, 0xFF7FBBB3),
    omarchy("flexoki-light", true, 0xFFFFFCF0, 0xFF100F0F, 0xFF205EA6),
    omarchy("gruvbox", false, 0xFF282828, 0xFFD4BE98, 0xFF7DAEA3),
    omarchy("hackerman", false, 0xFF0B0C16, 0xFFDDF7FF, 0xFF82FB9C),
    omarchy("kanagawa", false, 0xFF1F1F28, 0xFFDCD7BA, 0xFFDCD7BA),
    ThemeDef(
        "last-horizon", "omarchy", false, 0xFF0C0B0C, 0xFFFAFCFB, 0xFFB59790,
        "walls/last-horizon.webp", wallLeft = "walls/last-horizon-left.webp",
    ),
    omarchy("lumon", false, 0xFF16242D, 0xFFD6E2EE, 0xFF8BC9EB),
    omarchy("lupine", true, 0xFFFAFAFA, 0xFF212121, 0xFF3264EB),
    omarchy("matte-black", false, 0xFF121212, 0xFFBEBEBE, 0xFFE68E0D),
    omarchy("miasma", false, 0xFF222222, 0xFFC2C2B0, 0xFF78824B),
    omarchy("nord", false, 0xFF2E3440, 0xFFD8DEE9, 0xFF81A1C1),
    omarchy("osaka-jade", false, 0xFF111C18, 0xFFC1C497, 0xFF509475),
    omarchy("retro-82", false, 0xFF05182E, 0xFFF6DCAC, 0xFFFAA968),
    omarchy("ristretto", false, 0xFF2C2525, 0xFFE6D9DB, 0xFFF38D70),
    omarchy("rose-pine", true, 0xFFFAF4ED, 0xFF575279, 0xFF56949F),
    omarchy("solitude", false, 0xFF101315, 0xFFCACCCC, 0xFF798186),
    omarchy("starship", false, 0xFF1C1C1E, 0xFFF5F5F7, 0xFFFF9F0A),
    omarchy("tokyo-night", false, 0xFF1A1B26, 0xFFA9B1D6, 0xFF7AA2F7),
    ThemeDef("vantablack", "omarchy", false, 0xFF000000, 0xFFFFFFFF, 0xFF8D8D8D, null, 0xFF505050),
    omarchy("white", true, 0xFFFFFFFF, 0xFF000000, 0xFF6E6E6E),
)

val THEME_NAMES = THEMES.map { it.name }

/** Mixes [a] into [b]. t = 1 gives [a]. */
fun mix(a: Color, b: Color, t: Float) = Color(
    a.red * t + b.red * (1 - t),
    a.green * t + b.green * (1 - t),
    a.blue * t + b.blue * (1 - t),
)

class Palette(t: ThemeDef) {
    val light = t.light
    val bg = t.bg
    val fg = t.fg
    val accent = t.accent
    val onAccent = if (t.accent.luminance() > 0.33f) Color(0xFF111111) else Color.White
    val muted = mix(fg, bg, .5f)
    val muted2 = mix(fg, bg, .68f)
    val rim = fg.copy(alpha = .14f)
    val rim2 = fg.copy(alpha = .22f)
    val tickMaj = fg.copy(alpha = .6f)
    val tickMin = fg.copy(alpha = .25f)
    val dotMaj = fg.copy(alpha = .65f)
    val dotMin = fg.copy(alpha = .3f)
    val nodeMin = fg.copy(alpha = .22f)
    val hover = fg.copy(alpha = .07f)
    val sheet = mix(fg, bg, .05f)
    val card = mix(fg, bg, .1f)
    val btnBg = bg.copy(alpha = .55f)
    val hand = t.hand
    val veil = bg.copy(alpha = .62f)
    val chipBg = bg.copy(alpha = .5f)
}

val PALETTES = THEMES.map { Palette(it) }

/**
 * Colour matrix for the dot-hand art. The art is white dots on black. The dots take the hand colour and the
 * black takes the background colour. Offsets are in the 0 to 255 range.
 */
fun handMatrix(p: Palette) = floatArrayOf(
    p.hand.red - p.bg.red, 0f, 0f, 0f, p.bg.red * 255f,
    p.hand.green - p.bg.green, 0f, 0f, 0f, p.bg.green * 255f,
    p.hand.blue - p.bg.blue, 0f, 0f, 0f, p.bg.blue * 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** Dial geometry for one rim radius. */
class Geo(val r: Float, val cy: Float, ax: Float = AX) {
    val cx = ax + r
    val step = GAP / r
    val span = min(6.5f, 1.55f / step)
}

class LabelPose(val x: Float, val y: Float, val rot: Float, val tx: Float, val bx: Float, val by: Float)

/** Largest scale at which a name of [chars] glyphs still fits inside the dial. */
fun fitScale(chars: Int) = max(1f, 150f / (chars * 9f))

fun selScale(chars: Int) = min(1.45f, fitScale(chars))

/** Scale of a dial label [a] rows from the notch. The label grows as it nears the notch. */
fun labelScale(a: Float, chars: Int): Float {
    val cl = max(0f, 1 - a)
    return min(1 + .45f * cl * cl, fitScale(chars))
}

fun labelRadius(g: Geo, m: Markers) = g.r - when (m) {
    Markers.Ticks -> 28f
    Markers.Double -> 34f
    else -> 22f
}

/**
 * Position of a label [d] rows away from the selected row. [tx] is the horizontal shift in label widths.
 * [bx] and [by] give the unread dot that trails the name.
 */
fun labelPose(d: Float, chars: Int, s: Float, g: Geo, m: Markers, left: Boolean) =
    labelPoseAt(d, chars, s, g, labelRadius(g, m), left)

/** [labelPose] for labels at radius [r]. */
fun labelPoseAt(d: Float, chars: Int, s: Float, g: Geo, r: Float, left: Boolean): LabelPose {
    val ang = d * g.step
    val x0 = g.cx - r * cos(ang)
    val y = g.cy + r * sin(ang)
    val w = chars * CH * s
    val rd = if (left) r + 8 else r - w - 8
    val bx = g.cx - rd * cos(ang)
    val by = g.cy + rd * sin(ang)
    return LabelPose(
        x = if (left) W - x0 else x0,
        y = y,
        rot = if (left) ang else -ang,
        tx = if (left) -1f else 0f,
        bx = if (left) W - bx else bx,
        by = by,
    )
}
