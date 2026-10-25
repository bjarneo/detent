package app.detent.launcher

import android.content.Context
import androidx.core.content.edit

class Saved(val cfg: Cfg, val keys: Set<String>, val pos: Int)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("detent", Context.MODE_PRIVATE)
    private val openedSp = ctx.getSharedPreferences("detent.opened", Context.MODE_PRIVATE)

    // The data for the dial hints: "s:" opens from search, "a:" the time an app joined the dial,
    // "z:" the time until which an app gets no hint.
    private val usageSp = ctx.getSharedPreferences("detent.usage", Context.MODE_PRIVATE)

    /** The saved setup, or null when onboarding has not finished. */
    fun load(): Saved? {
        if (!sp.getBoolean("done", false)) return null
        val cfg = Cfg(
            layout = sp.getInt("layout", 0).coerceIn(LAYOUTS.indices),
            theme = sp.getInt("theme", 0).coerceIn(THEMES.indices),
            side = if (sp.getString("side", "right") == "left") Side.Left else Side.Right,
        )
        return Saved(cfg, sp.getStringSet("apps", null).orEmpty().toSet(), sp.getInt("pos", 0))
    }

    fun save(cfg: Cfg, keys: Set<String>, pos: Int) = sp.edit {
        putBoolean("done", true)
        putInt("layout", cfg.layout)
        putInt("theme", cfg.theme)
        putString("side", if (cfg.side == Side.Left) "left" else "right")
        putStringSet("apps", HashSet(keys))
        putInt("pos", pos)
    }

    fun savePos(pos: Int) = sp.edit { putInt("pos", pos) }

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit { putBoolean("sound", v) }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit { putBoolean("haptics", v) }

    var dots: Boolean
        get() = sp.getBoolean("dots", true)
        set(v) = sp.edit { putBoolean("dots", v) }

    /** Shows the mic button in the bottom corner. */
    var voice: Boolean
        get() = sp.getBoolean("voice", true)
        set(v) = sp.edit { putBoolean("voice", v) }

    /** Shows the web button in the bottom corner. */
    var webSearch: Boolean
        get() = sp.getBoolean("webSearch", true)
        set(v) = sp.edit { putBoolean("webSearch", v) }

    /** Sets the lock screen and the system wallpaper to the theme. */
    var lockWall: Boolean
        get() = sp.getBoolean("lockWall", true)
        set(v) = sp.edit { putBoolean("lockWall", v) }

    /** Path of the user's own wallpaper, or null to use the theme wallpaper. */
    var customWall: String?
        get() = sp.getString("customWall", null)?.takeIf { java.io.File(it).exists() }
        set(v) = sp.edit { putString("customWall", v) }

    /** The quick apps. Each line holds the app key, a tab, and the icon name. */
    var quick: List<QuickSlot>
        get() {
            val lines = sp.getString("quicks", null)
            if (lines != null) {
                return lines.split('\n').filter { it.isNotEmpty() }.map {
                    QuickSlot(it.substringBefore('\t'), it.substringAfter('\t', "").ifEmpty { null })
                }
            }
            // Detent 0.10 and older keep one quick app.
            val key = sp.getString("quick", null) ?: return emptyList()
            return listOf(QuickSlot(key, sp.getString("quickIcon", null)))
        }
        set(v) = sp.edit {
            putString("quicks", v.joinToString("\n") { it.key + "\t" + (it.icon ?: "") })
            remove("quick")
            remove("quickIcon")
        }

    /** True when Detent hides the Android status bar and shows battery, wifi, and signal itself. */
    var ownBar: Boolean
        get() = sp.getBoolean("ownBar", false)
        set(v) = sp.edit { putBoolean("ownBar", v) }

    /** Keys of the apps last opened from search, newest first. */
    var recent: List<String>
        get() = sp.getString("recent", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        set(v) = sp.edit { putString("recent", v.joinToString("\n")) }

    /** The theme and side that the system wallpaper shows now. */
    var appliedWall: String?
        get() = sp.getString("appliedWall", null)
        set(v) = sp.edit { putString("appliedWall", v) }

    fun opened(): Map<String, Long> = openedSp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    fun markOpened(pkgUser: String, time: Long) = openedSp.edit { putLong(pkgUser, time) }

    private fun <T> usage(prefix: String, read: (Any?) -> T?): Map<String, T> =
        usageSp.all.mapNotNull { (k, v) -> if (k.startsWith(prefix)) read(v)?.let { k.removePrefix(prefix) to it } else null }.toMap()

    private fun times(v: Any?) = (v as? String)?.split(',')?.mapNotNull { it.toLongOrNull() }

    /** Times of the opens from search in the last week, by app key. */
    fun searchOpens(): Map<String, List<Long>> = usage("s:", ::times)

    fun markSearchOpen(key: String, time: Long) {
        val old = times(usageSp.getString("s:$key", null)).orEmpty()
        val keep = (old.filter { time - it < Suggest.WEEK } + time).takeLast(8)
        usageSp.edit { putString("s:$key", keep.joinToString(",")) }
    }

    /** The time that each dial app joined the dial, by app key. */
    fun dialSince(): Map<String, Long> = usage("a:") { it as? Long }

    /** Records the time that apps join the dial. An app that joins the dial forgets its opens from search. */
    fun syncDial(keys: Set<String>, now: Long) {
        val since = dialSince().keys
        if (since == keys) return
        usageSp.edit {
            for (k in keys - since) {
                putLong("a:$k", now)
                remove("s:$k")
            }
            for (k in since - keys) remove("a:$k")
        }
    }

    /** The time until which each app gets no hint, by app key. */
    fun quiet(): Map<String, Long> = usage("z:") { it as? Long }

    fun quiet(key: String, until: Long) = usageSp.edit { putLong("z:$key", until) }

    /** The time of the last dial hint. */
    var hintAt: Long
        get() = usageSp.getLong("hintAt", 0L)
        set(v) = usageSp.edit { putLong("hintAt", v) }
}
