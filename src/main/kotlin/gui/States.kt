package ceres.gui.common

import ceres.core.*
import androidx.compose.runtime.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.io.path.*
import kotlinx.serialization.json.*

data class ChatInfo(val id: Long, val name: String?, val updated: Long = 0)

interface ChatApi {
  val chat: Long
  fun prompt(text: String, files: List<String>, onReject: (String) -> Unit)
  fun command(id: Long, text: String): String
  fun upload(file: Path): String
  fun cancel(); fun history(id: Long): List<HistoryLine>
  fun chats(): List<ChatInfo>; fun create(name: String?): Long; fun select(id: Long)
  fun connect()
}

enum class LineRole { USER, AGENT, TOOL, ERROR, THINKING }

class Line(val id: Long, val role: LineRole, text: String) {
  var text by mutableStateOf(text)
}

class ChatView {
  val lines = mutableStateListOf<Line>()
  private var next = 0L
  @Synchronized fun add(role: LineRole, text: String) { lines += Line(next++, role, text) }
  @Synchronized fun load(h: List<HistoryLine>) { lines.clear(); h.forEach { add(LineRole.valueOf(it.role), it.text) } }
  fun snapshot() = lines.map { HistoryLine(it.role.name, it.text) }
}

class Cache(private val dir: Path) {
  init { dir.createDirectories() }

  fun save(id: Long, h: List<HistoryLine>) = runCatching {
    (dir / "$id.json").writeText(buildJsonArray {
      h.forEach { addJsonObject { put("role", it.role); put("text", it.text) } }
    }.toString())
  }
  fun load(id: Long): List<HistoryLine>? = runCatching {
    Json.parseToJsonElement((dir / "$id.json").readText()).jsonArray.map {
      val o = it.jsonObject
      HistoryLine(o.getValue("role").jsonPrimitive.content, o.getValue("text").jsonPrimitive.content)
    }
  }.getOrNull()

  fun saveChats(c: List<ChatInfo>) = runCatching {
    (dir / "chats.json").writeText(buildJsonArray {
      c.forEach { addJsonObject { put("id", it.id); put("name", it.name); put("updated", it.updated) } }
    }.toString())
  }
  fun loadChats(): List<ChatInfo>? = runCatching {
    Json.parseToJsonElement((dir / "chats.json").readText()).jsonArray.map {
      val o = it.jsonObject
      ChatInfo(o.getValue("id").jsonPrimitive.long, o["name"]?.jsonPrimitive?.contentOrNull,
        o["updated"]?.jsonPrimitive?.longOrNull ?: 0)
    }
  }.getOrNull()
}

class ChatState(private val api: ChatApi, private val cache: Cache) {
  private val views = ConcurrentHashMap<Long, ChatView>()
  private fun view(id: Long) = views.computeIfAbsent(id) { ChatView() }

  var current by mutableStateOf(api.chat)
    private set
  var busyChat by mutableStateOf<Long?>(null)
    private set
  var online by mutableStateOf(false)
    private set
  var chats by mutableStateOf(listOf<ChatInfo>())
    private set

  val busy get() = busyChat != null
  val lines get() = view(current).lines
  val pending = mutableStateListOf<String>()

  fun connect() = thread(isDaemon = true, name = "connect") {
    cache.loadChats()?.let { chats = it }
    cache.load(current)?.let { view(current).load(it) }
    while (!online) {
      runCatching {
        api.connect()
        current = api.chat
        val h = api.history(current)
        if (busyChat != current) view(current).load(h)
        cache.save(current, h)
        chats = api.chats().also { cache.saveChats(it) }
        online = true
      }
      if (!online) Thread.sleep(3000)
    }
  }

  fun refreshChats() = thread(isDaemon = true) {
    runCatching { chats = api.chats().also { cache.saveChats(it) } }
  }

  fun select(id: Long) {
    api.select(id); current = id
    thread(isDaemon = true) {
      runCatching { api.history(id) }
        .onSuccess { h ->
          online = true
          val v = view(id)
          if (busyChat != id || v.lines.isEmpty()) v.load(h)
          cache.save(id, h)
        }
        .onFailure {
          online = false
          val v = view(id)
          if (v.lines.isEmpty()) cache.load(id)?.let(v::load)
        }
    }
  }

  fun newChat(name: String?) = thread(isDaemon = true) {
    runCatching { api.create(name) }
      .onSuccess { select(it); refreshChats() }
      .onFailure { view(current).add(LineRole.ERROR, "error: ${it.message}") }
  }

  fun attach(file: Path) { pending += api.upload(file) }

  fun send(text: String) {
    if (busy) return
    val id = current
    val files = pending.toList(); pending.clear()
    view(id).add(LineRole.USER, text); busyChat = id
    api.prompt(text, files) { err -> view(id).add(LineRole.ERROR, err); busyChat = null }
  }

  fun command(text: String) {
    if (busy) return
    val id = current
    view(id).add(LineRole.USER, text)
    thread(isDaemon = true) {
      val out = runCatching { api.command(id, text) }.getOrElse { "error: ${it.message}" }
      if (api.chat != id) select(api.chat)
      view(api.chat).add(LineRole.AGENT, out)
    }
  }

  var notice by mutableStateOf<String?>(null)
    private set

  fun clearNotice() { notice = null }

  fun commandOn(id: Long, text: String) {
    if (busy) return
    thread(isDaemon = true) {
      notice = runCatching { api.command(id, text) }.getOrElse { "error: ${it.message}" }
      val list = runCatching { api.chats() }.getOrNull() ?: return@thread
      chats = list; cache.saveChats(list)
      if (list.none { it.id == current }) list.firstOrNull()?.let { select(it.id) }
    }
  }

  fun cancel() = api.cancel()

  fun onEvent(chat: Long, e: Event) {
    val v = view(chat)
    when (e) {
      is Event.Delta -> {
        busyChat = chat
        val last = v.lines.lastOrNull()
        if (last?.role == LineRole.AGENT) last.text += e.text else v.add(LineRole.AGENT, e.text)
      }
      is Event.ToolStart -> { busyChat = chat; v.add(LineRole.TOOL, "${e.icon} ${e.label}") }
      is Event.ToolEnd -> {}
      is Event.Done, is Event.Cancelled -> {
        busyChat = null
        cache.save(chat, v.snapshot()); refreshChats()
      }
      is Event.Error -> { v.add(LineRole.ERROR, e.message); busyChat = null }
    }
  }
}
