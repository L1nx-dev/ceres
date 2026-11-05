package ceres.gui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

@Composable
fun SidebarHeader(onNewChat: () -> Unit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().height(HeaderHeight).padding(start = MenuX + MenuSize + 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Image(painterResource("logo.svg"), contentDescription = null, Modifier.size(24.dp))
    }
    Column(Modifier.padding(horizontal = 8.dp)) {
      TextButton(onClick = onNewChat, modifier = Modifier.fillMaxWidth()) { Text("New chat") }
      HorizontalDivider()
    }
  }
}
