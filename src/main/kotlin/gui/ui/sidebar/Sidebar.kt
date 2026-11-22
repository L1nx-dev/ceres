package ceres.gui.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*

@Composable
fun Sidebar(
  state: ChatState,
  themes: ThemeState,
  onImport: () -> Unit,
  onSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxSize()) {
    SidebarHeader(
      onNewChat = state::startDraft,
      modifier = Modifier.padding(vertical = 4.dp)
    )

    ChatList(
      chats = state.chats,
      current = if (state.draft) -1L else state.current,
      onSelect = state::select,
      onRename = { c, name -> state.commandOn(state.current, "/chat rename ${c.id} $name") },
      onRemove = state::remove,
      modifier = Modifier.weight(1f).fillMaxWidth(),
    )
    
    HorizontalDivider()

    TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
  }
}
