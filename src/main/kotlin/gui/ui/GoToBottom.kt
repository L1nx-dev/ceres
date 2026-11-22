package ceres.gui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment

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
      blur = LocalTheme.current.blur, 
      tint = LocalTheme.current.input
    ) {
      Box(
        Modifier.size(40.dp).clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
        ) {
          coroutineScope.launch {
            listState.animateScrollToItem(
              (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0),
              scrollOffset = Int.MAX_VALUE
            )
          }
        },
        contentAlignment = Alignment.Center,
      ) { Text("↓") }
    }
  }
}
