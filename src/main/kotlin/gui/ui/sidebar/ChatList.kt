package ceres.gui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatList(state: ChatState, modifier: Modifier = Modifier) {
  var renaming by remember { mutableStateOf<ChatInfo?>(null) }
  var draft by remember { mutableStateOf("") }

  LazyColumn(modifier) {
    items(state.chats.sortedByDescending { it.updated }, key = { it.id }) { c ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          (if (c.id == state.current) "● " else "") + (c.name ?: "chat ${c.id}"),
          Modifier.weight(1f).clickable { state.select(c.id) }.padding(8.dp),
        )
        TextButton(onClick = { draft = c.name ?: ""; renaming = c }) { Text("✎") }
        if (c.id != state.current)
          TextButton(onClick = { state.commandOn(state.current, "/chat remove ${c.id}") }) { Text("✕") }
      }
    }
  }

  renaming?.let { c ->
    AlertDialog(
      onDismissRequest = { renaming = null },
      title = { Text("Rename") },
      text = { OutlinedTextField(draft, { draft = it }, singleLine = true) },
      confirmButton = {
        TextButton(onClick = {
          state.commandOn(state.current, "/chat rename ${c.id} ${draft.trim()}")
          renaming = null
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
    )
  }
}
