package ceres.cli

import ceres.main.*
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import kotlin.io.path.div
import org.jline.reader.impl.DefaultHighlighter
import org.jline.utils.*
import org.jline.reader.impl.LineReaderImpl

class Chat {
  private val xdg = Xdg()
  private val config = Config(xdg)
  private val agent = Agent(config, xdg)
  private val dreamer = Dreamer(config, xdg)
  private val commands = Commands(agent, dreamer)

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
        input.startsWith("/") -> println(commands.run(input))
        else -> println("${Ansi.RESET}⚳ " + agent.prompt(input))
      }
    terminal.close()
    }
  }
}
