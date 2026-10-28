package ceres.cli

import ceres.system.*
import ceres.core.*
import java.util.stream.Stream
import kotlin.io.path.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.serialization.json.*

class Client(private val config: Config, private val xdg: Xdg) {
  private val http = java.net.http.HttpClient.newHttpClient()
  private val base = "http://127.0.0.1:${config.port}"

  private fun req(path: String) =
    java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + path))
      .header("Authorization", "Bearer ${config.token}")

  fun ping(): Boolean = try {
    http.send(req("/status").GET().build(), java.net.http.HttpResponse.BodyHandlers.discarding())
      .statusCode() == 200
  } catch (_: Exception) { false }

  fun ensureDaemon() {
    if (ping()) return
    val p = ProcessBuilder("systemctl", "--user", "start", "ceres")
      .redirectErrorStream(true).start()
    val out = p.inputStream.readBytes().decodeToString()
    if (p.waitFor() != 0) error("systemctl failed: $out")
    repeat(50) { if (ping()) return; Thread.sleep(200) }
    error("daemon did not start")
  }

  fun create(): Long {
    val r = http.send(req("/chat")
      .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
      java.net.http.HttpResponse.BodyHandlers.ofString())
    check(r.statusCode() == 200) { "HTTP ${r.statusCode()} ${r.body()}" }
    return r.body().trim().toLong()
  }
 
  fun events(lastId: Long): Stream<String> = http.send(
    req("/events").header("Last-Event-ID", lastId.toString()).GET().build(),
    java.net.http.HttpResponse.BodyHandlers.ofLines()).body()

    fun prompt(text: String) {
      val r = http.send(
        req("/prompt").POST(java.net.http.HttpRequest.BodyPublishers.ofString(text)).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString())
      check(r.statusCode() == 202) { "HTTP ${r.statusCode()} ${r.body()}" }
    }

  fun chats(): List<Pair<Long, String?>> {
    val r = http.send(req("/chats").GET().build(),
      java.net.http.HttpResponse.BodyHandlers.ofString())
    check(r.statusCode() == 200) { "HTTP ${r.statusCode()} ${r.body()}" }
    return kotlinx.serialization.json.Json.parseToJsonElement(r.body()).jsonArray.map {
      val o = it.jsonObject
      o.getValue("id").jsonPrimitive.long to o["name"]?.jsonPrimitive?.contentOrNull
    }
  }

  fun command(line: String, chat: Long): String = http.send(
    req("/command?chat=$chat").timeout(java.time.Duration.ofMinutes(10))
      .POST(java.net.http.HttpRequest.BodyPublishers.ofString(line)).build(),
    java.net.http.HttpResponse.BodyHandlers.ofString()).body()

  fun cancel() { http.send(req("/cancel").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
    java.net.http.HttpResponse.BodyHandlers.discarding()) }

  fun shutdown() { http.send(req("/shutdown").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
    java.net.http.HttpResponse.BodyHandlers.discarding()) }
}

fun promptOnce(config: Config, xdg: Xdg, chat: String, text: String) {
  val client = Client(config, xdg)
  client.ensureDaemon()

  val chatId = client.create()
  val sw = client.command("/switch $chat", chatId)
  if (sw.startsWith("error")) { System.err.println(sw); exitProcess(2) }

  val done = java.util.concurrent.CountDownLatch(1)
  var result: Event = Event.Cancelled

  thread(isDaemon = true) {
    val res = client.events(-1)
    res.forEach { line ->
      if (line.startsWith("data:")) {
        val e = decode(line.substring(5).trim())
        when (e) {
          is Event.Delta -> { print(e.text); System.out.flush() }
          is Event.Done, is Event.Error, Event.Cancelled -> { result = e; done.countDown() }
          else -> {}
        }
      }
    }
  }
  Thread.sleep(100) //TODO make this not as shitty
  client.prompt(text)
  done.await()
  println()
  exitProcess(if (result is Event.Done) 0 else 1)
}
