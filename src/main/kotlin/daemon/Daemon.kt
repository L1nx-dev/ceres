package ceres.main

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.io.path.*
import ceres.system.*
import ceres.core.*

private const val MAX_UPLOAD = 512L * 1024 * 1024

class Daemon(
  private val config: Config,
  private val hub: Hub, private val commands: Commands,
  private val agent: Agent
) {
  private val http = HttpServer.create(InetSocketAddress(config.listen, config.port), 0)

  private fun HttpExchange.query(): Map<String, String> =
    (requestURI.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.associate {
      val p = it.split('=', limit = 2)
      java.net.URLDecoder.decode(p[0], Charsets.UTF_8) to java.net.URLDecoder.decode(p.getOrElse(1) { "" }, Charsets.UTF_8)
    }
  private fun HttpExchange.chat(): Long? = query()["chat"]?.toLongOrNull()
  val share = Share(Path.of(config.workDir).resolve("mnt/share").also { it.createDirectories() })

  init {
    require(config.token.isNotBlank()) { "token mustn't be empty" }
    http.executor = Executors.newCachedThreadPool()

    route("/file") { ex ->
      val p = share.open(ex.query()["path"] ?: return@route reply(ex, 400)) ?: return@route reply(ex, 404)
      ex.responseHeaders.add("Content-Type", "application/octet-stream")
      ex.sendResponseHeaders(200, Files.size(p))
      Files.copy(p, ex.responseBody)
    }

    route("/upload") { ex ->
      val q = ex.query()
      val chat = q["chat"]?.toLongOrNull() ?: return@route reply(ex, 400)
      val name = q["name"] ?: return@route reply(ex, 400)
      val len = ex.requestHeaders.getFirst("Content-Length")?.toLongOrNull()
      if (len == null || len > MAX_UPLOAD) return@route reply(ex, 413)
      reply(ex, 201, share.save(chat, name, ex.requestBody)) // returns <folder>/<file>
    }

    route("/history") { ex ->
      val chat = ex.chat() ?: return@route reply(ex, 400, "missing chat")
      ex.responseHeaders.add("Content-Type", "application/json")
      reply(ex, 200, buildJsonArray {
        for (l in agent.transcript(chat)) addJsonObject { put("role", l.role); put("text", l.text) }
      }.toString())
    }

    route("/shutdown") { ex ->
      reply(ex, 202)
      thread { while (hub.busy) Thread.sleep(200); exitProcess(0) }
    }

    route("/status") { reply(it, 200, "ok") }

    route("/events") { ex ->
      val last = ex.requestHeaders.getFirst("Last-Event-ID")?.toLongOrNull() ?: 0L
      ex.responseHeaders.add("Content-Type", "text/event-stream")
      ex.responseHeaders.add("Cache-Control", "no-cache")
      ex.sendResponseHeaders(200, 0)
      val out = ex.responseBody
      val q = hub.subscribe(last)
      try {
        while (true) {
          val item = q.poll(15, TimeUnit.SECONDS)
          val chunk = if (item == null) ": ping\n\n"
            else "id: ${item.id}\nchat: ${item.chat}\ndata: ${encode(item.event)}\n\n"
          out.write(chunk.toByteArray()); out.flush()
        }
      } catch (_: IOException) {
      } finally { hub.unsubscribe(q) }
    }

    route("/prompt") { ex ->
      val q = ex.query()
      val chat = q["chat"]?.toLongOrNull() ?: return@route reply(ex, 400, "missing chat")
      if (!agent.exists(chat)) return@route reply(ex, 404, "no such chat")
      val files = (q["files"] ?: "").split('\n').filter { it.isNotBlank() }
      if (files.any { share.open(it) == null }) return@route reply(ex, 400, "bad file") // upload
      reply(ex, if (hub.start(chat, ex.requestBody.readBytes().decodeToString(), files)) 202 else 409)
    }

    route("/chats") { ex ->
      val cur = ex.query()["current"]?.toLongOrNull() ?: -1L
      ex.responseHeaders.add("Content-Type", "application/json")
      reply(ex, 200, buildJsonArray {
        for (c in agent.chats(cur)) addJsonObject { put("id", c.id); put("name", c.name) }
      }.toString())
    }

    route("/chat") { ex ->
      val name = ex.query()["name"]?.takeIf { it.isNotBlank() }
      if (name != null) commands.checkName(name)?.let { return@route reply(ex, 400, it) }
      reply(ex, 200, agent.createChat(name).toString())
    }

    route("/command") { ex ->
      val chat = ex.chat() ?: return@route reply(ex, 400, "missing chat")
      if (hub.busy) return@route reply(ex, 409, "run active")
      val r = commands.run(ex.requestBody.readBytes().decodeToString(), chat)
      r.chat?.let { ex.responseHeaders.add("X-Chat", it.toString()) }
      reply(ex, 200, r.text)
    }

    route("/cancel") { ex -> hub.cancel(); reply(ex, 204) }
  }

  fun start() = http.start()
  fun stop() = http.stop(0)

  private fun route(path: String, handler: (HttpExchange) -> Unit) {
    http.createContext(path) { ex ->
      try {
        if (!ex.authed()) reply(ex, 401)
        else handler(ex)
      } catch (t: Throwable) {
        System.err.println("handler ${ex.requestURI}: $t"); System.err.println(t.stackTraceToString())
        runCatching { reply(ex, 500, t.message ?: t.javaClass.simpleName) }
      } finally { ex.close() }
    }
  }

  private fun reply(ex: HttpExchange, code: Int, body: String = "") {
    if (body.isEmpty()) { ex.sendResponseHeaders(code, -1); return }
    val b = body.toByteArray()
    ex.sendResponseHeaders(code, b.size.toLong())
    ex.responseBody.write(b)
  }

  private fun HttpExchange.authed(): Boolean {
    val h = requestHeaders.getFirst("Authorization") ?: ""
    return MessageDigest.isEqual(h.toByteArray(), "Bearer ${config.token}".toByteArray())
  }
}  

fun runDaemon(config: Config, xdg: Xdg, share: Share) {
  val agent = Agent(config, xdg)
  val hub = Hub(agent)
  val share = Share(Path.of(config.workDir).resolve("mnt/share").also { it.createDirectories() })
  val daemon = Daemon(
    config = config,
    hub = hub,
    commands = Commands(agent, Dreamer(config, xdg)),
    agent = agent,
  )
  Runtime.getRuntime().addShutdownHook(Thread {
    hub.cancel()
    repeat(30) { if (hub.busy) Thread.sleep(100) }
    daemon.stop()
  })
  daemon.start()
  println("listening on ${config.listen}:${config.port}")
  Thread.currentThread().join()
}
