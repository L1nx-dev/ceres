package ceres.gui.common

import ceres.core.*
import androidx.compose.runtime.*
import ceres.core.Event

data class ChatInfo(val id: Long, val name: String?)

interface ChatApi {
  fun prompt(text: String); fun cancel(); fun history(): List<HistoryLine>
  fun chats(): List<ChatInfo>; fun create(name: String?): Long; fun select(id: Long)
}

enum class LineRole { USER, AGENT, TOOL, ERROR, THINKING }

class Line(val id: Long, val role: LineRole, text: String) {
  var text by mutableStateOf(text)
}

class ChatState(private val api: ChatApi) {
  val lines = mutableStateListOf<Line>()
  var busy by mutableStateOf(false)
    private set
  private var nextId = 0L

  private fun add(role: LineRole, text: String) { lines += Line(nextId++, role, text) }

  fun select(id: Long) { if (busy) return; api.select(id); load(api.history()) }
  fun newChat(name: String?) { if (busy) return; select(api.create(name)) }
  fun send(text: String) { add(LineRole.USER, text); busy = true; api.prompt(text) }
  fun cancel() = api.cancel()

  fun onEvent(e: Event) {
    when (e) {
      is Event.Delta -> {
        val last = lines.lastOrNull()
        if (last?.role == LineRole.AGENT) last.text += e.text else add(LineRole.AGENT, e.text)
      }
      is Event.ToolStart -> add(LineRole.TOOL, "${e.icon} ${e.label}")
      is Event.ToolEnd -> {}
      is Event.Done, is Event.Cancelled -> busy = false
      is Event.Error -> { add(LineRole.ERROR, e.message); busy = false }
    }
  }
  fun load(h: List<HistoryLine>) {
    lines.clear()
    h.forEach { add(LineRole.valueOf(it.role), it.text) }
  }
}
