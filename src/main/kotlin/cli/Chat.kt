package ceres.cli

import ceres.system.*
import ceres.core.*
import kotlin.io.path.div
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import org.jline.reader.impl.DefaultHighlighter
import org.jline.utils.*
import org.jline.reader.impl.LineReaderImpl
import org.jline.terminal.Terminal

class Chat( 
  val xdg: Xdg,
  val config: Config
) {
  private var spinner: Spinner? = null
  private var icon = "✔"
  private var streaming = false
  private val http = java.net.http.HttpClient.newHttpClient()
  private val httpAddress = "http://${config.host}:${config.port}"
  @Volatile private var lastId = -1L
  @Volatile private var done = java.util.concurrent.CountDownLatch(1)
  @Volatile private var chatId: Long = -1L

  private fun req(path: String) =
    java.net.http.HttpRequest.newBuilder(java.net.URI.create(httpAddress + path))
      .header("Authorization", "Bearer ${config.token}")

  private fun listen() = kotlin.concurrent.thread(isDaemon = true, name = "events") {
    while (true) {
      try {
        val res = http.send(
          req("/events").header("Last-Event-ID", lastId.toString()).GET().build(),
          java.net.http.HttpResponse.BodyHandlers.ofLines())
        res.body().forEach { line ->
          when {
            line.startsWith("id:") -> lastId = line.substring(3).trim().toLong()
            line.startsWith("data:") -> {
              val e = decode(line.substring(5).trim())
              render(e)
              if (e is Event.Done || e is Event.Error || e === Event.Cancelled) done.countDown()
            }
          }
        }
      }
      catch (e: Exception) {
        System.err.println("events: $e")
      } catch (t: Throwable) {
        System.err.println("events thread died: $t"); throw t
      }
      Thread.sleep(1000)
    }
  }

  private fun render(e: Event) {
    when (e) {
      is Event.Delta -> {
        if (!streaming) { print("${Ansi.RESET}⚳ "); streaming = true }
        print(e.text); System.out.flush()
      }
      is Event.ToolStart -> {
        if (streaming) { println(); streaming = false }
        icon = e.icon
        spinner = Spinner(e.label).start()
      }
      is Event.ToolEnd -> { spinner?.stop(ok = e.ok, icon = icon); spinner = null }
      is Event.Done -> { if (streaming) println(); streaming = false }
      is Event.Error -> {
        spinner?.stop(ok = false, icon = icon); spinner = null
        if (streaming) println(); streaming = false
        println("error: ${e.message}")
      }
      Event.Cancelled -> {
        spinner?.stop(ok = false, icon = icon); spinner = null
        if (streaming) println(); streaming = false
        println("[cancelled]")
      }
    }
  }

  fun start() {

    // ==== DEBUG DUMP ====
    println(config.baseUrl)
    println(config.model)
    println(config.workDir)

    // ==== MAIN LOOP ====
    val terminal = TerminalBuilder.builder().system(true).build()
    val tokenRe = Regex("\\s+|\\S+")
    val yellow = AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)

    val reader = LineReaderBuilder.builder()
      .terminal(terminal)
      .highlighter(object : DefaultHighlighter() {
        override fun highlight(reader: LineReader, buffer: String): AttributedString {
          val cmd = buffer.startsWith("/")
          (reader as LineReaderImpl).setPrompt(
            if (cmd) "\u001b[35m=>> \u001b[0m" else "\u001b[36m>=> \u001b[0m"
          )
          val base = AttributedStyle.DEFAULT.foreground(if (cmd) AttributedStyle.MAGENTA else AttributedStyle.CYAN)
          if (!cmd) return AttributedString(buffer, base)

            val tokens = tokenRe.findAll(buffer).map { it.value }.toList()
          val words = tokens.count { it.isNotBlank() }
          val sb = AttributedStringBuilder()
          var n = 0
          for (t in tokens) {
            if (t.isBlank()) { sb.styled(base, t); continue }
            n++
            val isYellow = if (words == 2) n == 2 else n >= 3
            sb.styled(if (isYellow) yellow else base, t)
          }
          return sb.toAttributedString()
        }
      })
      .variable(LineReader.HISTORY_FILE, xdg.share / "history")
      .build()

    val events = listen()

    while (true) {
      val input = try {
        reader.readLine("${Ansi.CYAN}>=> ${Ansi.RESET}")
      } catch (_: UserInterruptException) {
        continue
      } catch (_: EndOfFileException) {
        break
      }
      when {
        input.isBlank() -> {}
        input == "/clear" -> {
          terminal.puts(InfoCmp.Capability.clear_screen)
          terminal.flush()
        }

        input == "/chats" -> {
          Client(config, xdg).chats().forEach { (id, name) ->
            println("${if (id == chatId) "*" else " "} [$id] ${name ?: ""}")
          }
        }

        input.startsWith("/") -> {
          val r = http.send(
            req("/command?chat=$chatId")
              .timeout(java.time.Duration.ofMinutes(10))
              .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
              .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString())
          r.headers().firstValue("X-Chat").ifPresent { chatId = it.toLong() }
          println(r.body())
        }

        else -> {
          done = java.util.concurrent.CountDownLatch(1)
          val prev = terminal.handle(Terminal.Signal.INT) {
            http.sendAsync(
              req("/cancel").POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),
              java.net.http.HttpResponse.BodyHandlers.discarding())
          }
          try {
            val r = http.send(
              req("/prompt?chat=$chatId")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(input))
                .build(),
              java.net.http.HttpResponse.BodyHandlers.ofString())
              if (r.statusCode() == 202) {
                while (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                  if (!events.isAlive) { println("error: lost connection to daemon"); break }
                }
              }
            else println("error: HTTP ${r.statusCode()} ${r.body()}")
          } finally { terminal.handle(Terminal.Signal.INT, prev) }
        }
      }
    }
    terminal.close()
  }
}
