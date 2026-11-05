package ceres.gui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale

// import androidx.compose.material3.ColorScheme.*
// import androidx.compose.ui.graphics.Color
// import androidx.compose.foundation.background

@Composable
fun MenuButton(
  onClick: () -> Unit,
  menuSize: Int,
  state: Boolean
) {
  Box(
    Modifier.size(MenuSize).scale(0.9f).clickable( // for whatever reason changing MenuSize doesnt do shit, i fucking hate compose
      interactionSource = remember { MutableInteractionSource() },
      indication = null,
      onClick = onClick,
    )
    // .background(Color.Red)
    ,
    contentAlignment = Alignment.Center,
  ) {
    if (state) {
      Text("", Modifier.offset(x = (-4).dp, y = (0).dp).scale(1.1f))
    } else {
      Text("", Modifier.offset(x = (-4).dp, y = (0).dp).scale(1.1f))
    }
  }
}
