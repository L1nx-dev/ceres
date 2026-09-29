package ceres.main

import ceres.tools.*
import kotlinx.serialization.json.*
import java.nio.file.Path

class Agent(val config: Config, xdg: Xdg) {
  private val llm = ApiV1(config)
  init { require(config.workDir.isNotBlank()) { "workDir mustn't be empty" } }
  private val ctx = ToolContext(root = Path.of(config.workDir).toRealPath())
  private val tools = listOf<Tool>(ReadFile(), ListFiles()).associateBy { it.def.name }
  private val history = mutableListOf<Msg>()

  fun prompt(input: String): String {
    history += Msg.Text("user", input)
    repeat(config.maxToolDepth) {
      val reply = llm.request(history, tools.values.map { it.def })
      val calls = reply.toolCalls
      if (calls.isNullOrEmpty()) {
        val text = reply.content ?: ""
        if (text.isNotBlank()) history += Msg.Text("assistant", text)
        return text
      }
      history += Msg.ToolCalls(calls)
      for (call in calls) history += Msg.ToolResult(call.id, runTool(call))
    }
    return "step limit reached"
  }

  private fun runTool(call: ToolCall): String {
    val tool = tools[call.name] ?: return "error: unknown tool ${call.name}"
    return runCatching {
      tool.run(Json.parseToJsonElement(call.arguments).jsonObject, ctx)
    }.getOrElse { "error: ${it.message}" }
  }
}
