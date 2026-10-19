package app.detent.launcher.ui

import app.detent.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.detent.launcher.AppEntry
import app.detent.launcher.Ease
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun SearchSheet(s: LauncherState, pal: Palette) {
    val u = LocalUnits.current
    val move = (40 * u.k).toInt()
    AnimatedVisibility(
        s.search,
        enter = fadeIn(tween(300)) + slideInVertically(tween(400, easing = Ease)) { move },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(400, easing = Ease)) { move },
    ) {
        val focus = remember { FocusRequester() }
        val kb = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            delay(80)
            runCatching { focus.requestFocus() }
            kb?.show()
        }
        val q = s.query
        val results = remember(q, s.repo.apps) { s.matches(q) }
        Column(Modifier.fillMaxSize().background(pal.sheet).tap { }.imePadding()) {
            Spacer(Modifier.height(u.dp(max(60f, u.top + 20f))))
            Row(
                Modifier.padding(start = u.dp(28f), end = u.dp(18f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
            ) {
                BasicTextField(
                    value = q,
                    onValueChange = { s.query = it },
                    modifier = Modifier.weight(1f).height(u.dp(52f)).focusRequester(focus)
                        .onPreviewKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                                s.closeSearch()
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    textStyle = textStyle(u, 26f, pal.fg, FontWeight.Light, spacing = -.03f),
                    cursorBrush = SolidColor(pal.accent),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { s.searchEnter() }),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxSize().drawBehind {
                                val y = size.height - u.k / 2
                                drawLine(pal.rim2, Offset(0f, y), Offset(size.width, y), strokeWidth = u.k)
                            },
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (q.isEmpty()) Txt(if (s.pickFavorites) stringResource(R.string.favorites) else if (s.pick) stringResource(R.string.pick_a_quick_app) else stringResource(R.string.search_apps), 20f, pal.muted, weight = FontWeight.Light, spacing = -.03f, maxLines = 1)
                            inner()
                        }
                    },
                )
                Box(Modifier.height(u.dp(44f)).tap { s.closeSearch() }.padding(horizontal = u.dp(10f)), contentAlignment = Alignment.Center) {
                    Txt(stringResource(if (s.pickFavorites) R.string.done else R.string.cancel), 13f, pal.muted2)
                }
            }
            val n = s.dialApps.size
            val count = when {
                s.pickFavorites -> stringResource(R.string.favorites_count, s.favoriteKeys.size)
                q.isNotBlank() -> pluralStringResource(R.plurals.matches, results.size, results.size)
                s.pick -> stringResource(R.string.tap_an_app_for_the_quick_button)
                else -> stringResource(R.string.count_apps_other_on_dial, results.size, n)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = u.dp(28f), top = u.dp(16f), end = u.dp(18f), bottom = u.dp(8f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(count, 11f, pal.muted, Modifier.weight(1f), spacing = .06f, maxLines = 1)
                if (s.pick && s.quickKey != null) {
                    Box(Modifier.height(u.dp(32f)).tap { s.clearQuick() }.padding(horizontal = u.dp(10f)), contentAlignment = Alignment.Center) {
                        Txt(stringResource(R.string.clear), 11f, pal.accent, spacing = .06f)
                    }
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = u.dp(12f), end = u.dp(12f), bottom = u.dp(48f)),
            ) {
                itemsIndexed(results, key = { _, a -> a.key }) { i, app ->
                    ResultRow(s, pal, app, highlight = q.isNotBlank() && i == 0)
                }
            }
        }
    }
}

@Composable
private fun ResultRow(s: LauncherState, pal: Palette, app: AppEntry, highlight: Boolean) {
    val u = LocalUnits.current
    var centre by remember { mutableFloatStateOf(0f) }
    val onDial = app.key in s.dialKeys
    Row(
        Modifier.fillMaxWidth().height(u.dp(48f)).clip(RoundedCornerShape(u.dp(10f)))
            .background(if (highlight) pal.hover else Color.Transparent)
            .onGloballyPositioned { centre = it.positionInRoot().y + it.size.height / 2f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .tap { if (s.pickFavorites) s.toggleFavorite(app) else if (s.pick) s.setQuick(app) else s.openFromSearch(app, centre / u.k) }
                .padding(horizontal = u.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(u.dp(10f)),
        ) {
            Txt(app.label, 16f, pal.fg, Modifier.weight(1f, fill = false), maxLines = 1)
            if (s.hasDot(app)) Box(Modifier.size(u.dp(5f)).clip(CircleShape).background(pal.accent))
        }
        if (s.pickFavorites) {
            val selected = app.key in s.favoriteKeys
            Box(Modifier.height(u.dp(44f)).tap { s.toggleFavorite(app) }.padding(horizontal = u.dp(16f)), contentAlignment = Alignment.Center) {
                Txt(stringResource(if (selected) R.string.remove_favorite else R.string.add_favorite), 12f, if (selected) pal.accent else pal.fg)
            }
        } else if (s.pick) {
            if (app.key == s.quickKey) Txt(stringResource(R.string.quick_app), 11f, pal.muted, Modifier.padding(horizontal = u.dp(16f)), spacing = .04f)
        } else if (onDial) {
            Txt(stringResource(R.string.on_dial), 11f, pal.muted, Modifier.padding(horizontal = u.dp(16f)), spacing = .04f)
        } else {
            Box(
                Modifier.height(u.dp(44f)).tap { s.addToDial(app) }.padding(start = u.dp(4f), end = u.dp(8f)),
                contentAlignment = Alignment.Center,
            ) {
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier.height(u.dp(28f)).clip(shape).border(u.dp(1f), pal.rim2, shape).padding(horizontal = u.dp(12f)),
                    contentAlignment = Alignment.Center,
                ) { Txt(stringResource(R.string.dial_message), 11f, pal.fg, maxLines = 1) }
            }
        }
    }
}
