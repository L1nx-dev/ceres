package ceres.gui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val SidebarWidth = 300.dp
private val SidebarInset = 1.dp   // sidebar content stays clear of the border
private val Closed = 7.dp          // hole's left edge when the sidebar is closed
private val FrameTop = 7.dp
private val FrameSide = 7.dp
private val FrameRadius = 20.dp
private val BorderWidth = 2.dp

internal val MenuX = 8.dp
internal val MenuSize = 40.dp
internal val HeaderHeight = 48.dp

@Composable
fun Frame(
  open: Boolean, frameColor: Color, borderColor: Color,
  menu: @Composable () -> Unit,
  sidebar: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

  val left by animateDpAsState(
    if (open) SidebarWidth else Closed,
    tween(durationMillis = 350, easing = ease),
  )

  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().padding(start = FrameSide, top = FrameTop, end = FrameSide, bottom = FrameSide)) {
      content()
    }

    Canvas(Modifier.fillMaxSize()) {
      val hole = Path().apply {
        addRoundRect(RoundRect(
          left.toPx(), FrameTop.toPx(), size.width - FrameSide.toPx(), size.height - FrameSide.toPx(),
          CornerRadius(FrameRadius.toPx())))
      }
      val full = Path().apply { addRect(Rect(Offset.Zero, size)) }
      drawPath(Path.combine(PathOperation.Difference, full, hole), frameColor)
      drawPath(hole, borderColor, style = Stroke(BorderWidth.toPx()))
    }

    Box(Modifier.width(left).fillMaxHeight().padding(top = FrameTop, bottom = FrameSide).clipToBounds()) {
      Box(Modifier.width(SidebarWidth - SidebarInset)) { sidebar() }
    }

    Box(Modifier.padding(start = MenuX, top = FrameTop + (HeaderHeight - MenuSize) / 2)) { menu() }
  }
}
