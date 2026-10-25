package app.detent.launcher.ui

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.text.format.DateFormat
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.detent.launcher.DeviceStatus
import app.detent.launcher.Hear
import app.detent.launcher.Ease
import app.detent.launcher.Kind
import app.detent.launcher.LauncherState
import app.detent.launcher.PALETTES
import app.detent.launcher.Palette
import app.detent.launcher.Pose
import app.detent.launcher.QUICK_MAX
import app.detent.launcher.QuickApp
import app.detent.launcher.Phase
import app.detent.launcher.Spin
import app.detent.launcher.Step
import app.detent.launcher.THEMES
import app.detent.launcher.ThemeDef
import app.detent.launcher.handMatrix
import app.detent.launcher.W
import app.detent.launcher.mod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

internal val DAYS = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
internal val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetentRoot(s: LauncherState) {
    val base = LocalDensity.current
    // The dial geometry is fixed. A font scale of 1 keeps text sizes in design units.
    CompositionLocalProvider(LocalDensity provides Density(base.density, 1f)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val d = LocalDensity.current
            val k = constraints.maxWidth / W
            // The layout keeps the space of the status bar also when Detent hides it.
            val top = WindowInsets.statusBarsIgnoringVisibility.getTop(d) / k
            val bottom = WindowInsets.navigationBars.getBottom(d) / k
            val u = remember(k, constraints.maxHeight, top, bottom, d.density) {
                Units(k, d.density, constraints.maxHeight / k, top, bottom)
            }
            SideEffect {
                s.k = u.k
                s.hd = u.hd
                s.clockTop = u.clockTop
                s.buttonY = u.buttonY
            }
            CompositionLocalProvider(LocalUnits provides u) { Home(s) }
        }
    }
}

@Composable
private fun Home(s: LauncherState) {
    val themeStep = s.ob == Step.THEME
    val live = if (themeStep) mod(s.index, THEMES.size) else s.cfg.theme
    val pal = PALETTES[live]
    // While the theme dial turns, colours follow every tick. The wallpaper waits until the dial stops.
    var wall by remember { mutableIntStateOf(live) }
    val idle = s.spin == Spin.Idle
    LaunchedEffect(live, idle, themeStep) { if (!themeStep || idle) wall = live }
    val bg by animateColorAsState(pal.bg, tween(400), label = "bg")
    SystemBars(pal.light, s.ownBar, s.resumed)

    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val fm = LocalFocusManager.current
    LaunchedEffect(s.search) {
        if (!s.search) {
            kb?.hide()
            fm.clearFocus(force = true)
            runCatching { focus.requestFocus() }
        }
    }

    Box(
        Modifier.fillMaxSize().background(bg)
            .focusRequester(focus).focusable()
            .onKeyEvent { it.type == KeyEventType.KeyDown && s.key(it.key) },
    ) {
        // The grid layouts draw the whole screen as dots, with no wallpaper.
        val grid = s.layout.grid
        val wave = rememberVoiceWave(s)
        val ctx = LocalContext.current
        val status = remember { DeviceStatus(ctx.applicationContext) }
        if (!grid) Wallpaper(THEMES[wall], pal, s.left, s.customWall)
        GestureLayer(s)
        if (grid) {
            DotGrid(s, pal, wave, status)
        } else {
            Clock(s, pal)
            DialCanvas(s, pal, wave)
        }
        // The readout layout shows the letter in its readout.
        if (s.kind != Kind.Readout) ScrubLetter(s, pal)
        PressRing(s, pal)
        AppsHint(s, pal)
        CornerButtons(s, pal)
        HearLayer(s, pal)
        if (s.ob != null) Onboarding(s, pal, THEMES[live])
        QuickMenuView(s, pal)
        SearchSheet(s, pal)
        StatusStrip(s, pal, status)
        AppSheet(s, pal)
        Title(s, pal)
        HintCard(s, pal)
        ToastView(s, pal)
    }
}

/** Sets the icon colours of the system bars. With the Detent bar, the Android status bar hides. A swipe from the top edge still shows it. */
@Composable
private fun SystemBars(light: Boolean, hideStatus: Boolean, resumed: Boolean) {
    val view = LocalView.current
    LaunchedEffect(light, hideStatus, resumed) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideStatus) hide(WindowInsetsCompat.Type.statusBars()) else show(WindowInsetsCompat.Type.statusBars())
        }
    }
}

