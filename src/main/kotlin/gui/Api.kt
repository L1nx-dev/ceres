package ceres.gui.desktop

import ceres.core.*
import ceres.system.*
import ceres.gui.common.ChatApi
import kotlinx.serialization.json.*

class Api(private val config: Config): ChatApi {
  private val http = java.net.http.HttpClient.newHttpClient()
  private val base = "http://${config.host}:${config.port}"
  @Volatile private var lastId = -1L

  private fun req(path: String) =
    java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path))
      .header("Authorization", "Bearer ${config.token}")

  override fun prompt(text: String) { http.sendAsync(req("/prompt")
    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(text)).build(),
    java.net.http.HttpResponse.BodyHandlers.discarding()) }

  override fun cancel() { http.sendAsync(req("/cancel")
    .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
    java.net.http.HttpResponse.BodyHandlers.discarding()) }

  override fun history(): List<HistoryLine> = Json.parseToJsonElement(
    http.send(req("/history").GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body()
  ).jsonArray.map {
    val o = it.jsonObject
    HistoryLine(o.getValue("role").jsonPrimitive.content, o.getValue("text").jsonPrimitive.content)
  }

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
