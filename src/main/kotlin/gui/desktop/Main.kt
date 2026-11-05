package ceres.gui.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import java.nio.file.Path
import kotlin.io.path.div
import ceres.system.*
import ceres.gui.common.*
import androidx.compose.runtime.*

fun main() {
  val xdg = Xdg(seed = false)
  val config = Config(xdg)
  val api = Api(config, xdg)
  val state = ChatState(api, Cache(xdg.share / "gui" / "cache"))
  api.listen(state::onEvent)
  state.connect()
  application {
    Window(onCloseRequest = ::exitApplication, title = "Ceres") {
      val field = remember { StarField() }
      MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(
          Modifier.fillMaxSize(),
          color = Color.Transparent,
          contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
          CompositionLocalProvider(LocalStarField provides field) {
            Box(Modifier.fillMaxSize()) {
              StarBackground(field, Modifier.fillMaxSize())
              Box(Modifier.fillMaxSize()) {
                StarBackground(field, Modifier.fillMaxSize())
                var open by remember { mutableStateOf(false) }
                Frame(open, Color(0xFF0E0E12), Color(0xFF3A3A4A),
                  sidebar = { ChatList(state, Modifier.fillMaxSize()) },
                  content = { Chat(state, Modifier.fillMaxSize()) }) 
                // toggle in the top band:
                TextButton(onClick = { open = !open }, Modifier.height(32.dp)) { Text("☰") }
              }
            }
          }
        }
      }
    }
  }
}
