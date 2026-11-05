package ceres.gui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
      (if (selected) "● " else "") + (chat.name ?: "chat ${chat.id}"),
      Modifier.weight(1f).clickable(onClick = onSelect).padding(8.dp),
    )
    TextButton(onClick = onRename) { Text("✎") }
    if (!selected) TextButton(onClick = onRemove) { Text("✕") } // Commands.remove refuses the current chat
  }
}
