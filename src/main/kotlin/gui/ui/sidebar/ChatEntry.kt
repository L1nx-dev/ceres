package ceres.gui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatEntry(
  chat: ChatInfo,
  selected: Boolean,
  onSelect: () -> Unit,
  onRename: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val theme = LocalTheme.current
  val hoverSource = remember { MutableInteractionSource() }
  val hovered by hoverSource.collectIsHoveredAsState()
  var menu by remember { mutableStateOf(false) }

  val bg = when {
    selected -> theme.user
    hovered || menu -> theme.input
    else -> Color.Transparent
  }

  Row(
    modifier
      .fillMaxWidth()
      .padding(horizontal = 6.dp, vertical = 2.dp)
      .clip(RoundedCornerShape(8.dp))
      .hoverable(hoverSource)
      .background(bg)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onSelect,
      )
      .padding(start = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      chat.name ?: "chat ${chat.id}",
      Modifier.weight(1f).padding(vertical = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
      if (hovered || menu) {
        Text("⋯", Modifier.clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
        ) { menu = true })
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
        DropdownMenuItem(
          text = { Text("Delete") },
          onClick = { menu = false; onRemove() },
        )
      }
    }
  }
}
