package ceres.gui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Toasts(state: ChatState, modifier: Modifier = Modifier) {
  Column(
    modifier.widthIn(max = 320.dp).animateContentSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    horizontalAlignment = Alignment.End,
  ) {
    for (n in state.notices) key(n.id) { Toast(n) { state.dismiss(n.id) } }
  }
}

@Composable
private fun Toast(n: Notice, onDone: () -> Unit) {
  var shown by remember { mutableStateOf(false) }
  var closing by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) { shown = true; delay(3000); closing = true }
  LaunchedEffect(closing) { if (closing) { delay(250); onDone() } }

  AnimatedVisibility(shown && !closing, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
    GlassPanel(shape = RoundedCornerShape(12.dp), blur = LocalTheme.current.blur, tint = LocalTheme.current.input) {
      Text(
        n.text,
        Modifier
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { closing = true }
          .padding(horizontal = 14.dp, vertical = 8.dp),
        fontSize = 13.sp,
      )
    }
  }
}