// Wallpaper.

private object Walls {
    private val cache = LruCache<String, ImageBitmap>(4)

    suspend fun load(ctx: Context, path: String): ImageBitmap? = cache.get(path) ?: withContext(Dispatchers.IO) {
        runCatching {
            // An absolute path is the user's own wallpaper. Anything else is an asset.
            val bitmap = if (path.startsWith("/")) BitmapFactory.decodeFile(path)
            else ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }
            bitmap?.asImageBitmap()
        }.getOrNull()
    }?.also { cache.put(path, it) }
}

/** The last loaded image for [path]. The previous image stays until the next one is ready. */
@Composable
private fun rememberAsset(path: String): Pair<String, ImageBitmap>? {
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(path) { Walls.load(ctx, path)?.let { shown = path to it } }
    return shown
}

@Composable
private fun Wallpaper(t: ThemeDef, pal: Palette, left: Boolean, custom: String?) {
    val wall = custom ?: t.wall
    if (wall == null) {
        HandArt(pal, left)
        return
    }
    val img = rememberAsset(if (custom == null && left && t.wallLeft != null) t.wallLeft else wall)
    Crossfade(img, animationSpec = tween(450), label = "wall") { p ->
        if (p != null) Image(p.second, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    val b = pal.bg
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to b.copy(alpha = .6f), .32f to b.copy(alpha = .15f), .7f to b.copy(alpha = .2f), 1f to b.copy(alpha = .75f),
            ),
        ),
    )
    // The side with the dial is darker, so the labels stay readable.
    val near = b.copy(alpha = .86f)
    val far = b.copy(alpha = .2f)
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to (if (left) near else far), .5f to b.copy(alpha = .5f), 1f to (if (left) far else near)),
        ),
    )
}

/** The dot-hand art, tinted so the dots take the hand colour and the black takes the background colour. */
@Composable
private fun HandArt(pal: Palette, left: Boolean) {
    val img = rememberAsset("hands.webp") ?: return
    val m = ColorMatrix(handMatrix(pal))
    Image(
        img.second, null,
        Modifier.fillMaxSize().graphicsLayer { scaleX = if (left) -1f else 1f },
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.colorMatrix(m),
    )
}

// Input.

@Composable
private fun GestureLayer(s: LauncherState) {
    val u = LocalUnits.current
    val k by rememberUpdatedState(u.k)
    val mid = s.cy
    Box(
        Modifier.fillMaxSize()
            // The dial sits on a screen edge. Keep the system back swipe off the rows around the notch.
            .systemGestureExclusion { c ->
                val w = c.size.width.toFloat()
                val top = (mid - 110f) * u.k
                val bottom = (mid + 110f) * u.k
                if (s.left) Rect(0f, top, 56f * u.k, bottom) else Rect(w - 56f * u.k, top, w, bottom)
            }
            .pointerInput(s) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val kk = k
                    s.onDown(down.position.x / kk, down.position.y / kk)
                    var ended = false
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.changedToUp() || !ch.pressed) {
                                s.onUp(ch.position.x / kk, ch.position.y / kk)
                                ended = true
                                break
                            }
                            if (ch.positionChanged()) {
                                s.onMove(ch.position.x / kk, ch.position.y / kk)
                                ch.consume()
                            }
                        }
                    } finally {
                        if (!ended) s.onCancel()
                    }
                }
            }
            .pointerInput(s) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        if (ev.type == PointerEventType.Scroll) {
                            s.onWheel(ev.changes.first().scrollDelta.y * 90f)
                            ev.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    )
}

// Home elements.

@Composable
private fun Clock(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val ctx = LocalContext.current
    val alpha by animateFloatAsState(
        when {
            s.ob != null -> 0f
            s.scrub != null -> .25f
            else -> 1f
        },
        tween(400), label = "clockAlpha",
    )
    val cal = Calendar.getInstance().apply { timeInMillis = s.now }
    val hour = if (DateFormat.is24HourFormat(ctx)) {
        cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    } else {
        (cal.get(Calendar.HOUR).takeIf { it != 0 } ?: 12).toString()
    }
    val time = hour + ":" + cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    val date = "${DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} ${MONTHS[cal.get(Calendar.MONTH)]}"
    // While Detent listens, the words take the place of the clock.
    val hear by animateFloatAsState(if (s.hear != null) 1f else 0f, tween(300), label = "clockHear")
    if (hear < .996f) {
        SideBox(s.left, u.clockTop, 28f, Modifier.graphicsLayer { this.alpha = alpha * (1 - hear) }) {
            Column(
                horizontalAlignment = if (s.left) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(u.dp(6f)),
            ) {
                Txt(time, 68f, pal.fg, weight = FontWeight.Light, spacing = -.05f, line = 1f)
                Txt(date, 13f, pal.muted2, spacing = .02f)
            }
        }
    }
    if (hear > .004f) Heard(s, pal, Modifier.graphicsLayer { this.alpha = hear; translationY = (1 - hear) * 6f * u.k })
}

