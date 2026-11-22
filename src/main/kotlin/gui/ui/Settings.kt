package ceres.gui.common

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class SettingsPage(val id: String, val title: String, val content: @Composable ColumnScope.() -> Unit)

@Composable
fun SettingsOverlay(visible: Boolean, pages: List<SettingsPage>, sheet: Boolean, onClose: () -> Unit) {
  var selected by remember { mutableStateOf<String?>(null) }
  val theme = LocalTheme.current
  val none = remember { MutableInteractionSource() }
  val scope = rememberCoroutineScope()
  val offset = remember { Animatable(0f) } // px the sheet is dragged down; 0 = fully open

  LaunchedEffect(visible) { if (visible) offset.snapTo(0f) }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val current = selected ?: if (sheet) null else pages.firstOrNull()?.id
    val page = pages.firstOrNull { it.id == current }
    val sheetPx = with(LocalDensity.current) { (maxHeight * 0.9f).toPx() }

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
      val fade = if (sheet) (1f - offset.value / sheetPx).coerceIn(0f, 1f) else 1f
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * fade))
        .clickable(interactionSource = none, indication = null, onClick = onClose))
    }

    if (!sheet) {
      val panelColor = (theme.background as? Background.Plain)?.color ?: theme.frame // shader themes have no single color
      val shape = RoundedCornerShape(16.dp)
      AnimatedVisibility(visible, Modifier.align(Alignment.Center),
        enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
        Box(
          Modifier
            .widthIn(min = 600.dp)
            .fillMaxWidth(0.60f)
            .heightIn(min = 400.dp)
            .fillMaxHeight(0.65f)
            .clip(shape)
            .background(panelColor)
            .border(1.dp, theme.border, shape)
            .clickable(interactionSource = none, indication = null) {}, // swallow clicks
        ) {
          Row(Modifier.fillMaxSize()) {
            PageList(pages, current, Modifier.width(200.dp).fillMaxHeight().padding(8.dp)) { selected = it }
            VerticalDivider()
            if (page != null) PageBody(page, Modifier.weight(1f).fillMaxHeight())
          }
        }
      }
    } else {
      AnimatedVisibility(visible, Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Column(
          Modifier.fillMaxWidth().fillMaxHeight(0.9f)
            .offset { IntOffset(0, offset.value.roundToInt()) }
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(theme.frame)
            .clickable(interactionSource = none, indication = null) {},
        ) {
          Column(
            Modifier.fillMaxWidth().draggable(
              orientation = Orientation.Vertical,
              state = rememberDraggableState { d ->
                scope.launch { offset.snapTo((offset.value + d).coerceAtLeast(0f)) }
              },
              onDragStopped = { velocity ->
                if (offset.value > sheetPx * 0.25f || velocity > 1500f) {
                  offset.animateTo(sheetPx, tween(200))
                  onClose()
                } else offset.animateTo(0f, spring())
              },
            ),
          ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 6.dp)
              .size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp))
              .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
              if (page != null) TextButton({ selected = null }) { Text("←") }
              Text(page?.title ?: "Settings", Modifier.weight(1f).padding(start = 8.dp))
              TextButton(onClose) { Text("✕") }
            }
            HorizontalDivider()
          }
          if (page == null) PageList(pages, null, Modifier.fillMaxSize().padding(8.dp)) { selected = it }
          else PageBody(page, Modifier.fillMaxSize(), showTitle = false)
        }
      }
    }
  }
}

@Composable
private fun PageList(pages: List<SettingsPage>, selected: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
  val theme = LocalTheme.current
  Column(modifier.verticalScroll(rememberScrollState())) {
    pages.forEach { p ->
      Text(
        p.title,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
          .background(if (p.id == selected) theme.user else Color.Transparent)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(p.id) }
          .padding(horizontal = 12.dp, vertical = 10.dp),
      )
    }
  }
}

@Composable
private fun PageBody(page: SettingsPage, modifier: Modifier = Modifier, showTitle: Boolean = true) {
  Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
    if (showTitle) { Text(page.title, fontSize = 18.sp); Spacer(Modifier.height(8.dp)) }
    page.content(this)
  }
}

// ==== ROWS ====

@Composable
fun SettingRow(title: String, desc: String? = null, trailing: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(title)
      if (desc != null) Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
    Spacer(Modifier.width(12.dp))
    trailing()
  }
  HorizontalDivider()
}

@Composable
fun SettingSwitch(title: String, desc: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) =
  SettingRow(title, desc) { Switch(checked, onChange) }
