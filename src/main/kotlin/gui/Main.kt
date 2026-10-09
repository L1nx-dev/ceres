package ceres.gui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ceres.core.*
import ceres.system.*

class Line(val role: String, text: String) { var text by mutableStateOf(text) }

class ChatState(private val api: Api) {
  val lines = mutableStateListOf<Line>()
  var busy by mutableStateOf(false)

  fun send(text: String) {
    lines += Line("you", text)
    busy = true
    api.prompt(text)
  }

  fun cancel() = api.cancel()

  fun onEvent(e: Event) {
    when (e) {
      is Event.Delta -> {
        val last = lines.lastOrNull()
        if (last?.role == "ceres") last.text += e.text else lines += Line("ceres", e.text)
      }
      is Event.ToolStart -> lines += Line("tool", "${e.icon} ${e.label}")
      is Event.ToolEnd -> {}
      is Event.Done, is Event.Cancelled -> busy = false
      is Event.Error -> { lines += Line("error", e.message); busy = false }
    }
  }
}

@Composable
fun App(state: ChatState) = MaterialTheme {
  var input by remember { mutableStateOf("") }
  Column(Modifier.fillMaxSize().padding(12.dp)) {
    LazyColumn(Modifier.weight(1f)) {
      items(state.lines) { Text("${it.role}: ${it.text}", Modifier.padding(vertical = 4.dp)) }
    }
    Row {
      OutlinedTextField(input, { input = it }, Modifier.weight(1f))
      Button(onClick = {
        if (state.busy) state.cancel()
        else if (input.isNotBlank()) { state.send(input); input = "" }
      }) { Text(if (state.busy) "Stop" else "Send") }
    }
  }
}

fun main() {
  val config = Config(Xdg(seed = false))
  val api = Api(config)
  val state = ChatState(api)
  api.listen(state::onEvent)
  application { Window(onCloseRequest = ::exitApplication, title = "ceres") { App(state) } }
}
