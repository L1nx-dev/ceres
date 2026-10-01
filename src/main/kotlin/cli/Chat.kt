package ceres.cli

import ceres.main.*
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import kotlin.io.path.div
import org.jline.reader.impl.DefaultHighlighter
import org.jline.utils.AttributedString
import org.jline.utils.AttributedStyle
import org.jline.reader.impl.LineReaderImpl

class Chat {
  private val xdg = Xdg()
  private val config = Config(xdg)
  private val agent = Agent(config, xdg)
  private val commands = Commands(agent)

  fun start() {

    // ==== DEBUG DUMP ====
    println(config.baseUrl)
    println(config.model)
    println(config.workDir)

    // ==== MAIN LOOP ====
    val terminal = TerminalBuilder.builder().system(true).build()
    val reader = LineReaderBuilder.builder()
      .terminal(terminal)
        .highlighter(object : DefaultHighlighter() {
          override fun highlight(reader: LineReader, buffer: String): AttributedString {
            val cmd = buffer.startsWith("/")
            (reader as LineReaderImpl).setPrompt(
              if (cmd) "\u001b[35m=>> \u001b[0m" else "\u001b[36m>=> \u001b[0m"
            )
            val color = if (cmd) AttributedStyle.MAGENTA else AttributedStyle.CYAN
            return AttributedString(buffer, AttributedStyle.DEFAULT.foreground(color))
          }
        })
      .variable(LineReader.HISTORY_FILE, xdg.share / "history")
      .build()

    while (true) {
      val input = try {
        reader.readLine("${Ansi.CYAN}>=> ${Ansi.RESET}")
      } catch (_: UserInterruptException) {   // Ctrl+C: drop the line
        continue
      } catch (_: EndOfFileException) {       // Ctrl+D: quit
        break
      }
      if (input.isBlank()) continue
      println(
        if (input.startsWith("/")) commands.run(input)
        else "${Ansi.RESET}⚳ " + agent.prompt(input)
      )
    }
    terminal.close()
  }
}