/** The words that Detent heard, in the place of the clock. The app that they name shows below them. */
@Composable
private fun Heard(s: LauncherState, pal: Palette, modifier: Modifier) {
    val u = LocalUnits.current
    val words = s.heardText
    val app = s.heardApp
    val miss = s.hear == Hear.Miss
    val end = if (s.left) TextAlign.End else TextAlign.Start
    SideBox(s.left, u.clockTop, 28f, modifier) {
        Column(
            horizontalAlignment = if (s.left) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            val quiet = miss || words.isEmpty()
            Txt(
                if (quiet) "say an app name" else words, 34f, if (quiet) pal.muted else pal.fg,
                weight = FontWeight.Light, spacing = -.03f, line = 1.1f, align = end, maxLines = 3,
            )
            if (app != null && !miss) {
                Txt(
                    app.display, 13f, pal.accent,
                    Modifier.onGloballyPositioned {
                        val p = it.positionInRoot()
                        s.heardPose = Pose(p.x / u.k, (p.y + it.size.height / 2f) / u.k, 0f, 13f / 44f, 0f)
                    },
                    spacing = .02f, maxLines = 1,
                )
            } else {
                Txt(hearStatus(s), 13f, pal.muted2, spacing = .02f, maxLines = 1)
            }
        }
    }
}

/**
 * The hint for the app search on the free side of the bottom row. A drag up from the bottom opens the search.
 * The hint follows the finger and takes the accent colour. When the home screen shows, the arrow rises once.
 */
@Composable
private fun AppsHint(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val shown = s.ob == null && !s.search && s.hear == null && s.open == null && s.scrub == null
    val now by rememberUpdatedState(shown)
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "hintAlpha")
    val dy by animateFloatAsState(if (shown) 0f else 8f, tween(300), label = "hintY")
    val lift by animateFloatAsState(s.lift, tween(80), label = "hintLift")
    val nudge = remember { Animatable(0f) }
    LaunchedEffect(s.resumed) {
        if (!s.resumed) return@LaunchedEffect
        delay(700)
        if (!now) return@LaunchedEffect
        nudge.animateTo(1f, tween(280, easing = Ease))
        nudge.animateTo(0f, tween(460, easing = Ease))
    }
    if (alpha < .004f) return
    val color = lerp(pal.muted2, pal.accent, lift)
    SideBox(s.left, u.buttonY, 28f, Modifier.graphicsLayer { this.alpha = alpha; translationY = (dy - lift * 10f) * u.k }) {
        // No pointer input here. The drag goes to the gesture layer below.
        Row(
            Modifier.height(u.dp(44f)).semantics {
                contentDescription = "apps"
                onClick { s.openSearch(); true }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(8f)),
        ) {
            Canvas(Modifier.size(u.dp(10f)).graphicsLayer { translationY = -nudge.value * 4f * u.k }) {
                val k = u.k
                val w = 1.5f * k
                drawLine(color, Offset(1f * k, 7f * k), Offset(5f * k, 3f * k), strokeWidth = w, cap = StrokeCap.Round)
                drawLine(color, Offset(5f * k, 3f * k), Offset(9f * k, 7f * k), strokeWidth = w, cap = StrokeCap.Round)
            }
            Txt("apps", 13f, color, spacing = .02f)
        }
    }
}

/** The web button. Type words, and the browser or the search app searches the web for them. */
@Composable
private fun WebButton(s: LauncherState, pal: Palette, idle: Boolean) {
    val u = LocalUnits.current
    Box(
        Modifier.size(u.dp(44f)).clip(CircleShape).background(pal.btnBg).border(u.dp(1f), pal.rim2, CircleShape)
            .semantics { contentDescription = "web search" }
            .tap(enabled = idle) { s.openWeb() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(u.dp(16f))) { drawQuickIcon("globe", pal.fg, Offset.Zero, size.width, 1.5f * u.k) }
    }
}

