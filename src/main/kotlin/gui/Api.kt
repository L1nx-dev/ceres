package ceres.gui.desktop

import ceres.core.*
import ceres.gui.common.*
import ceres.system.*
import ceres.gui.common.ChatApi
import kotlinx.serialization.json.*
import kotlin.io.path.*

class Api(private val config: Config, xdg: Xdg): ChatApi {
  private val http = java.net.http.HttpClient.newHttpClient()
  private val base = "http://${config.host}:${config.port}"
  @Volatile private var lastId = -1L
  private val chatFile = xdg.share / "gui-chat"
  @Volatile var chat = -1L
    private set

  private fun getString(path: String): String {
    val res = http.send(req(path).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString())
    if (res.statusCode() !in 200..299) error("GET $path -> ${res.statusCode()}: ${res.body()}")
    return res.body()
  }

  override fun chats() = Json.parseToJsonElement(getString("/chats")).jsonArray.map {
    val o = it.jsonObject
    ChatInfo(o.getValue("id").jsonPrimitive.long, o["name"]?.jsonPrimitive?.contentOrNull)
  }

  override fun create(name: String?): Long {
    val n = java.net.URLEncoder.encode(name ?: "", Charsets.UTF_8)
    return http.send(req("/chat?name=$n").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
      java.net.http.HttpResponse.BodyHandlers.ofString()).body().trim().toLong()
  }

  override fun select(id: Long) {
    chat = id
    chatFile.parent?.createDirectories()
    chatFile.writeText(id.toString())
  }

  fun initChat() {
    val all = chats()
    val saved = runCatching { chatFile.readText().trim().toLong() }.getOrNull()
    select(saved?.takeIf { s -> all.any { it.id == s } } ?: all.firstOrNull()?.id ?: create(null))
  }

  private fun req(path: String) =
    java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path))
      .header("Authorization", "Bearer ${config.token}")

  override fun prompt(text: String) { http.sendAsync(req("/prompt?chat=$chat")
    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(text)).build(),
    java.net.http.HttpResponse.BodyHandlers.discarding()) }

  override fun history(): List<HistoryLine> = Json.parseToJsonElement(
    http.send(req("/history?chat=$chat").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body()
  ).jsonArray.map {
    val o = it.jsonObject
    HistoryLine(o.getValue("role").jsonPrimitive.content, o.getValue("text").jsonPrimitive.content)
  }

  override fun cancel() { http.sendAsync(req("/cancel")
    .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
    java.net.http.HttpResponse.BodyHandlers.discarding()) }

  fun listen(onEvent: (Event) -> Unit) = Thread {
    while (true) {
      try {
        http.send(req("/events").header("Last-Event-ID", lastId.toString()).GET().build(),
          java.net.http.HttpResponse.BodyHandlers.ofLines()).body().forEach { line ->
          when {
            line.startsWith("id:") -> lastId = line.substring(3).trim().toLong()
            line.startsWith("data:") -> onEvent(decode(line.substring(5).trim()))
          }
        }
      } catch (_: Exception) {}
      Thread.sleep(1000)
    }
  }.apply { isDaemon = true; start() }
}
