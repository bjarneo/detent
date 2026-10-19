package app.detent.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.detent.launcher.Kind
import app.detent.launcher.LauncherState
import app.detent.launcher.Palette
import app.detent.launcher.R

/** The search button expands upward; its search row stays at the original button position. */
@Composable
fun FavoritesPanel(s: LauncherState, pal: Palette) {
    if (!s.favoritesExpanded || s.ob != null || s.search || s.open != null || s.scrub != null || s.menu != null) return
    val u = LocalUnits.current
    val apps = s.favoriteApps
    val extraHeight = apps.size * 44f + 44f + 1f + 8f
    val shape = RoundedCornerShape(u.dp(22f))
    SideBox(s.left, u.searchY - extraHeight, 24f) {
        Column(Modifier.width(u.dp(190f)).clip(shape).background(pal.sheet)
            .border(u.dp(1f), pal.rim2, shape)) {
            Column(Modifier.padding(start = u.dp(14f), end = u.dp(14f), top = u.dp(8f))) {
                apps.forEachIndexed { index, app ->
                    var origin by remember(app.key) { mutableStateOf(Offset.Zero) }
                    Row(Modifier.fillMaxWidth().height(u.dp(44f))
                        .combinedClickable(onClick = { s.launchFavorite(app, origin.x, origin.y) }, onLongClick = s::manageFavorites),
                        verticalAlignment = Alignment.CenterVertically) {
                        Txt("${index + 1}", 10f, pal.accent, Modifier.width(u.dp(18f)))
                        Txt(app.display, 13f, pal.fg, Modifier.weight(1f).onGloballyPositioned {
                            val at = it.positionInRoot()
                            origin = Offset(at.x / u.k, (at.y + it.size.height / 2f) / u.k)
                        }, maxLines = 1)
                        if (s.hasDot(app)) Txt("•", 12f, pal.accent)
                    }
                }
                Row(Modifier.fillMaxWidth().height(u.dp(44f)).tap { s.manageFavorites() },
                    verticalAlignment = Alignment.CenterVertically) {
                    Txt(stringResource(if (apps.isEmpty()) R.string.choose_favorites else R.string.edit_short),
                        11f, pal.muted2, Modifier.weight(1f), maxLines = 1)
                    Txt("${apps.size}/5", 10f, pal.muted)
                }
                Box(Modifier.fillMaxWidth().height(u.dp(1f)).background(pal.rim2))
            }
            Row(Modifier.fillMaxWidth().height(u.dp(44f))
                .combinedClickable(onClick = s::openSearch, onLongClick = s::toggleFavorites)
                .padding(start = u.dp(14f), end = u.dp(18f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(u.dp(10f))) {
                Canvas(Modifier.size(u.dp(14f))) {
                    val k = u.k
                    drawCircle(pal.fg, 4.25f * k, Offset(5f * k, 5f * k), style = Stroke(1.5f * k))
                    drawLine(pal.fg, Offset(8.5f * k, 8.5f * k), Offset(12.74f * k, 12.74f * k), strokeWidth = 1.5f * k)
                }
                Txt(stringResource(R.string.search), 13f, pal.fg, Modifier.weight(1f))
                val stateLabel = stringResource(if (s.favoritesExpanded) R.string.close_favorites else R.string.keep_favorites_open)
                Box(Modifier.size(u.dp(40f).let { it }).semantics { contentDescription = stateLabel }
                    .tap { s.toggleFavorites() }, contentAlignment = Alignment.Center) {
                    Txt(if (s.favoritesExpanded) "●" else "○", 13f, pal.accent)
                }
            }
        }
    }
}

