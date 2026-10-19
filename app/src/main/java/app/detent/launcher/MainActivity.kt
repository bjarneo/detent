package app.detent.launcher

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.core.app.NotificationManagerCompat
import app.detent.launcher.ui.DetentRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.concurrent.thread

class MainActivity : ComponentActivity(), Host {
    // AndroidUiDispatcher.Main carries the frame clock that the dial loop waits on.
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    private lateinit var repo: AppsRepository
    private lateinit var state: LauncherState
    private val launcherApps by lazy { getSystemService(LauncherApps::class.java) }
    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        state.refreshSystem()
    }
    private var pickDone: ((String?, Boolean) -> Unit)? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val done = pickDone ?: return@registerForActivityResult
        pickDone = null
        if (uri == null) {
            done(null, false)
            return@registerForActivityResult
        }
        val (w, h) = screenSize()
        val app = applicationContext
        thread(name = "detent-photo") {
            val path = runCatching { CustomWallpaper.save(app, uri, w, h).path }.getOrNull()
            runOnUiThread { done(path, path == null) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        window.isNavigationBarContrastEnforced = false
        repo = AppsRepository(applicationContext)
        state = LauncherState(applicationContext, this, repo, Prefs(applicationContext), Feedback(applicationContext), scope)
        repo.start()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = state.back()
        })
        setContent { DetentRoot(state) }
    }

    override fun onResume() {
        super.onResume()
        state.onResume()
    }

    override fun onPause() {
        super.onPause()
        state.onPause()
    }

    override fun onStop() {
        super.onStop()
        state.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val home = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        val alreadyHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (home && alreadyHome) state.onHome()
    }

    override fun onDestroy() {
        repo.stop()
        state.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun reveal(b: Rect?): Bundle? = b?.let {
        ActivityOptions.makeClipRevealAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
    }

    override fun launchApp(app: AppEntry, bounds: Rect?) =
        launcherApps.startMainActivity(app.component, app.user, bounds, reveal(bounds))

    override fun launchShortcut(info: ShortcutInfo, bounds: Rect?) =
        launcherApps.startShortcut(info, bounds, reveal(bounds))

    override fun showAppInfo(app: AppEntry, bounds: Rect?) =
        launcherApps.startAppDetailsActivity(app.component, app.user, bounds, reveal(bounds))

    override fun isDefaultHome(): Boolean =
        getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME) == true

    override fun requestHome() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            roleRequest.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else {
            open(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    override fun hasNotificationAccess() =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    override fun openNotificationAccess() {
        val cn = ComponentName(this, UnreadService::class.java).flattenToString()
        val detail = if (Build.VERSION.SDK_INT >= 30) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn)
        } else null
        if (detail == null || !open(detail)) open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun openClock(): Boolean = open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))

    override fun openCalendar(): Boolean {
        val uri = android.provider.CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
        return open(Intent(Intent.ACTION_VIEW, uri)) ||
            open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }

    override fun is24Hour() = DateFormat.is24HourFormat(this)

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= 30) {
        windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        @Suppress("DEPRECATION")
        DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }

    override fun setWallpaper(
        theme: ThemeDef,
        pal: Palette,
        left: Boolean,
        custom: String?,
        grid: Boolean,
        done: (Boolean) -> Unit,
    ) {
        val (w, h) = screenSize()
        val app = applicationContext
        thread(name = "detent-wallpaper") {
            val ok = runCatching { SystemWallpaper.apply(app, theme, pal, left, custom, grid, w, h) }.isSuccess
            runOnUiThread { done(ok) }
        }
    }

    override fun pickWallpaper(done: (path: String?, failed: Boolean) -> Unit) {
        pickDone = done
        try {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: ActivityNotFoundException) {
            pickDone = null
            done(null, true)
        }
    }

    override fun cleanWallpapers(keep: String?) {
        val app = applicationContext
        thread(name = "detent-clean") { CustomWallpaper.clean(app, keep) }
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
