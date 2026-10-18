package app.detent.launcher

import android.content.Context
import androidx.core.content.edit

class Saved(val cfg: Cfg, val keys: Set<String>, val pos: Int)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("detent", Context.MODE_PRIVATE)
    private val openedSp = ctx.getSharedPreferences("detent.opened", Context.MODE_PRIVATE)

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

    var pullToLaunch: Boolean
        get() = sp.getBoolean("pullToLaunch", false)
        set(v) = sp.edit { putBoolean("pullToLaunch", v) }

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit { putBoolean("sound", v) }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit { putBoolean("haptics", v) }

    var dots: Boolean
        get() = sp.getBoolean("dots", true)
        set(v) = sp.edit { putBoolean("dots", v) }

    /** Sets the lock screen and the system wallpaper to the theme. */
    var lockWall: Boolean
        get() = sp.getBoolean("lockWall", true)
        set(v) = sp.edit { putBoolean("lockWall", v) }

    /** Path of the user's own wallpaper, or null to use the theme wallpaper. */
    var customWall: String?
        get() = sp.getString("customWall", null)?.takeIf { java.io.File(it).exists() }
        set(v) = sp.edit { putString("customWall", v) }

    /** The theme and side that the system wallpaper shows now. */
    var appliedWall: String?
        get() = sp.getString("appliedWall", null)
        set(v) = sp.edit { putString("appliedWall", v) }

    fun opened(): Map<String, Long> = openedSp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    fun markOpened(pkgUser: String, time: Long) = openedSp.edit { putLong(pkgUser, time) }
}
