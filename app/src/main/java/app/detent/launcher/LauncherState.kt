package app.detent.launcher

import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

enum class Spin { Idle, Drag, Fling, Settle, Wheel }
enum class Zone { None, Empty, Dial, Selected }
enum class Phase { From, In, Out }

/** A label transform: translate(x, y) rotate(rot) scale(s) translate(tx * width, -50%). */
data class Pose(val x: Float, val y: Float, val rot: Float, val s: Float, val tx: Float)

data class OpenApp(
    val id: Long,
    val label: String,
    val key: String?,
    val sub: String,
    val from: Pose,
    val fromDial: Boolean,
    val phase: Phase,
)

class Press(val id: Long, val x: Float, val y: Float)
class QuickMenu(val app: AppEntry, val shortcuts: List<ShortcutInfo>)
class Toast(val id: Long, val text: String, val undo: (() -> Unit)?)

/** The calls that need the activity. */
interface Host {
    fun launchApp(app: AppEntry, bounds: Rect?)
    fun launchShortcut(info: ShortcutInfo, bounds: Rect?)
    fun showAppInfo(app: AppEntry, bounds: Rect?)
    fun isDefaultHome(): Boolean
    fun requestHome()
    fun hasNotificationAccess(): Boolean
    fun openNotificationAccess()

    /** Sets the lock screen and system wallpaper off the main thread, then calls [done] on the main thread. */
    fun setWallpaper(theme: ThemeDef, pal: Palette, left: Boolean, custom: String?, done: (Boolean) -> Unit)

    /**
     * Opens the photo picker and saves the picked image. [done] gets the saved path, or null when the user
     * cancels. [failed] is true when the image cannot be read.
     */
    fun pickWallpaper(done: (path: String?, failed: Boolean) -> Unit)

    /** Deletes saved wallpapers except [keep]. */
    fun cleanWallpapers(keep: String?)
}

private class Drag(val x0: Float, val y0: Float, var y: Float, val t0: Long, val zone: Zone) {
    var moved = false
    var scrub = false
    var pull: PullToLaunch? = null
    val samples = ArrayDeque<Pair<Long, Float>>()
}

/** Onboarding steps. */
object Step {
    const val WELCOME = 0
    const val SHAPE = 1
    const val THEME = 2
    const val APPS = 3
    const val FEEL = 4
    const val READY = 5
}

