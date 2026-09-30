package ceres.cli

import java.io.PrintStream
import ceres.*
import ceres.cli.Ansi

class Spinner(
  private val label: String,
  private val out: PrintStream = System.out,
) {
  private val frames = "◜◝◞◟"
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

  fun stop(ok: Boolean = true) {
    running = false
    thread?.interrupt()
    thread?.join()
    val mark = if (ok) "${Ansi.GREEN}✔" else "${Ansi.RED}✘"
    out.println("${Ansi.RESET}\r\u001B[K${mark}${Ansi.RESET}${Ansi.DIM} $label")
  }
}
