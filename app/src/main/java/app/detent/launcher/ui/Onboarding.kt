package app.detent.launcher.ui

import app.detent.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.detent.launcher.Ease
import app.detent.launcher.Kind
import app.detent.launcher.LAYOUTS
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import app.detent.launcher.Side
import app.detent.launcher.Step
import app.detent.launcher.ThemeDef
import kotlinx.coroutines.launch

private class Copy(val counter: String, val title: String, val body: String)

@Composable
private fun copy(step: Int, custom: Boolean, grid: Boolean) = when (step) {
    Step.WELCOME -> Copy("", stringResource(R.string.dial), stringResource(R.string.your_apps_sit_on_a_wheel_at_the_edge_turn_it_to_choose_tap_to_open))
    Step.SHAPE -> Copy("1 / 4", stringResource(R.string.shape), stringResource(R.string.pick_a_layout_and_a_side_then_turn_the_dial_to_try_it))
    Step.THEME -> if (grid) Copy("2 / 4", stringResource(R.string.theme), stringResource(R.string.turn_the_dial_to_pick_the_colours_of_the_grid))
    else if (custom) Copy("2 / 4", stringResource(R.string.theme), stringResource(R.string.turn_the_dial_to_pick_the_colours_your_wallpaper_stays))
    else Copy("2 / 4", stringResource(R.string.theme), stringResource(R.string.turn_the_dial_to_try_one_each_brings_its_own_wallpaper))
    Step.APPS -> Copy("3 / 4", stringResource(R.string.apps), stringResource(R.string.pick_what_goes_on_the_dial_the_rest_stay_one_search_away))
    Step.FEEL -> Copy("4 / 4", stringResource(R.string.feel), stringResource(R.string.a_click_and_a_tick_for_every_detent_dots_mark_new_notifications_the_lock_screen_can_use_the_theme))
    else -> Copy("", stringResource(R.string.ready), stringResource(R.string.hold_the_clock_to_change_your_setup_hold_an_app_for_shortcuts_or_the_dial_to_jump_by_letter))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun Onboarding(s: LauncherState, pal: Palette, theme: ThemeDef) {
    val u = LocalUnits.current
    val step = s.ob ?: return
    val c = copy(step, s.customWall != null, s.layout.grid)

    // Each step fades in and rises 8 units.
    val fade = remember { Animatable(1f) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(s.obSeq) {
        fade.snapTo(0f)
        rise.snapTo(0f)
        launch { fade.animateTo(1f, tween(350)) }
        rise.animateTo(1f, tween(400, easing = Ease))
    }
    val enter = Modifier.graphicsLayer {
        alpha = fade.value
        translationY = (1 - rise.value) * 8f * u.k
    }

    val controlsY = u.navY - 170f
    Box(Modifier.fillMaxSize()) {
        if (step == Step.APPS) Box(Modifier.fillMaxSize().background(pal.veil))
        if (step == Step.SHAPE || step == Step.THEME || step == Step.FEEL) {
            Box(
                Modifier.fillMaxWidth().place(u, 0f, controlsY - 70f).height(u.dp(u.hd - controlsY + 70f))
                    .background(Brush.verticalGradient(0f to pal.bg.copy(alpha = 0f), .3f to pal.bg, 1f to pal.bg)),
            )
        }

        SideBox(s.left, 84f, 28f, enter) {
            Column(Modifier.width(u.dp(170f)), verticalArrangement = Arrangement.spacedBy(u.dp(14f))) {
                val align = if (s.left) TextAlign.End else TextAlign.Start
                Txt(c.counter, 11f, pal.muted, Modifier.fillMaxWidth().heightIn(min = u.dp(14f)), spacing = .08f, align = align)
                Txt(c.title, 52f, pal.fg, Modifier.fillMaxWidth(), weight = FontWeight.Light, spacing = -.05f, line = 1f, align = align)
                Txt(c.body, 13f, pal.muted2, Modifier.fillMaxWidth(), line = 1.6f, align = align)
            }
        }

        if (step == Step.APPS) AppChips(s, pal, enter, bottom = u.navY - 20f)

        Column(
            Modifier.fillMaxWidth().place(u, 0f, controlsY).padding(horizontal = u.dp(28f)),
            verticalArrangement = Arrangement.spacedBy(u.dp(12f)),
            horizontalAlignment = if (s.left) Alignment.End else Alignment.Start,
        ) {
            val gap = Arrangement.spacedBy(u.dp(8f))
            when (step) {
                Step.SHAPE -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(u.dp(6f))) {
                        LAYOUTS.indices.forEach { i ->
                            Chip("${i + 1}", s.cfg.layout == i, pal, { s.setLayout(i) }, square = true, size = 38f)
                        }
                    }
                    Row(horizontalArrangement = gap) {
                        Chip(stringResource(R.string.dial_right), s.cfg.side == Side.Right, pal, { s.setSide(Side.Right) })
                        Chip(stringResource(R.string.dial_left), s.cfg.side == Side.Left, pal, { s.setSide(Side.Left) })
                    }
                }
                Step.THEME -> {
                    Row(horizontalArrangement = gap) {
                        Chip(stringResource(R.string.phone), theme.group == "phone", pal, { s.jumpGroup("phone") }, weight = FontWeight.Medium)
                        Chip("omarchy", theme.group == "omarchy", pal, { s.jumpGroup("omarchy") }, weight = FontWeight.Medium)
                    }
                    // The grid layouts have no wallpaper.
                    if (!s.layout.grid) Row(horizontalArrangement = gap) {
                        Chip(stringResource(R.string.theme_wallpaper), s.customWall == null, pal, { s.useThemeWallpaper() })
                        Chip(stringResource(R.string.your_wallpaper), s.customWall != null, pal, { s.pickWallpaper() })
                    }
                }
                Step.FEEL -> FlowRow(Modifier.padding(top = u.dp(42f)), horizontalArrangement = gap) {
                    Chip(stringResource(R.string.sound), s.sound, pal, { s.toggleSound() })
                    Chip(stringResource(R.string.haptics), s.haptics, pal, { s.toggleHaptics() })
                    Chip(stringResource(R.string.dots), s.dots && s.notifAccess, pal, { s.toggleDots() })
                    Chip(stringResource(R.string.lock_screen), s.lockWall, pal, { s.toggleLockWall() })
                }
                Step.READY -> if (!s.isHome) Row(Modifier.padding(top = u.dp(42f))) {
                    Chip(stringResource(R.string.set_as_home_app), false, pal, { s.requestHome() })
                }
            }
            val pick = when (step) {
                Step.SHAPE -> stringResource(LAYOUTS[s.cfg.layout].name)
                // The readout dial has no names, so the theme name goes here.
                Step.THEME -> (if (s.kind == Kind.Readout) theme.name + " · " else "") +
                    (if (theme.group == "phone") stringResource(R.string.phone) else theme.group) + " · " + if (theme.light) stringResource(R.string.light) else stringResource(R.string.dark)
                Step.FEEL -> when {
                    s.dots && !s.notifAccess -> stringResource(R.string.dots_need_notification_access)
                    s.lockWall -> stringResource(R.string.the_lock_screen_uses_the_theme)
                    else -> ""
                }
                Step.READY -> if (s.isHome) stringResource(R.string.detent_is_your_home_app) else ""
                else -> ""
            }
            Txt(pick, 12f, pal.fg, Modifier.heightIn(min = u.dp(16f)), spacing = .02f)
        }

        Nav(s, pal, step)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppChips(s: LauncherState, pal: Palette, enter: Modifier, bottom: Float) {
    val u = LocalUnits.current
    val top = 262f
    val n = s.dialApps.size
    val all = s.repo.apps
    Column(
        enter.fillMaxWidth().place(u, 0f, top).height(u.dp(bottom - top)).padding(horizontal = u.dp(24f)),
        verticalArrangement = Arrangement.spacedBy(u.dp(14f)),
    ) {
        FlowRow(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(u.dp(6f)),
            verticalArrangement = Arrangement.spacedBy(u.dp(6f)),
        ) {
            all.forEach { app ->
                Chip(app.display, app.key in s.dialKeys, pal, { s.toggleApp(app.key) }, padding = 13f, offBg = pal.chipBg, ms = 150)
            }
        }
        val warn = s.warn
        val color by animateColorAsState(if (warn) pal.accent else pal.muted2, tween(200), label = "countColor")
        Txt(
            if (warn) stringResource(R.string.keep_at_least_3_on_the_dial) else stringResource(R.string.count_on_the_dial_other_in_search, n, all.size - n),
            12f, color, Modifier.padding(horizontal = u.dp(4f)),
        )
    }
}

@Composable
private fun Nav(s: LauncherState, pal: Palette, step: Int) {
    val u = LocalUnits.current
    val showBack = step != Step.WELCOME && step != Step.READY
    val back = if (step == Step.SHAPE && s.obEdit) stringResource(R.string.cancel) else stringResource(R.string.back)
    val next = when (step) {
        Step.WELCOME -> stringResource(R.string.begin)
        Step.FEEL -> if (s.obEdit) stringResource(R.string.done) else stringResource(R.string.next)
        Step.READY -> stringResource(R.string.go_home)
        else -> stringResource(R.string.next)
    }
    Box(Modifier.fillMaxWidth().place(u, 16f, u.navY).padding(end = u.dp(44f)).height(u.dp(44f))) {
        if (showBack) {
            Box(
                Modifier.align(Alignment.CenterStart).height(u.dp(44f)).tap { s.obBack() }.padding(horizontal = u.dp(12f)),
                contentAlignment = Alignment.Center,
            ) { Txt(back, 13f, pal.muted2) }
        }
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val bg by animateColorAsState(pal.accent, tween(300), label = "nextBg")
        Row(
            Modifier.align(Alignment.CenterEnd).height(u.dp(44f))
                .graphicsLayer { val sc = if (pressed) .97f else 1f; scaleX = sc; scaleY = sc }
                .clip(RoundedCornerShape(50)).background(bg)
                .clickable(source, null) { s.obNext() }
                .padding(horizontal = u.dp(22f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            Txt(next, 13f, pal.onAccent, weight = FontWeight.Medium)
            Txt("→", 13f, pal.onAccent, weight = FontWeight.Medium)
        }
    }
}
