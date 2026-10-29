package ceres.cli

import java.io.PrintStream
import ceres.cli.*
import ceres.core.*
import org.jline.terminal.*

object Term {
  private val terminal by lazy {
    runCatching { TerminalBuilder.builder().system(true).build() }.getOrNull()
  }
  val width: Int get() = terminal?.width?.takeIf { it > 0 } ?: 80
}

class Spinner(
  label: String,
  private val out: PrintStream = System.out,
) {
  private val label = label.take(Term.width - 4)
  // private val frames = "◜◝◞◟"
  private val frames = "⠋⠙⠹⠸⠼⠴⠦⠧⠏"
  @Volatile private var running = false
  private var thread: Thread? = null

  fun start(): Spinner {
    running = true
    thread = Thread {
      var i = 0
      while (running) {
        out.print("${Ansi.RESET}${Ansi.DIM}\r${frames[i++ % frames.length]} $label")
        out.flush()
        try { Thread.sleep(100) } catch (_: InterruptedException) { break }
      }
    }.apply { isDaemon = true; start() }
    return this
  }

  fun stop(ok: Boolean = true, icon: String? = null) {
    running = false
    thread?.interrupt()
    thread?.join()
    val mark = when {
      !ok -> "${Ansi.RED} "
      icon != null -> "${Ansi.GREEN}${icon}"
      else -> "${Ansi.GREEN}󰸞 "
    }
    out.println("${Ansi.RESET}\r\u001B[K${mark}${Ansi.RESET}${Ansi.DIM} $label")
  }
}
