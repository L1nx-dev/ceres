package ceres.main

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Daemon(
  private val config: Config,
  private val hub: Hub, private val commands: Commands
) {
  private val http = HttpServer.create(InetSocketAddress(config.listen, config.port), 0)

  init {
    require(config.token.isNotBlank()) { "token mustn't be empty" }
    http.executor = Executors.newCachedThreadPool()

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
                      else "id: ${item.id}\ndata: ${encode(item.event)}\n\n"
          out.write(chunk.toByteArray()); out.flush()
        }
      } catch (_: IOException) {
      } finally { hub.unsubscribe(q) }
    }

    route("/prompt") { ex ->
      val text = ex.requestBody.readBytes().decodeToString()
      reply(ex, if (hub.start(text)) 202 else 409)
    }

    route("/cancel") { ex -> hub.cancel(); reply(ex, 204) }

    route("/command") { ex ->
      val line = ex.requestBody.readBytes().decodeToString()
      if (hub.busy) reply(ex, 409, "run active")
      else reply(ex, 200, commands.run(line))
    }
  }

  fun start() = http.start()
  fun stop() = http.stop(0)

  private fun route(path: String, handler: (HttpExchange) -> Unit) {
    http.createContext(path) { ex ->
      try {
        if (!ex.authed()) reply(ex, 401)
        else handler(ex)
      } catch (e: Exception) {
        runCatching { reply(ex, 500, e.message ?: "error") }
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
