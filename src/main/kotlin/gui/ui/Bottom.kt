package ceres.gui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun GoToBottom(
  modifier: Modifier = Modifier,
  showScrollToBottomButton: Boolean,
  state: ChatState,
  listState: LazyListState
) {
  val coroutineScope = rememberCoroutineScope()

  AnimatedVisibility(
    visible = showScrollToBottomButton,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier
  ) {
    GlassPanel(
      shape = RoundedCornerShape(100.dp),
      blur = 12.dp, 
      tint = Color(0xCC15151A)
    ) {
      SmallFloatingActionButton(
        shape = RoundedCornerShape(100.dp),
        onClick = {
          coroutineScope.launch {
            if (state.lines.isNotEmpty()) {
              listState.animateScrollToItem(state.lines.lastIndex)
            }
          }
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        elevation = FloatingActionButtonDefaults.elevation(0.dp),
      ) {
        Text("↓")
      }
    }
  }
}
