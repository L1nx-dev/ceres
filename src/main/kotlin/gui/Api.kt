package ceres.gui.desktop

import ceres.core.*
import ceres.gui.common.*
import ceres.system.*
import kotlinx.serialization.json.*
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.io.path.*

class Api(private val config: Config, xdg: Xdg) : ChatApi {
  private val http = java.net.http.HttpClient.newHttpClient()
  private val base = "http://${config.host}:${config.port}"
  @Volatile private var lastId = -1L
  private val chatFile = xdg.share / "gui" / "chat"

  private fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8)

  private fun req(path: String) =
    HttpRequest.newBuilder(java.net.URI.create(base + path))
      .header("Authorization", "Bearer ${config.token}")

  private fun getString(path: String): String {
    val res = http.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString())
    if (res.statusCode() !in 200..299) error("GET $path -> ${res.statusCode()}: ${res.body()}")
    return res.body()
  }

  fun ensureDaemon() {
    fun ping() = try {
      http.send(req("/status").GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200
    } catch (_: Exception) { false }
    if (ping()) return
    val p = ProcessBuilder("systemctl", "--user", "start", "ceres").redirectErrorStream(true).start()
    val out = p.inputStream.readBytes().decodeToString()
    if (p.waitFor() != 0) error("systemctl failed: $out")
    repeat(50) { if (ping()) return; Thread.sleep(200) }
    error("daemon did not start")
  }

  override fun create(name: String?): Long {
    val r = http.send(
      req("/chat?name=${enc(name ?: "")}").POST(HttpRequest.BodyPublishers.noBody()).build(),
      HttpResponse.BodyHandlers.ofString())
    check(r.statusCode() == 200) { "HTTP ${r.statusCode()} ${r.body()}" }
    return r.body().trim().toLong()
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

  override fun prompt(text: String, files: List<String>, onReject: (String) -> Unit) {
    val q = "/prompt?chat=$chat" + if (files.isEmpty()) "" else "&files=${enc(files.joinToString("\n"))}"
    http.sendAsync(req(q).POST(HttpRequest.BodyPublishers.ofString(text)).build(),
      HttpResponse.BodyHandlers.ofString())
      .thenAccept { if (it.statusCode() != 202) onReject("HTTP ${it.statusCode()} ${it.body()}") }
      .exceptionally { onReject(it.message ?: "request failed"); null }
  }

  override fun command(id: Long, text: String): String {
    val r = http.send(
      req("/command?chat=$id").timeout(java.time.Duration.ofMinutes(10))
        .POST(HttpRequest.BodyPublishers.ofString(text)).build(),
      HttpResponse.BodyHandlers.ofString())
    if (id == chat) r.headers().firstValue("X-Chat").ifPresent { select(it.toLong()) }
    return r.body()
  }

  // ofFile sets Content-Length; the daemon returns 413 without it
  override fun upload(file: Path): String {
    val r = http.send(
      req("/upload?chat=$chat&name=${enc(file.fileName.toString())}")
        .POST(HttpRequest.BodyPublishers.ofFile(file)).build(),
      HttpResponse.BodyHandlers.ofString())
    check(r.statusCode() == 201) { "HTTP ${r.statusCode()} ${r.body()}" }
    return r.body()
  }

  fun download(path: String, dest: Path) {
    http.send(req("/file?path=${enc(path)}").GET().build(), HttpResponse.BodyHandlers.ofFile(dest))
  }

  override fun cancel() {
    http.sendAsync(req("/cancel").POST(HttpRequest.BodyPublishers.noBody()).build(),
      HttpResponse.BodyHandlers.discarding())
  }

  fun listen(onEvent: (Long, Event) -> Unit) = Thread {
    while (true) {
      try {
        var evChat = -1L
        http.send(req("/events").header("Last-Event-ID", lastId.toString()).GET().build(),
          HttpResponse.BodyHandlers.ofLines()).body().forEach { line ->
          when {
            line.startsWith("id:") -> lastId = line.substring(3).trim().toLong()
            line.startsWith("chat:") -> evChat = line.substring(5).trim().toLong()
            line.startsWith("data:") -> onEvent(evChat, decode(line.substring(5).trim()))
          }
        }
      } catch (_: Exception) {}
      Thread.sleep(1000)
    }
  }.apply { isDaemon = true; start() }

  // === LOCAL STORAGE === //

  @Volatile override var chat = runCatching { chatFile.readText().trim().toLong() }.getOrDefault(-1L)
    private set

  override fun connect() { ensureDaemon(); initChat() }

  override fun chats() = Json.parseToJsonElement(getString("/chats")).jsonArray.map {
    val o = it.jsonObject
    ChatInfo(o.getValue("id").jsonPrimitive.long, o["name"]?.jsonPrimitive?.contentOrNull,
      o["updated"]?.jsonPrimitive?.longOrNull ?: 0)
  }

  override fun history(id: Long): List<HistoryLine> = Json.parseToJsonElement(
    getString("/history?chat=$id")
  ).jsonArray.map {
    val o = it.jsonObject
    HistoryLine(o.getValue("role").jsonPrimitive.content, o.getValue("text").jsonPrimitive.content)
  }
}
