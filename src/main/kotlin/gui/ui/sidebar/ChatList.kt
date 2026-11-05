package ceres.gui.common

import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun ChatList(
  chats: List<ChatInfo>,
  current: Long,
  onSelect: (Long) -> Unit,
  onRename: (ChatInfo, String) -> Unit,
  onRemove: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var renaming by remember { mutableStateOf<ChatInfo?>(null) }
  val sorted = remember(chats) { chats.sortedByDescending { it.updated } }

  LazyColumn(modifier) {
    items(sorted, key = { it.id }) { c ->
      ChatEntry(
        chat = c,
        selected = c.id == current,
        onSelect = { onSelect(c.id) },
        onRename = { renaming = c },
        onRemove = { onRemove(c.id) },
      )
    }
  }

  renaming?.let { c ->
    RenameDialog(
      initial = c.name ?: "",
      onConfirm = { onRename(c, it.trim()); renaming = null },
      onDismiss = { renaming = null },
    )
  }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  var draft by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rename") },
    text = { OutlinedTextField(draft, { draft = it }, singleLine = true) },
    confirmButton = { TextButton(onClick = { onConfirm(draft) }) { Text("OK") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
