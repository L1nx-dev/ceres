package ceres.gui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize

class StarField {
  private val builder = RuntimeShaderBuilder(RuntimeEffect.makeForShader(backgroundStars))
  val time = mutableFloatStateOf(0f)
  var size by mutableStateOf(Size.Zero)

  fun brush(): Brush {
    builder.uniform("resolution", size.width, size.height)
    builder.uniform("time", time.floatValue)
    return ShaderBrush(builder.makeShader())
  }
}

@Composable
fun StarBackground(field: StarField, modifier: Modifier = Modifier) {
  LaunchedEffect(field) {
    val t0 = withFrameNanos { it }
    while (true) withFrameNanos { field.time.floatValue = (it - t0) / 1e9f }
  }
  Canvas(modifier.background(Color.Black).onSizeChanged { field.size = it.toSize() }) {
    drawRect(field.brush())
  }
}

private const val backgroundStars = """
    const float SPEED  = 0.16;
    const int   LAYERS = 20;
    const float DEPTH  = 45.0;
    const float THICK  = 0.3;
    const float SIZE   = 16.0;
    const float GAIN   = 5.5;

    uniform float2 resolution;
    uniform float  time;

    float hash12(float2 p) {
        float3 p3 = fract(p.xyx * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    half4 main(float2 fragCoord) {
        // camera ray (z = 1 gives the perspective warp)
        float2 xy = 2.0 * (fragCoord - 0.5 * resolution) / resolution.x;
        float3 ray = float3(xy, 1.0);

        float offset = time * SPEED;

        // DDA step: advance one grid cell per iteration
        float3 stp = ray / max(abs(ray.x), abs(ray.y));
        float3 pos = 2.0 * stp + 0.5;

        float3 col = float3(0.0);
        for (int i = 0; i < LAYERS; i++) {
            float z = hash12(floor(pos.xy));
            z = fract(z - offset);

            float d = DEPTH * z - pos.z;
            float c = max(0.0, 1.0 - abs(d) / THICK);

            float w = max(0.0, 1.0 - SIZE * length(fract(pos.xy) - 0.5));
            col += (1.0 - z) * c * w * w;

            pos += stp;
        }

        col *= GAIN;
        return half4(pow(col, float3(1.0 / 2.2)), 1.0);
    }
"""

@Composable
fun StarBackground(modifier: Modifier = Modifier) {
  val builder = remember { RuntimeShaderBuilder(RuntimeEffect.makeForShader(backgroundStars)) }
  var time by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    val t0 = withFrameNanos { it }
    while (true) withFrameNanos { time = (it - t0) / 1e9f }
  }

  Canvas(modifier.background(Color.Black)) {
    builder.uniform("resolution", size.width, size.height)
    builder.uniform("time", time)
    drawRect(ShaderBrush(builder.makeShader()))
  }
}
