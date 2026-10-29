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
    Window(onCloseRequest = ::exitApplication, title = "ceres") {
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
              Chat(state)
            }
          }
        }
      }
    }
  }
}
