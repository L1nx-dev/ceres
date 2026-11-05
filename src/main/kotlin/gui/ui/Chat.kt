package ceres.gui.common

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun Chat(state: ChatState, modifier: Modifier = Modifier) {
  val listState = rememberLazyListState()
  var isInputExpanded by remember { mutableStateOf(false) }

  val showScrollToBottomButton by remember {
    derivedStateOf { listState.canScrollForward }
  }

  val needsFade = showScrollToBottomButton || isInputExpanded

  Box(modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxHeight()
        .widthIn(max = 900.dp)
        .fillMaxWidth()
        .let { mod ->
          if (needsFade) {
            mod
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
          } else mod
        }
    ) {
      key(state.current) {
        MessageList(
          state.lines,
          listState = listState,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
    
    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .widthIn(max = 900.dp)
        .fillMaxWidth(),
      contentAlignment = Alignment.BottomCenter
    ) {
      GoToBottom(
        modifier = Modifier.padding(bottom = 80.dp, end = 16.dp),
        showScrollToBottomButton = showScrollToBottomButton,
        state = state,
        listState = listState
      )
    }

    state.notice?.let { n ->
      Text(
        n,
        Modifier.align(Alignment.TopCenter).clickable { state.clearNotice() }.padding(8.dp),
      )
    }

    InputBar(
      busy = state.busy, 
      onSend = { 
        state.send(it)
        isInputExpanded = false
      }, 
      onStop = state::cancel,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(16.dp)
        .widthIn(max = 900.dp)
        .fillMaxWidth(),
    )
  }
}