/** The voice button. Say the name of an app to open it. */
@Composable
private fun VoiceButton(s: LauncherState, pal: Palette, idle: Boolean) {
    val u = LocalUnits.current
    // The centre of the button in design units.
    var centre by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(u.dp(44f)).clip(CircleShape).background(pal.btnBg).border(u.dp(1f), pal.rim2, CircleShape)
            .onGloballyPositioned {
                val p = it.positionInRoot()
                centre = Offset((p.x + it.size.width / 2f) / u.k, (p.y + it.size.height / 2f) / u.k)
            }
            .semantics { contentDescription = "voice" }
            .tap(enabled = idle) { s.voiceTap(centre.x, centre.y) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(u.dp(14f))) {
            val k = u.k
            val line = Stroke(1.5f * k)
            drawRoundRect(pal.fg, Offset(4.5f * k, .75f * k), Size(5f * k, 8f * k), CornerRadius(2.5f * k), style = line)
            drawArc(pal.fg, 0f, 180f, false, Offset(2f * k, 3.5f * k), Size(10f * k, 8f * k), style = line)
            drawLine(pal.fg, Offset(7f * k, 11.5f * k), Offset(7f * k, 13.5f * k), strokeWidth = 1.5f * k)
        }
    }
}

/**
 * The buttons in the bottom corner on the dial side: up to 3 quick buttons, the web button, and the voice button
 * at the screen edge. The first quick button sits next to the web button. A drag up from the bottom opens search.
 * The feel step turns the web button and the voice button on and off.
 */
@Composable
private fun CornerButtons(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val homeIdle = s.ob == null && !s.search && s.hear == null && s.open == null
    val shown = homeIdle && s.scrub == null
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "cornerAlpha")
    val dy by animateFloatAsState(if (shown) 0f else 8f, tween(300), label = "cornerY")
    SideBox(!s.left, u.buttonY, 24f, Modifier.graphicsLayer { this.alpha = alpha; translationY = dy * u.k }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u.dp(10f))) {
            if (s.left) {
                if (s.voice) VoiceButton(s, pal, homeIdle)
                if (s.webSearch) WebButton(s, pal, homeIdle)
            }
            val quick = s.quickApps
            if (quick.isEmpty()) AddQuickButton(s, pal, homeIdle)
            // With one quick app, the button can show the full name. With more, each button is a circle.
            val solo = quick.size == 1
            for (q in if (s.left) quick else quick.asReversed()) key(q.app.key) { QuickButton(s, pal, homeIdle, q, solo) }
            if (!s.left) {
                if (s.webSearch) WebButton(s, pal, homeIdle)
                if (s.voice) VoiceButton(s, pal, homeIdle)
            }
        }
    }
}

/** A quick button. A tap opens its app. Hold it for the shortcuts of the app and the actions of the button. */
@Composable
private fun QuickButton(s: LauncherState, pal: Palette, homeIdle: Boolean, q: QuickApp, solo: Boolean) {
    val u = LocalUnits.current
    val idle by rememberUpdatedState(homeIdle)
    val slot by rememberUpdatedState(q.slot)
    val app = q.app
    val icon = q.icon
    val unread = s.hasDot(app)
    // The start and the centre of the label in design units. The name lifts off from here when the app opens.
    var label by remember { mutableStateOf(Offset.Zero) }
    val gestures = Modifier.pointerInput(s) {
        detectTapGestures(
            onTap = { if (idle) s.quickTap(slot, label.x, label.y) },
            onLongPress = { if (idle) s.quickHold(slot, label.x, label.y) },
        )
    }
    if (!solo || icon != null) {
        // The icon, or the first letter of the name, takes the place of the name. A new notification adds an accent dot.
        Box(
            Modifier.size(u.dp(44f)).clip(CircleShape).background(pal.btnBg).border(u.dp(1f), pal.rim2, CircleShape)
                .onGloballyPositioned {
                    val p = it.positionInRoot()
                    label = Offset((p.x + it.size.width / 2f) / u.k - 6f, (p.y + it.size.height / 2f) / u.k)
                }
                .semantics { contentDescription = app.display }
                .then(gestures),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == null) Txt(app.initial, 17f, pal.fg, line = 1f)
            Canvas(Modifier.size(u.dp(44f))) {
                val k = u.k
                if (icon != null) drawQuickIcon(icon, pal.fg, Offset(12f * k, 12f * k), 20f * k, 1.5f * k)
                if (unread) drawCircle(pal.accent, 3f * k, Offset(33f * k, 11f * k))
            }
        }
        return
    }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(u.dp(44f)).clip(shape).background(pal.btnBg).border(u.dp(1f), pal.rim2, shape)
            .then(gestures)
            .padding(start = u.dp(14f), end = u.dp(18f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
    ) {
        Canvas(Modifier.size(u.dp(14f))) {
            drawCircle(if (unread) pal.accent else pal.fg, 3.5f * u.k, Offset(7f * u.k, 7f * u.k))
        }
        Txt(
            app.display, 13f, pal.fg,
            Modifier.onGloballyPositioned {
                val p = it.positionInRoot()
                label = Offset(p.x / u.k, (p.y + it.size.height / 2f) / u.k)
            },
            maxLines = 1,
        )
    }
}

