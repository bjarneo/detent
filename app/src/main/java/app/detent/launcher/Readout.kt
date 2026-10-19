package app.detent.launcher

import kotlin.math.abs
import kotlin.math.pow

// 5 x 7 glyphs. "0" is an empty row, so short letters sit on the baseline.
private val GLYPH_SRC = mapOf(
    'a' to "0 0 .###. ....# .#### #...# .####", 'b' to "#.... #.... ####. #...# #...# #...# ####.",
    'c' to "0 0 .#### #.... #.... #.... .####", 'd' to "....# ....# .#### #...# #...# #...# .####",
    'e' to "0 0 .###. #...# ##### #.... .###.", 'f' to "..##. .#... ####. .#... .#... .#... .#...",
    'g' to "0 0 .#### #...# #...# #...# .#### ....# .###.", 'h' to "#.... #.... ####. #...# #...# #...# #...#",
    'i' to "..#.. 0 .##.. ..#.. ..#.. ..#.. .###.", 'j' to "...#. 0 ..##. ...#. ...#. ...#. ...#. #..#. .##..",
    'k' to "#.... #.... #..#. #.#.. ##... #.#.. #..#.", 'l' to ".##.. ..#.. ..#.. ..#.. ..#.. ..#.. .###.",
    'm' to "0 0 ##.#. #.#.# #.#.# #.#.# #.#.#", 'n' to "0 0 ####. #...# #...# #...# #...#",
    'o' to "0 0 .###. #...# #...# #...# .###.", 'p' to "0 0 ####. #...# #...# #...# ####. #.... #....",
    'q' to "0 0 .#### #...# #...# #...# .#### ....# ....#", 'r' to "0 0 #.##. ##..# #.... #.... #....",
    's' to "0 0 .#### #.... .###. ....# ####.", 't' to ".#... .#... ####. .#... .#... .#..# ..##.",
    'u' to "0 0 #...# #...# #...# #...# .####", 'v' to "0 0 #...# #...# #...# .#.#. ..#..",
    'w' to "0 0 #...# #...# #.#.# #.#.# .#.#.", 'x' to "0 0 #...# .#.#. ..#.. .#.#. #...#",
    'y' to "0 0 #...# #...# #...# #...# .#### ....# .###.", 'z' to "0 0 ##### ...#. ..#.. .#... #####",
    '0' to ".###. #...# #..## #.#.# ##..# #...# .###.", '1' to "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
    '2' to ".###. #...# ....# ...#. ..#.. .#... #####", '3' to "##### ...#. ..#.. ...#. ....# #...# .###.",
    '4' to "...#. ..##. .#.#. #..#. ##### ...#. ...#.", '5' to "##### #.... ####. ....# ....# #...# .###.",
    '6' to "..##. .#... #.... ####. #...# #...# .###.", '7' to "##### ....# ...#. ..#.. .#... .#... .#...",
    '8' to ".###. #...# #...# .###. #...# #...# .###.", '9' to ".###. #...# #...# .#### ....# ...#. .##..",
    'á' to "...#. ..#.. .###. ....# .#### #...# .####",
    'é' to "...#. ..#.. .###. #...# ##### #.... .###.",
    'í' to "...#. ..#.. .##.. ..#.. ..#.. ..#.. .###.",
    'ó' to "...#. ..#.. .###. #...# #...# #...# .###.",
    'ú' to "...#. ..#.. #...# #...# #...# #...# .####",
    'ü' to ".#.#. 0 #...# #...# #...# #...# .####",
    'ñ' to ".##.# #.##. ####. #...# #...# #...# #...#",
    '.' to "0 0 0 0 0 0 ..#..",
    ':' to ". . # . # . .",
)

private val GLYPHS = GLYPH_SRC.mapValues { (_, v) -> v.split(' ').map { if (it == "0") "....." else it } }

/** Lit dots for [text] at [scale] dots per glyph pixel, [rowOff] rows from the top. Unknown characters are blank. */
private fun bits(text: String, scale: Int, rowOff: Int): BooleanArray {
    val out = BooleanArray(RC * RR)
    var cx = 0
    for (ch in text) {
        val g = GLYPHS[ch]
        if (g == null) {
            cx += 3 * scale
            continue
        }
        val w = g[0].length
        for (r in g.indices) for (c in 0 until w) {
            if (g[r][c] != '#') continue
            for (sy in 0 until scale) for (sx in 0 until scale) {
                val x = cx + c * scale + sx
                val y = rowOff + r * scale + sy
                if (x in 0 until RC && y in 0 until RR) out[y * RC + x] = true
            }
        }
        cx += (w + 1) * scale
    }
    return out
}

/** The dot-matrix display at the top of the grid layouts. Each dot eases to its new state, left to right. */
class Readout {
    /** Brightness of each dot, from 0 to 1. */
    val v = FloatArray(RC * RR)

    /** True when the dot takes the accent colour. */
    val accent = BooleanArray(RC * RR)
    private val target = BooleanArray(RC * RR)
    private val start = LongArray(RC * RR)

    fun set(text: String, scale: Int, rowOff: Int, accentOn: Boolean, now: Long, instant: Boolean) {
        val b = bits(text, scale, rowOff)
        for (i in b.indices) {
            val on = b[i]
            if (on) accent[i] = accentOn
            if (on != target[i]) {
                target[i] = on
                start[i] = now + (i % RC) * 5L + if (on) 30L else 0L
            }
            if (instant) v[i] = if (on) 1f else 0f
        }
    }

    /** Moves the dots one frame. Returns true while a dot has not reached its state. */
    fun advance(now: Long, dt: Float): Boolean {
        val k = 1 - 0.7f.pow(dt / 16f)
        var busy = false
        for (i in v.indices) {
            val t = if (target[i]) 1f else 0f
            val x = v[i]
            if (x == t) continue
            busy = true
            if (now < start[i]) continue
            val d = t - x
            v[i] = if (abs(d) < .004f) t else x + d * k
        }
        return busy
    }
}
