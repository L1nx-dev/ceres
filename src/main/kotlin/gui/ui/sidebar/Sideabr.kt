package ceres.gui.common

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Sidebar(state: ChatState, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxSize()) {
    SidebarHeader(onNewChat = { state.newChat(null) })
    ChatList(
      chats = state.chats,
      current = state.current,
      onSelect = state::select,
      onRename = { c, name -> state.commandOn(state.current, "/chat rename ${c.id} $name") },
      onRemove = { id -> state.commandOn(state.current, "/chat remove $id") },
      modifier = Modifier.weight(1f).fillMaxWidth(),
    )
  }
}