/** The empty quick button. A tap picks the first quick app. */
@Composable
private fun AddQuickButton(s: LauncherState, pal: Palette, homeIdle: Boolean) {
    val u = LocalUnits.current
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(u.dp(44f)).clip(shape).background(pal.btnBg).border(u.dp(1f), pal.rim2, shape)
            .tap(enabled = homeIdle) { s.quickAdd() }
            .padding(start = u.dp(14f), end = u.dp(18f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
    ) {
        Canvas(Modifier.size(u.dp(14f))) {
            val k = u.k
            drawLine(pal.fg, Offset(2f * k, 7f * k), Offset(12f * k, 7f * k), strokeWidth = 1.5f * k)
            drawLine(pal.fg, Offset(7f * k, 2f * k), Offset(7f * k, 12f * k), strokeWidth = 1.5f * k)
        }
        Txt("quick app", 13f, pal.fg, maxLines = 1)
    }
}

@Composable
private fun ScrubLetter(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val letters = s.scrub
    var letter by remember { mutableStateOf("") }
    if (letters != null) letter = letters[mod(s.index, letters.size)]
    val on = letters != null
    val alpha by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "scrubAlpha")
    val t by animateFloatAsState(if (on) 1f else 0f, tween(250, easing = Ease), label = "scrubMove")
    SideBox(s.left, s.cy - 66f, 36f) {
        Box(
            Modifier.height(u.dp(132f)).graphicsLayer {
                this.alpha = alpha
                translationY = (1 - t) * 6f * u.k
                scaleX = .96f + .04f * t
                scaleY = .96f + .04f * t
            },
            contentAlignment = Alignment.Center,
        ) {
            Txt(letter, 112f, pal.accent, weight = FontWeight.Light, spacing = -.04f, line = 1f)
        }
    }
}

@Composable
private fun PressRing(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val p = s.press ?: return
    key(p.id) {
        val scale = remember { Animatable(.35f) }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { scale.animateTo(1f, tween(500, easing = LinearEasing)) }
            alpha.animateTo(.9f, tween(300))
        }
        Box(
            Modifier.place(u, p.x - 36f, p.y - 36f).size(u.dp(72f))
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                .border(u.dp(1.5f), pal.accent, CircleShape),
        )
    }
}

@Composable
private fun ToastView(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val d = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(d) / u.k
    val y = min(u.buttonY - 56f, u.hd - ime - 60f)
    var last by remember { mutableStateOf(s.toast) }
    s.toast?.let { last = it }
    val t = last ?: return
    val move = (10 * u.k).toInt()
    Box(Modifier.fillMaxWidth().place(u, 0f, y), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            s.toastShown,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300, easing = Ease)) { move },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300, easing = Ease)) { move },
        ) {
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.height(u.dp(44f)).shadow(u.dp(10f), shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape).background(pal.card).border(u.dp(1f), pal.rim2, shape)
                    .padding(start = u.dp(18f), end = u.dp(4f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u.dp(4f)),
            ) {
                Txt(t.text, 12f, pal.fg, maxLines = 1)
                if (t.undo != null) {
                    Box(Modifier.height(u.dp(44f)).tap { s.undo() }.padding(horizontal = u.dp(14f)), contentAlignment = Alignment.Center) {
                        Txt("undo", 12f, pal.accent, weight = FontWeight.Medium)
                    }
                } else {
                    Spacer(Modifier.width(u.dp(14f)))
                }
            }
        }
    }
}

