package ceres.main

import ceres.tools.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import ceres.cli.Spinner

class Agent(val config: Config, xdg: Xdg) {
  private val llm = ApiV1(config)
  init { require(config.workDir.isNotBlank()) { "workDir mustn't be empty" } }
  private val ctx = ToolContext(root = Path.of(config.workDir).toRealPath())
  private val tools = Tools.list
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
    val spinner = Spinner(label(call)).start()
    val result = runCatching {
      tool.run(Json.parseToJsonElement(call.arguments).jsonObject, ctx)
    }.getOrElse { "error: ${it.message}" }
    spinner.stop(ok = !result.startsWith("error:"))
    return result
  }

  private fun label(call: ToolCall): String {
    val first = runCatching {
      Json.parseToJsonElement(call.arguments).jsonObject.values.firstOrNull()?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    return if (first == null) call.name else "${call.name}: ${first.take(60)}"
  }
}
