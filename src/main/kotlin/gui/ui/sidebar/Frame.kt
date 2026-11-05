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

@Composable
fun Frame(
  open: Boolean,
  frameColor: Color,
  borderColor: Color,
  sidebar: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  val left by animateDpAsState(
    if (open) 300.dp else 7.dp,
    spring(dampingRatio = if (open) 1f else 0.4f, stiffness = if (open) 300f else 100f),
  )
  val top = 32.dp; val side = 7.dp; val radius = 20.dp

  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().padding(start = side, top = top, end = side, bottom = side)) { content() }

    Canvas(Modifier.fillMaxSize()) {
      val hole = Path().apply {
        addRoundRect(RoundRect(
          left.toPx(), top.toPx(), size.width - side.toPx(), size.height - side.toPx(),
          CornerRadius(radius.toPx())))
      }
      val full = Path().apply { addRect(Rect(Offset.Zero, size)) }
      drawPath(Path.combine(PathOperation.Difference, full, hole), frameColor)
      drawPath(hole, borderColor, style = Stroke(2.dp.toPx()))
    }

    Box(Modifier.width(left).fillMaxHeight().padding(top = top, bottom = side).clipToBounds()) {
      Box(Modifier.width(300.dp - 12.dp)) { sidebar() }
    }
  }
}