/** A hint to add an app that you often open from search, or to remove a dial app that you do not open. */
@Composable
private fun HintCard(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    var last by remember { mutableStateOf(s.dialHint) }
    s.dialHint?.let { last = it }
    val h = last ?: return
    val homeIdle = s.ob == null && !s.search && s.hear == null && s.open == null && s.menu == null
    val move = (10 * u.k).toInt()
    // The card ends above the bottom row.
    Box(
        Modifier.fillMaxSize().padding(start = u.dp(24f), end = u.dp(24f), bottom = u.dp(u.hd - u.buttonY + 14f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            s.dialHint != null && homeIdle,
            enter = fadeIn(tween(250)) + slideInVertically(tween(300, easing = Ease)) { move },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(300, easing = Ease)) { move },
        ) {
            val shape = RoundedCornerShape(u.dp(16f))
            val name = h.app.display
            Column(
                Modifier.widthIn(max = u.dp(364f)).fillMaxWidth()
                    .shadow(u.dp(16f), shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape).background(pal.card).border(u.dp(1f), pal.rim2, shape)
                    .tap { }
                    .padding(start = u.dp(18f), top = u.dp(16f), end = u.dp(4f)),
            ) {
                Txt(
                    if (h.add) "add $name to the dial?" else "remove $name from the dial?", 14f, pal.fg,
                    Modifier.padding(end = u.dp(14f)), maxLines = 2,
                )
                Txt(
                    if (h.add) "you opened it from search ${h.count} times this week."
                    else "you have not opened it from the home screen in ${h.count / 7} weeks.",
                    12f, pal.muted2, Modifier.padding(top = u.dp(6f), end = u.dp(14f)), line = 1.45f,
                )
                Row(Modifier.fillMaxWidth().padding(top = u.dp(4f)), horizontalArrangement = Arrangement.End) {
                    HintAction(if (h.add) "not now" else "keep", pal.muted2, FontWeight.Normal) { s.skipHint() }
                    HintAction(if (h.add) "add" else "remove", pal.accent, FontWeight.Medium) { s.takeHint() }
                }
            }
        }
    }
}

@Composable
private fun HintAction(label: String, color: Color, weight: FontWeight, onClick: () -> Unit) {
    val u = LocalUnits.current
    Box(Modifier.height(u.dp(44f)).tap(onClick = onClick).padding(horizontal = u.dp(14f)), contentAlignment = Alignment.Center) {
        Txt(label, 12f, color, weight = weight)
    }
}

@Composable
private fun QuickMenuView(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    var last by remember { mutableStateOf(s.menu) }
    s.menu?.let { last = it }
    if (s.menu == null) return
    val m = last ?: return
    val veil by animateFloatAsState(if (s.menuShown) 1f else 0f, tween(200), label = "menuVeil")
    val op by animateFloatAsState(if (s.menuShown) 1f else 0f, tween(180), label = "menuAlpha")
    val t by animateFloatAsState(if (s.menuShown) 1f else 0f, tween(220, easing = Ease), label = "menuMove")
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil }.background(pal.veil).tap { s.closeMenu() })
    // A quick button opens its menu above the button. The menu grows from the bottom.
    val quick = m.slot != null
    val body = @Composable {
        val shape = RoundedCornerShape(u.dp(16f))
        Column(
            Modifier.width(u.dp(220f))
                .graphicsLayer {
                    alpha = op
                    scaleX = .95f + .05f * t
                    scaleY = .95f + .05f * t
                    translationY = (if (quick) 6f else -6f) * (1 - t) * u.k
                    transformOrigin = TransformOrigin(if (s.left) .12f else .88f, if (quick) 1f else 0f)
                }
                .shadow(u.dp(16f), shape).clip(shape).background(pal.card).border(u.dp(1f), pal.rim2, shape)
                .tap { }
                .padding(u.dp(6f)),
        ) {
            Txt(m.app.display, 11f, pal.muted, Modifier.padding(start = u.dp(12f), top = u.dp(10f), end = u.dp(12f), bottom = u.dp(8f)), spacing = .06f)
            m.shortcuts.forEach { info ->
                val label = (info.shortLabel ?: info.longLabel)?.toString()?.lowercase().orEmpty()
                MenuItem(label, pal.fg) { s.menuShortcut(info) }
            }
            if (m.shortcuts.isNotEmpty()) Box(Modifier.fillMaxWidth().height(u.dp(1f)).background(pal.rim))
            if (quick) {
                MenuItem("change icon", pal.muted2) { s.menuQuickIcon() }
                MenuItem("change app", pal.muted2) { s.menuQuickApp() }
                if (s.quick.size < QUICK_MAX) MenuItem("add a quick app", pal.muted2) { s.menuQuickAdd() }
                MenuItem("remove quick app", pal.muted2) { s.menuQuickRemove() }
            } else {
                MenuItem("app info", pal.muted2) { s.menuInfo() }
                MenuItem("remove from dial", pal.muted2) { s.menuRemove() }
            }
        }
    }
    if (quick) {
        Box(
            Modifier.fillMaxSize().padding(start = u.dp(24f), end = u.dp(24f), bottom = u.dp(u.hd - u.buttonY + 8f)),
            contentAlignment = if (s.left) Alignment.BottomStart else Alignment.BottomEnd,
        ) { body() }
    } else {
        // Below the selected app. Near the bottom, the menu moves up so that it stays on the screen.
        SideBox(s.left, min(s.anchorY + 30f, u.hd - u.bottom - 330f), 24f) { body() }
    }
}

