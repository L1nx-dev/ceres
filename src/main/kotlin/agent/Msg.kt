package ceres.main

sealed class Msg {
  data class Text(val role: String, val content: String) : Msg()      // user/system/assistant prose
  data class ToolCalls(val calls: List<ToolCall>) : Msg()             // assistant asking to run tools
  data class ToolResult(val callId: String, val content: String) : Msg() // role "tool" in JSON
}
