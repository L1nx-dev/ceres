package ceres.main

import ceres.tools.*
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.div
import ceres.cli.Spinner
import kotlin.io.path.*

class Agent(val config: Config, private val xdg: Xdg) {
  private val llm = ApiV1(config)
  init { require(config.workDir.isNotBlank()) { "workDir mustn't be empty" } }

  private val seen = mutableSetOf<String>()
  private val urlRe = Regex("""https?://[^\s<>"')\]]+""")
  private fun remember(s: String) {
    urlRe.findAll(s).forEach { seen += it.value.trimEnd('.', ',', ';') }
  }

  private val ctx = ToolContext(
    root = Path.of(config.workDir).toRealPath(),
    config = config,
    seen = seen,
    ask = { msgs ->
      llm.request(
        msgs,
        emptyList(),
        model = config.summaryModel.ifBlank { config.model },
        think = false
      ).content.orEmpty()
    },
  )

  private val tools = Tools(xdg).list

  private val store = Store(xdg.share / "sessions.db")
  private var sid: Long = 0
  private var history: MutableList<Msg> = mutableListOf()

  init { open(store.lastSession() ?: store.newSession()) }   // resume last session

  private fun open(id: Long) {
    sid = id
    history = store.load(id)
    if (history.isEmpty()) seed()
  }

  private fun seed() {
    val text = listOf("SOUL.md", "USER.md")
      .map { xdg.config / it }
      .filter { it.exists() }
      .map { it.readText().trim() }
      .filter { it.isNotEmpty() }
      .joinToString("\n\n")

    val bootFile = xdg.config / "BOOT.script"
    val shellOut = if (bootFile.exists()) {
        val cmds = bootFile.readLines()
          .map { it.trim() }
          .filter { it.isNotEmpty() && !it.startsWith("#") }
        if (cmds.isEmpty()) ""
        else cmds.joinToString("\n\n") { cmd -> "$ $cmd\n${exec(cmd)}" }
          .let { "#SYSTEM INFO\n\n$it" }
    } else ""

    val full = listOf(text, shellOut).filter { it.isNotEmpty() }.joinToString("\n\n")

    if (full.isNotEmpty())
      record(Msg.Text(Role.SYSTEM, full, type = BlockType.PINNED, source = Source.SYSTEM))
  }

  private fun exec(cmd: String): String {
    val tool = tools["shell"] ?: return "error: shell tool not found"
    return runCatching {
      tool.run(buildJsonObject { put("command", cmd) }, ctx)
    }.getOrElse { "error: ${it.message}" }
  }

  private fun record(msg: Msg) {
    store.append(sid, msg)
    history += msg
  }

  val currentId get() = sid
  fun chats() = store.sessions(sid)
  fun newChat(name: String?): Long { open(store.newSession(name)); return sid }
  fun resolveChat(ref: String) = store.resolve(ref)
  fun chatByName(name: String) = store.byName(name)
  fun switchChat(id: Long) = open(id)
  fun renameChat(id: Long, name: String) = store.rename(id, name)
  fun removeChat(id: Long) = store.delete(id)

  fun prompt(input: String): String {
    remember(input)
    record(Msg.Text(Role.USER, input))
    repeat(config.maxToolDepth) {
      val reply = llm.request(history, tools.values.map { it.def })
      val calls = reply.toolCalls
      if (calls.isNullOrEmpty()) {
        val text = reply.content.orEmpty()
        if (text.isNotBlank()) {
          record(Msg.Text(Role.ASSISTANT, text, source = Source.MODEL))
          return text
        }
        record(Msg.Text(Role.USER, "SYSTEM: cannot return an empty string", source = Source.SYSTEM))
        return@repeat
      }
      record(Msg.ToolCalls(calls, reply.content))
      for (call in calls) record(runTool(call))
    }
    return "step limit reached"
  }

  private fun runTool(call: ToolCall): Msg.ToolResult {
    val tool = tools[call.name]
      ?: return Msg.ToolResult(call.id, "error: unknown tool ${call.name}", ok = false)
    val spinner = Spinner(label(call)).start()
    val result = runCatching {
      tool.run(Json.parseToJsonElement(call.arguments).jsonObject, ctx)
    }.getOrElse { "error: ${it.message}" }
    val ok = !result.startsWith("error:")
    spinner.stop(ok = ok, icon = tool.icon)
    if (ok) remember(result)
    return Msg.ToolResult(call.id, result, ok = ok, source = tool.source)
  }

  private fun label(call: ToolCall): String {
    val first = runCatching {
        Json.parseToJsonElement(call.arguments).jsonObject.values.firstOrNull()?.jsonPrimitive?.contentOrNull
      }.getOrNull()
    return if (first == null) call.name else "${call.name}: ${first.replace(Regex("\\s+"), " ").take(120)}"
  }
}
