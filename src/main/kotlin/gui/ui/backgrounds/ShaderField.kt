package ceres.gui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaCanvas

class ShaderField(sksl: String) {
  val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(sksl))

  val time = mutableFloatStateOf(0f)
  var size by mutableStateOf(Size.Zero)
  fun draw(scope: DrawScope) {
    if (size.width <= 0f || size.height <= 0f) return
    builder.uniform("resolution", size.width, size.height)
    builder.uniform("time", time.floatValue)
    val shader = builder.makeShader()
    val paint = Paint()
    try {
      paint.shader = shader
      scope.drawContext.canvas.skiaCanvas
        .drawRect(Rect.makeWH(size.width, size.height), paint)
    } finally {
      paint.close()
      shader.close()
    }
  }
}

@Composable
fun ShaderBackground(
    field: ShaderField,
    modifier: Modifier = Modifier
) {
  LaunchedEffect(field) {
    val start = withFrameNanos { it }

    while (true) {
      withFrameNanos {
        field.time.floatValue = (it - start) / 1e9f
      }
    }
  }

  Canvas(
    modifier
      .background(Color.Black)
      .onSizeChanged { field.size = it.toSize() }
  ) {
    if (size.width <= 0f || size.height <= 0f) return@Canvas

    field.builder.uniform("resolution", size.width, size.height)
    field.builder.uniform("time", field.time.floatValue)

    val shader = field.builder.makeShader()
    val paint = Paint()

    try {
      paint.shader = shader

      drawContext.canvas.skiaCanvas.drawRect(
        Rect.makeWH(size.width, size.height),
        paint
      )
    } finally {
      paint.close()
      shader.close()
    }
  }
}
