package ceres.gui.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun Chat(state: ChatState) {
  Box(Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxHeight()
        .widthIn(max = 900.dp)
        .fillMaxWidth()
        .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          drawRect(
            brush = Brush.verticalGradient(
              0.0f to Color.White,
              0.85f to Color.White,
              0.92f to Color.Transparent,
              1.0f to Color.Transparent,
              startY = 0f,
              endY = size.height
            ),
            blendMode = BlendMode.DstIn
          )
        }
    ) {
      MessageList(
        state.lines,
        modifier = Modifier.fillMaxSize()
      )
    }

    // 2. Input bar floating on top
    InputBar(
      busy = state.busy, 
      onSend = state::send, 
      onStop = state::cancel,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(16.dp)
        .widthIn(max = 900.dp)
        .fillMaxWidth(),
    )
  }
}
