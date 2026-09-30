package ceres.main

import ceres.cli.*

fun main() {
  val xdg = Xdg()
  val config = Config(xdg)
  val agent = Agent(config, xdg)

  // ==== DEBUG DUMP ====

  println(config.baseUrl)
  println(config.model)
  println(config.workDir)

  // ==== MAIN LOOP ====

  while (true) {
    print("${Ansi.RESET}${Ansi.CYAN}>=> ")
    val line = readlnOrNull() ?: break
    println("${Ansi.RESET}⚳ " + agent.prompt(line))
  }
}