@Composable
private fun MenuItem(label: String, color: Color, onClick: () -> Unit) {
    val u = LocalUnits.current
    Box(
        Modifier.fillMaxWidth().height(u.dp(44f)).clip(RoundedCornerShape(u.dp(10f))).tap(onClick = onClick)
            .padding(horizontal = u.dp(12f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Txt(label, 13f, color, maxLines = 1)
    }
}

// Opening an app: a sheet fades in and the name lifts off the dial into the title.

@Composable
private fun AppSheet(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val o = s.open ?: return
    // The grid layouts open apps with their dot ripple.
    if (o.ripple) return
    val shown = o.phase == Phase.In
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(280), label = "sheetAlpha")
    val scale by animateFloatAsState(if (shown) 1f else .985f, tween(400, easing = Ease), label = "sheetScale")
    Box(
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale }
            .background(pal.sheet).tap { s.close() },
    ) {
        if (o.sub.isNotEmpty()) {
            Txt(o.sub, 12f, pal.muted2, Modifier.place(u, 28f, max(150f, u.top + 110f)), spacing = .04f)
        }
    }
}

@Composable
private fun Title(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val o = s.open ?: return
    if (o.ripple) return
    val measurer = rememberTextMeasurer()
    val style = remember(u) { textStyle(u, 44f, pal.accent, FontWeight.Light, spacing = -.04f) }
    val prog = remember(o.id) { Animatable(0f) }
    val alpha = remember(o.id) { Animatable(1f) }
    LaunchedEffect(o.id, o.phase) {
        when (o.phase) {
            Phase.From -> prog.snapTo(0f)
            Phase.In -> prog.animateTo(1f, tween(440, easing = Ease))
            Phase.Out -> {
                if (!o.fromDial) launch { alpha.animateTo(0f, tween(250)) }
                prog.animateTo(0f, tween(440, easing = Ease))
            }
        }
    }
    // A long name shrinks so it fits between the margins.
    val fit = min(1f, 356f / (o.label.length * 24.64f))
    Canvas(Modifier.fillMaxSize()) {
        val t = prog.value
        val f = o.from
        fun lerp(a: Float, b: Float) = a + (b - a) * t
        val x = lerp(f.x, 28f)
        val y = lerp(f.y, u.titleY)
        val rot = lerp(f.rot, 0f)
        val sc = lerp(f.s, fit)
        val tx = lerp(f.tx, 0f)
        val text = measurer.measure(o.label, style)
        withTransform({
            translate(x * u.k, y * u.k)
            rotate(Math.toDegrees(rot.toDouble()).toFloat(), Offset.Zero)
            scale(sc, sc, Offset.Zero)
        }) {
            drawText(text, pal.accent, Offset(tx * text.size.width, -text.size.height / 2f), alpha = alpha.value)
        }
    }
}
