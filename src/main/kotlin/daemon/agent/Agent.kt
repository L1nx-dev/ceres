package ceres.main

import ceres.tools.*
import ceres.system.*
import ceres.core.*
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
  var cancel: Cancel? = null

  private val store = Store(xdg.share / "sessions.db")
  private var sid: Long = 0
  private var history: MutableList<Msg> = mutableListOf()

  private var memoryIndex = ""
  fun reloadMemory() { memoryIndex = MemoryIndex.build(xdg.memory) }

  private fun open(id: Long) {
    history = store.load(id)
    if (history.isEmpty()) seed()
    reloadMemory()
  }
  //
  // private fun withIndex(h: List<Msg>): List<Msg> {
  //   val first = h.firstOrNull() as? Msg.Text ?: return h
  //   if (first.role != Role.SYSTEM) return h
  //   val idx = "# MEMORY\nFiles you can open with load_memory, or search with recall:\n$memoryIndex"
  //   return listOf(first.copy(content = first.content + "\n\n" + idx)) + h.drop(1)
  // }

  init { open(store.lastSession() ?: store.newSession()) }   // resume last session

  private fun seed() {
    startupLine = StartupLine()
    sid = startupLine.startup()
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

  fun transcript(): List<HistoryLine> = buildList {
    for (m in store.load(sid, all = true).filter { it.type == BlockType.CHAT && it.source != Source.SYSTEM }) when (m) {
      is Msg.Text -> when (m.role) {
        Role.USER -> add(HistoryLine("USER", m.content))
        Role.ASSISTANT -> add(HistoryLine("AGENT", m.content))
        else -> {}
      }
      is Msg.ToolCalls -> {
        m.content?.takeIf { it.isNotBlank() }?.let { add(HistoryLine("AGENT", it)) }
        for (c in m.calls) add(HistoryLine("TOOL", "${tools[c.name]?.icon ?: ""} ${label(c)}"))
      }
      is Msg.ToolResult -> {}
    }
  }

  private val running = java.util.concurrent.atomic.AtomicBoolean(false)
  val busy: Boolean get() = running.get()

  fun prompt(input: String, cancel: Cancel, emit: (Event) -> Unit) {
    check(running.compareAndSet(false, true)) { "run already active" }
    try { runLoop(input, cancel, emit) } finally { running.set(false) }
  }

  fun runLoop(
    input: String,
    cancel: Cancel,
    emit: (Event) -> Unit
  ) {
    ctx.cancel = cancel
    remember(input)
    record(Msg.Text(Role.USER, input))
    val defs = tools.values.map { it.def }
    repeat(config.maxToolDepth) {
      val reply = try {
        llm.stream(withIndex(history), defs, cancel) { emit(Event.Delta(it)) }
      } catch (e: Exception) {
        emit(if (cancel.cancelled) Event.Cancelled else Event.Error(e.message ?: "request failed"))
        return
      }
      if (cancel.cancelled) { emit(Event.Cancelled); return }

      val calls = reply.toolCalls
      if (calls.isNullOrEmpty()) {
        val text = reply.content.orEmpty()
        if (text.isNotBlank()) {
          record(Msg.Text(Role.ASSISTANT, text, source = Source.MODEL))
          emit(Event.Done(text))
          return
        }
        record(Msg.Text(Role.USER, "SYSTEM: cannot return an empty string", source = Source.SYSTEM))
        return@repeat
      }
      record(Msg.ToolCalls(calls, reply.content))
      for (call in calls) {
        record(
          if (cancel.cancelled) Msg.ToolResult(call.id, "error: cancelled", ok = false)
          else runTool(call, emit)
        )
      }
      if (cancel.cancelled) { emit(Event.Cancelled); return }
    }
    emit(Event.Error("step limit reached"))
  }

  private fun label(call: ToolCall): String {
    val first = runCatching {
      Json.parseToJsonElement(call.arguments).jsonObject.values.firstOrNull()?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    return if (first == null) call.name else "${call.name}: ${first.replace(Regex("\\s+"), " ").take(120)}"
  }

  private fun runTool(call: ToolCall, emit: (Event) -> Unit): Msg.ToolResult {
    val tool = tools[call.name]
      ?: return Msg.ToolResult(call.id, "error: unknown tool ${call.name}", ok = false)
    emit(Event.ToolStart(call.id, label(call), tool.icon ?: ""))
    val result = runCatching {
      tool.run(Json.parseToJsonElement(call.arguments).jsonObject, ctx)
    }.getOrElse { "error: ${it.message}" }
    val ok = !result.startsWith("error:")
    emit(Event.ToolEnd(call.id, ok, result))
    if (ok) remember(result)
    return Msg.ToolResult(call.id, result, ok = ok, source = tool.source)
  }
}
