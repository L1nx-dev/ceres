package ceres.gui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalStarField = staticCompositionLocalOf<StarField?> { null }

@Composable
fun GlassPanel(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(16.dp),
  blur: Dp = 20.dp,
  tint: Color = Color(0x55101015),
  content: @Composable BoxScope.() -> Unit,
) {
  val field = LocalStarField.current
  var pos by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier
      .onGloballyPositioned { pos = it.positionInWindow() }
      .clip(shape)
      .border(1.dp, Color(0x22FFFFFF), shape)
  ) {
    if (field != null && blur > 0.dp) {
      Canvas(Modifier.matchParentSize().blur(blur, BlurredEdgeTreatment.Unbounded)) {
        translate(-pos.x, -pos.y) { field.draw(this) }
      }
    }
    Box(Modifier.matchParentSize().background(tint))
    content()
  }
}