class LauncherState(
    private val host: Host,
    val repo: AppsRepository,
    private val prefs: Prefs,
    private val fb: Feedback,
    private val scope: CoroutineScope,
) {
    // Screen size in design units. The UI sets these on each layout.
    var k = 1f
    var hd = H0
    val cy get() = CY0 + (hd - H0) / 2f

    // Dial engine. The canvas reads these three every frame.
    var pos by mutableFloatStateOf(0f)
        private set
    var pawl by mutableFloatStateOf(0f)
        private set
    var radius by mutableFloatStateOf(Curve.Soft.radius)
        private set
    var spin by mutableStateOf(Spin.Idle)
        private set
    val index by derivedStateOf { pos.roundToInt() }

    private var vel = 0f
    private var target = 0f
    private var lastIdx = 0
    private var lastClick = 0L
    private var rTarget = Curve.Soft.radius
    private var loop: Job? = null
    private var drag: Drag? = null
    private var lpJob: Job? = null
    private var wheelJob: Job? = null
    private var toastJob: Job? = null
    private var warnJob: Job? = null
    private var scrubBase = 0
    private var appPos = 0
    private var seq = 0L

    var cfg by mutableStateOf(Cfg())
        private set
    var dialKeys by mutableStateOf<Set<String>>(emptySet())
        private set

    var ob by mutableStateOf<Int?>(null)
        private set
    var obEdit by mutableStateOf(false)
        private set
    var obSeq by mutableIntStateOf(0)
        private set
    private var prevCfg: Cfg? = null
    private var prevKeys: Set<String>? = null

    var warn by mutableStateOf(false)
        private set
    var press by mutableStateOf<Press?>(null)
        private set
    var search by mutableStateOf(false)
        private set
    var query by mutableStateOf("")
    var open by mutableStateOf<OpenApp?>(null)
        private set
    var menu by mutableStateOf<QuickMenu?>(null)
        private set
    var menuShown by mutableStateOf(false)
        private set
    var scrub by mutableStateOf<List<String>?>(null)
        private set
    var toast by mutableStateOf<Toast?>(null)
        private set
    var toastShown by mutableStateOf(false)
        private set
    var now by mutableLongStateOf(System.currentTimeMillis())
        private set
    var resumed by mutableStateOf(false)
        private set

    var pullToLaunch by mutableStateOf(prefs.pullToLaunch)
        private set
    var pullProgress by mutableFloatStateOf(0f)
        private set
    var pullArmed by mutableStateOf(false)
        private set

    var sound by mutableStateOf(prefs.sound)
        private set
    var haptics by mutableStateOf(prefs.haptics)
        private set
    var dots by mutableStateOf(prefs.dots)
        private set
    var lockWall by mutableStateOf(prefs.lockWall)
        private set
    /** Path of the user's own wallpaper, or null for the theme wallpaper. */
    var customWall by mutableStateOf(prefs.customWall)
        private set
    private var prevCustom: String? = null
    var notifAccess by mutableStateOf(false)
        private set
    var isHome by mutableStateOf(false)
        private set
    private var unread by mutableStateOf<Map<String, Long>>(emptyMap())
    private var opened by mutableStateOf(prefs.opened())

    val layout get() = LAYOUTS[cfg.layout]
    val left get() = cfg.side == Side.Left
    val dialApps by derivedStateOf { repo.apps.filter { it.key in dialKeys } }

    init {
        fb.sound = sound
        fb.haptics = haptics
        val saved = prefs.load()
        if (saved != null) {
            cfg = saved.cfg
            dialKeys = saved.keys
            snapRadius()
            jumpTo(saved.pos.toFloat())
            syncWallpaper()
        } else {
            startOb()
        }
        repo.onLoaded = ::appsLoaded
        scope.launch { Unread.state.collect { unread = it } }
        scope.launch {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000 + 20)
            }
        }
    }

    fun hasDot(app: AppEntry) =
        dots && notifAccess && (unread[app.pkgUser] ?: 0L) > (opened[app.pkgUser] ?: 0L)

    fun curApp(): AppEntry? {
        val a = dialApps
        return if (a.isEmpty()) null else a[mod(pos.roundToInt(), a.size)]
    }

    private fun appsLoaded() {
        val apps = repo.apps
        if (apps.isEmpty()) return
        if (ob != null && !obEdit && dialKeys.isEmpty()) {
            dialKeys = repo.defaults()
            return
        }
        val installed = apps.mapTo(HashSet()) { it.key }
        val kept = dialKeys.filterTo(HashSet()) { it in installed }
        if (kept.isEmpty()) {
            dialKeys = repo.defaults()
            persist()
        } else if (kept.size != dialKeys.size) {
            dialKeys = kept
            persist()
        }
    }

    private fun persist() {
        if (ob != null) return
        prefs.save(cfg, dialKeys, pos.roundToInt())
        prefs.customWall = customWall
    }

    // Activity hooks.

    fun onResume() {
        resumed = true
        now = System.currentTimeMillis()
        notifAccess = host.hasNotificationAccess()
        isHome = host.isDefaultHome()
        val o = open ?: return
        // Back from an app: the name drops back into the dial.
        if (o.phase != Phase.Out) {
            open = o.copy(phase = Phase.In)
            close(silent = true)
        }
    }

    fun onPause() {
        onCancel()
        resumed = false
    }

    fun onStop() {
        clearPull()
        cancelPress()
        if (drag?.scrub == true) {
            drag = null
            endScrub()
        }
        drag = null
        if (menu != null) closeMenu()
    }

    /** The home button while the launcher is already in front. */
    fun onHome() {
        if (menu != null) closeMenu()
        if (search) closeSearch()
    }

    fun back() {
        when {
            open != null -> close()
            menu != null -> closeMenu()
            search -> closeSearch()
            ob in Step.SHAPE..Step.FEEL -> obBack()
        }
    }

    fun release() {
        loop?.cancel()
        fb.release()
    }

    // Engine.

    private fun jumpTo(p: Float) {
        pos = p
        target = p
        lastIdx = p.roundToInt()
        vel = 0f
        spin = Spin.Idle
    }

    private fun snapRadius() {
        radius = layout.curve.radius
        rTarget = radius
    }

    private fun syncRadius() {
        val r = layout.curve.radius
        if (r != rTarget) {
            rTarget = r
            run()
        }
    }

    private fun run() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var last = 0L
            while (true) {
                val t = withFrameMillis { it }
                val dt = if (last == 0L) 16f else min(40f, (t - last).toFloat())
                last = t
                if (!step(dt)) break
            }
        }
    }

    /** One frame. Returns false when the dial is at rest. */
    private fun step(dt: Float): Boolean {
        var p = pos
        when (spin) {
            Spin.Fling -> {
                p += vel * dt
                vel *= 0.9965f.pow(dt)
                if (abs(vel) < 0.004f) {
                    target = (p + sign(vel) * 0.3f).roundToInt().toFloat()
                    spin = Spin.Settle
                }
            }
            Spin.Settle -> {
                val d = target - p
                p += d * (1 - 0.78f.pow(dt / 16f))
                if (abs(d) < 0.002f) {
                    p = target
                    spin = Spin.Idle
                }
            }
            else -> {}
        }
        pos = p
        var morph = false
        if (abs(radius - rTarget) > 0.3f) {
            radius += (rTarget - radius) * (1 - 0.84f.pow(dt / 16f))
            morph = true
        } else {
            radius = rTarget
        }
        pawl *= 0.8f.pow(dt / 16f)
        val idx = p.roundToInt()
        if (idx != lastIdx) {
            lastIdx = idx
            pawl = 1f
            val t = SystemClock.uptimeMillis()
            if (t - lastClick > 26) {
                lastClick = t
                val letters = scrub != null
                fb.click(letters)
                if (letters) fb.letter() else fb.tick()
            }
        }
        val done = spin == Spin.Idle && pawl < 0.01f && !morph
        if (done) {
            pawl = 0f
            if (ob == null && scrub == null) prefs.savePos(index)
        }
        return !done
    }

    private fun stepBy(n: Int) {
        val base = if (spin == Spin.Settle) target else pos.roundToInt().toFloat()
        target = base + n
        spin = Spin.Settle
        run()
    }

    // Gestures. All coordinates are design units.

    private fun blocked() =
        open != null || search || menu != null || ob == Step.WELCOME || ob == Step.APPS

    private fun mirX(x: Float) = if (left) W - x else x

    private fun zoneAt(x: Float, y: Float): Zone {
        if (y < 200 || y > hd - 102) return Zone.Empty
        val g = Geo(radius, cy)
        val dx = g.cx - mirX(x)
        val dy = y - cy
        val dist = hypot(dx, dy)
        if (dist <= g.r - 200 || dist >= g.r + 40) return Zone.Empty
        val k = pos + atan2(dy, dx) / g.step
        if (abs(k - pos) > g.span) return Zone.Empty
        return if (k.roundToInt() == pos.roundToInt()) Zone.Selected else Zone.Dial
    }

    fun onDown(x: Float, y: Float, touchSlop: Float, pullDistance: Float) {
        if (blocked()) return
        val zone = if (ob == null) zoneAt(x, y) else Zone.None
        val t = SystemClock.uptimeMillis()
        val d = Drag(x, y, y, t, zone)
        if (pullToLaunch && ob == null && (zone == Zone.Dial || zone == Zone.Selected)) {
            d.pull = PullToLaunch(x, y, t, left, touchSlop, pullDistance).also {
                it.update(x, y, t, curApp()?.key)
            }
        }
        d.samples.addLast(t to pos)
        drag = d
        spin = Spin.Drag
        vel = 0f
        if (zone != Zone.None) {
            press = Press(++seq, x, y)
            lpJob?.cancel()
            lpJob = scope.launch {
                delay(500)
                longPress()
            }
        }
        run()
    }

    fun onMove(x: Float, y: Float) {
        val d = drag ?: return
        if (!d.moved && (abs(y - d.y0) > 6 || abs(x - d.x0) > 6)) {
            d.moved = true
            cancelPress()
        }
        // Once armed, hold the selected row. A vertical move cancels and resumes rotation.
        val p = d.pull
        val wasArmed = pullArmed
        if (wasArmed) {
            p?.update(x, y, SystemClock.uptimeMillis(), curApp()?.key)
            if (p?.armed != true) clearPull()
        }
        if (!pullArmed) pos -= (y - d.y) / GAP
        d.y = y
        val t = SystemClock.uptimeMillis()
        d.samples.addLast(t to pos)
        while (d.samples.size > 2 && t - d.samples.first().first > 90) d.samples.removeFirst()
        if (!d.scrub && p != null) {
            if (!wasArmed) p.update(x, y, t, curApp()?.key)
            pullProgress = p.progress
            pullArmed = p.armed
            if (pullArmed && !wasArmed) {
                pos = pos.roundToInt().toFloat()
                // Snapping into the notch must not contribute artificial fling velocity.
                d.samples.clear()
                d.samples.addLast(t to pos)
                fb.arm()
            }
        }
    }

    fun onUp(x: Float, y: Float) {
        cancelPress()
        val d = drag ?: return
        drag = null
        val p = d.pull
        val wasArmed = pullArmed
        clearPull()
        if (d.scrub) {
            endScrub()
            return
        }
        val t = SystemClock.uptimeMillis()
        p?.update(x, y, t, curApp()?.key, release = true)
        if (wasArmed && p?.armed == true && p.key == curApp()?.key && !blocked()) {
            jumpTo(pos.roundToInt().toFloat())
            launchSelected()
            return
        }
        if (!d.moved && t - d.t0 < 450) {
            target = pos.roundToInt().toFloat()
            spin = Spin.Settle
            tapAt(x, y)
            run()
            return
        }
        val (t0, p0) = d.samples.first()
        vel = ((pos - p0) / max(16f, (t - t0).toFloat())).coerceIn(-0.06f, 0.06f)
        target = pos.roundToInt().toFloat()
        spin = if (abs(vel) > 0.006f) Spin.Fling else Spin.Settle
        run()
    }

    private fun clearPull() {
        pullProgress = 0f
        pullArmed = false
    }

    fun onCancel() {
        clearPull()
        cancelPress()
        val d = drag ?: return
        drag = null
        if (d.scrub) {
            endScrub()
            return
        }
        target = pos.roundToInt().toFloat()
        spin = Spin.Settle
        run()
    }

    fun onWheel(dy: Float) {
        if (blocked() || scrub != null) return
        pos += dy.coerceIn(-140f, 140f) / 90f
        vel = 0f
        spin = Spin.Wheel
        wheelJob?.cancel()
        wheelJob = scope.launch {
            delay(110)
            target = pos.roundToInt().toFloat()
            spin = Spin.Settle
            run()
        }
        run()
    }

    private fun cancelPress() {
        lpJob?.cancel()
        press = null
    }

    private fun longPress() {
        val d = drag ?: return
        if (d.moved) return
        press = null
        if (d.zone == Zone.Dial) {
            startScrub()
            return
        }
        drag = null
        target = pos.roundToInt().toFloat()
        spin = Spin.Settle
        fb.click(true)
        fb.heavy()
        if (d.zone == Zone.Selected) openMenu() else openSetup()
        run()
    }

    private fun tapAt(x: Float, y: Float) {
        if (y < 200 || y > hd - 102) return
        val g = Geo(radius, cy)
        val dx = g.cx - mirX(x)
        val dy = y - cy
        if (abs(hypot(dx, dy) - g.r) > 210) return
        val k = (pos + atan2(dy, dx) / g.step).roundToInt()
        val cur = pos.roundToInt()
        if (abs(k - pos) > g.span) return
        if (k == cur) {
            if (ob == null) launchSelected()
        } else {
            fb.tick()
            target = k.toFloat()
            spin = Spin.Settle
        }
    }

    // Letter scrub: hold the dial, and the apps become their first letters.

    private fun startScrub() {
        val apps = dialApps
        val cur = curApp() ?: return
        val letters = apps.map { it.initial }.distinct()
        scrubBase = pos.roundToInt()
        val li = letters.indexOf(cur.initial).toFloat()
        pos = li
        target = li
        lastIdx = li.roundToInt()
        spin = Spin.Drag
        drag?.scrub = true
        drag?.pull = null
        clearPull()
        fb.letter()
        fb.click(true)
        scrub = letters
    }

    private fun endScrub() {
        val letters = scrub ?: return
        val letter = letters[mod(pos.roundToInt(), letters.size)]
        val apps = dialApps
        val n = apps.size
        scrub = null
        if (n == 0) return
        val idx = apps.indexOfFirst { it.initial == letter }
        val cur = mod(scrubBase, n)
        var d = idx - cur
        if (d > n / 2f) d -= n
        if (d < -n / 2f) d += n
        pos = scrubBase.toFloat()
        lastIdx = scrubBase
        target = (scrubBase + d).toFloat()
        spin = Spin.Settle
        run()
    }

    // Onboarding and the setup editor.

    private fun tap() {
        fb.click(false)
        fb.tap()
    }

    private fun startOb() {
        jumpTo(0f)
        appPos = 0
        cfg = Cfg()
        snapRadius()
        dialKeys = if (repo.apps.isNotEmpty()) repo.defaults() else emptySet()
        prevCfg = null
        prevKeys = null
        obEdit = false
        ob = Step.WELCOME
        obSeq++
    }

    /** Hold the clock or an empty spot: shape, theme, apps and feel, with cancel and done. */
    private fun openSetup() {
        prevCfg = cfg
        prevKeys = dialKeys
        prevCustom = customWall
        obEdit = true
        search = false
        ob = Step.SHAPE
        obSeq++
    }

    private fun goTo(n: Int?) {
        val cur = ob
        if (cur == Step.THEME && n != Step.THEME) {
            cfg = cfg.copy(theme = mod(pos.roundToInt(), THEMES.size))
            jumpTo(appPos.toFloat())
        }
        if (n == Step.THEME && cur != Step.THEME) {
            appPos = pos.roundToInt()
            jumpTo(cfg.theme.toFloat())
        }
        ob = n
        obSeq++
        if (n == null) {
            persist()
            host.cleanWallpapers(customWall)
            syncWallpaper()
        }
        run()
    }

    /** Copies the theme to the lock screen when the theme or the side changed since the last copy. */
    private fun syncWallpaper() {
        if (!lockWall) return
        val custom = customWall
        val key = "${THEMES[cfg.theme].name}|${cfg.side}|${custom ?: "theme"}"
        if (prefs.appliedWall == key) return
        host.setWallpaper(THEMES[cfg.theme], PALETTES[cfg.theme], left, custom) { ok ->
            if (ok) prefs.appliedWall = key else showToast("can't set the lock screen")
        }
    }

    /** Opens the photo picker. The theme keeps the colours, and the photo replaces the theme wallpaper. */
    fun pickWallpaper() {
        tap()
        host.pickWallpaper { path, failed ->
            if (path != null) customWall = path
            else if (failed) showToast("can't open that image")
        }
    }

    fun useThemeWallpaper() {
        tap()
        customWall = null
    }

    fun toggleLockWall() {
        tap()
        lockWall = !lockWall
        prefs.lockWall = lockWall
        if (!lockWall) prefs.appliedWall = null
    }

    fun obNext() {
        tap()
        when (val o = ob ?: return) {
            Step.FEEL -> goTo(if (obEdit) null else Step.READY)
            Step.READY -> goTo(null)
            else -> goTo(o + 1)
        }
    }

    fun obBack() {
        tap()
        val o = ob ?: return
        if (o == Step.SHAPE) {
            if (!obEdit) {
                goTo(Step.WELCOME)
            } else {
                cfg = prevCfg ?: cfg
                dialKeys = prevKeys ?: dialKeys
                customWall = prevCustom
                host.cleanWallpapers(prevCustom)
                ob = null
                syncRadius()
            }
        } else if (o > Step.SHAPE) {
            goTo(o - 1)
        }
    }

    fun setLayout(i: Int) {
        tap()
        cfg = cfg.copy(layout = i)
        syncRadius()
    }

    fun setSide(side: Side) {
        tap()
        cfg = cfg.copy(side = side)
    }

    fun toggleApp(key: String) {
        val on = key in dialKeys
        if (on && dialApps.size <= 3) {
            fb.refuse()
            warn = true
            warnJob?.cancel()
            warnJob = scope.launch {
                delay(1400)
                warn = false
            }
            return
        }
        tap()
        dialKeys = if (on) dialKeys - key else dialKeys + key
    }

    fun jumpGroup(group: String) {
        val first = THEMES.indexOfFirst { it.group == group }
        val base = pos.roundToInt()
        val n = THEMES.size
        var d = first - mod(base, n)
        if (d > n / 2f) d -= n
        if (d < -n / 2f) d += n
        fb.tap()
        target = (base + d).toFloat()
        spin = Spin.Settle
        run()
    }

    fun toggleSound() {
        sound = !sound
        fb.sound = sound
        prefs.sound = sound
        tap()
    }

    fun togglePullToLaunch() {
        tap()
        pullToLaunch = !pullToLaunch
        prefs.pullToLaunch = pullToLaunch
    }

    fun toggleHaptics() {
        haptics = !haptics
        fb.haptics = haptics
        prefs.haptics = haptics
        tap()
    }

    fun toggleDots() {
        tap()
        if (!notifAccess) {
            dots = true
            prefs.dots = true
            host.openNotificationAccess()
        } else {
            dots = !dots
            prefs.dots = dots
        }
    }

    fun requestHome() {
        tap()
        host.requestHome()
    }

    fun refreshSystem() {
        notifAccess = host.hasNotificationAccess()
        isHome = host.isDefaultHome()
    }

    // Toast with undo.

    fun showToast(text: String, undo: (() -> Unit)? = null) {
        toastJob?.cancel()
        toast = Toast(++seq, text, undo)
        toastShown = true
        toastJob = scope.launch {
            delay(3200)
            toastShown = false
            delay(300)
            toast = null
        }
    }

    fun undo() {
        val t = toast
        if (t?.undo != null) {
            fb.letter()
            fb.click(false)
            t.undo.invoke()
        }
        toastJob?.cancel()
        toastShown = false
        toastJob = scope.launch {
            delay(300)
            toast = null
        }
    }

    /** Sets the dial apps and keeps the selected app under the notch. */
    private fun setAppsKeep(next: Set<String>) {
        val cur = curApp()
        if (cur != null) {
            val ni = repo.apps.filter { it.key in next }.indexOfFirst { it.key == cur.key }
            if (ni >= 0) reindex(ni)
        }
        dialKeys = next
        persist()
    }

    /**
     * Moves the dial to row [idx] of a new app list. The position counts full turns, so a plain shift
     * lands on the wrong row when the list length changes.
     */
    private fun reindex(idx: Int) {
        val base = pos.roundToInt()
        pos = idx + (pos - base)
        target = idx + (target - base)
        lastIdx = idx + (lastIdx - base)
    }

    fun addToDial(app: AppEntry) {
        val before = dialKeys
        if (app.key in before) return
        fb.double()
        fb.click(false)
        setAppsKeep(before + app.key)
        showToast("added ${app.display} to the dial") { setAppsKeep(before) }
    }

    private fun removeFromDial(app: AppEntry) {
        val before = dialKeys
        if (dialApps.size <= 3) {
            fb.refuse()
            showToast("keep at least 3 on the dial")
            return
        }
        fb.double()
        fb.click(false)
        // The next app moves up into the notch.
        val size = dialApps.size
        reindex(mod(mod(pos.roundToInt(), size), size - 1))
        dialKeys = before - app.key
        persist()
        showToast("removed ${app.display}") { setAppsKeep(before) }
    }

    // Quick actions: hold the selected app.

    private fun openMenu() {
        val app = curApp() ?: return
        menu = QuickMenu(app, repo.shortcuts(app))
        menuShown = true
    }

    fun closeMenu() {
        if (menu == null) return
        menuShown = false
        scope.launch {
            delay(200)
            if (!menuShown) menu = null
        }
    }

    private fun afterMenu(wait: Long, action: (AppEntry) -> Unit) {
        val app = menu?.app ?: return
        closeMenu()
        scope.launch {
            delay(wait)
            action(app)
        }
    }

    fun menuShortcut(info: ShortcutInfo) = afterMenu(120) { app ->
        val label = (info.shortLabel ?: info.longLabel)?.toString()?.lowercase().orEmpty()
        openApp(app.display, app.key, app.pkgUser, selectedPose() ?: return@afterMenu, label, true) {
            host.launchShortcut(info, it)
        }
    }

    fun menuInfo() = afterMenu(120) { app ->
        openApp(app.display, app.key, null, selectedPose() ?: return@afterMenu, "app info", true) {
            host.showAppInfo(app, it)
        }
    }

    fun menuRemove() = afterMenu(160) { removeFromDial(it) }

    // Search.

    fun openSearch() {
        tap()
        query = ""
        search = true
    }

    fun closeSearch() {
        fb.tap()
        search = false
    }

    fun matches(q: String): List<AppEntry> {
        val s = q.trim().lowercase()
        val all = repo.apps
        if (s.isEmpty()) return all
        return all.filter { it.label.startsWith(s) } + all.filter { !it.label.startsWith(s) && s in it.label }
    }

    fun searchEnter() {
        matches(query).firstOrNull()?.let { openFromSearch(it, 200f) }
    }

    /** Opens a search result. [y] is the row centre in design units. */
    fun openFromSearch(app: AppEntry, y: Float) {
        openApp(app.display, app.key, app.pkgUser, Pose(28f, y, 0f, 16f / 44f, 0f), "", false) {
            host.launchApp(app, it)
        }
    }

    // Opening apps.

    private fun selectedPose(): Pose? {
        val app = curApp() ?: return null
        val n = app.display.length
        val s = selScale(n)
        val p = labelPose(0f, n, s, Geo(radius, cy), layout.markers, left)
        return Pose(p.x, p.y, p.rot, s * 15f / 44f, p.tx)
    }

    fun launchSelected() {
        val app = curApp() ?: return
        val from = selectedPose() ?: return
        openApp(app.display, app.key, app.pkgUser, from, "", true) { host.launchApp(app, it) }
    }

    private fun openApp(
        label: String,
        key: String?,
        pkgUser: String?,
        from: Pose,
        sub: String,
        fromDial: Boolean,
        action: (Rect?) -> Unit,
    ) {
        if (open != null) return
        fb.click(true)
        fb.heavy()
        if (pkgUser != null) {
            val t = System.currentTimeMillis()
            opened = opened + (pkgUser to t)
            prefs.markOpened(pkgUser, t)
        }
        val o = OpenApp(++seq, label, key, sub, from, fromDial, Phase.From)
        open = o
        search = false
        menu = null
        menuShown = false
        scope.launch {
            withFrameMillis { }
            withFrameMillis { }
            if (open?.id == o.id && open?.phase == Phase.From) open = o.copy(phase = Phase.In)
        }
        try {
            action(boundsOf(from, label))
        } catch (e: Exception) {
            open = null
            showToast("can't open $label")
        }
    }

    private fun boundsOf(p: Pose, label: String): Rect {
        val w = label.length * 24.64f * p.s
        val l = p.x + p.tx * w
        return Rect(
            (l * k).roundToInt(),
            ((p.y - 14) * k).roundToInt(),
            ((l + w) * k).roundToInt(),
            ((p.y + 14) * k).roundToInt(),
        )
    }

    fun close(silent: Boolean = false) {
        val o = open ?: return
        if (o.phase != Phase.In) return
        if (!silent) {
            fb.click(true)
            fb.tap()
        }
        open = o.copy(phase = Phase.Out)
        scope.launch {
            delay(440)
            if (open?.id == o.id) open = null
        }
    }

    // Hardware keys: j and k turn the dial, l or enter opens, slash searches.

    fun key(key: Key): Boolean {
        if (open != null) {
            if (key == Key.Escape || key == Key.Q || key == Key.H || key == Key.Backspace) {
                close()
                return true
            }
            return false
        }
        if (menu != null) {
            if (key == Key.Escape) closeMenu()
            return key == Key.Escape
        }
        if (search) {
            if (key == Key.Escape) closeSearch()
            return key == Key.Escape
        }
        val o = ob
        if (o == Step.WELCOME || o == Step.APPS || o == Step.READY) {
            if (key == Key.Enter) {
                obNext()
                return true
            }
            if (key == Key.Escape && o == Step.APPS) {
                obBack()
                return true
            }
            return false
        }
        when (key) {
            Key.DirectionDown, Key.J -> stepBy(1)
            Key.DirectionUp, Key.K -> stepBy(-1)
            Key.Enter, Key.L, Key.DirectionRight -> if (o != null) obNext() else launchSelected()
            Key.Escape -> if (o != null) obBack() else return false
            Key.Slash -> if (o == null) openSearch() else return false
            else -> return false
        }
        return true
    }
}
