package ceres.main

import kotlin.io.path.*
import kotlin.system.*
import kotlin.concurrent.thread
import java.util.stream.Stream

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
 
  fun events(lastId: Long): Stream<String> = http.send(
    req("/events").header("Last-Event-ID", lastId.toString()).GET().build(),
    java.net.http.HttpResponse.BodyHandlers.ofLines()).body()

    fun prompt(text: String) {
      val r = http.send(
        req("/prompt").POST(java.net.http.HttpRequest.BodyPublishers.ofString(text)).build(),
        java.net.http.HttpResponse.BodyHandlers.ofString())
      check(r.statusCode() == 202) { "HTTP ${r.statusCode()} ${r.body()}" }
    }

  fun command(line: String): String = http.send(
    req("/command").timeout(java.time.Duration.ofMinutes(10))
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

  val sw = client.command("/switch $chat")
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
  Thread.sleep(200) //TODO make this not as shitty
  client.prompt(text)
  done.await()
  println()
  exitProcess(if (result is Event.Done) 0 else 1)
}
