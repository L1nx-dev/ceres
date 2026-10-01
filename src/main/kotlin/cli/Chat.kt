package ceres.cli

import ceres.main.*

class Chat() {
  val xdg = Xdg()
  val config = Config(xdg)
  val agent = Agent(config, xdg)

  fun start() {

  // ==== DEBUG DUMP ====
    println(config.baseUrl)
    println(config.model)
    println(config.workDir)

  // ==== MAIN LOOP ====
    while (true) {
      print("${Ansi.RESET}${Ansi.CYAN}>=> ")
      val input = readlnOrNull() ?: break
      print(Ansi.RESET)
      if (!input.isEmpty()) {
        if (input[0] != "/".toCharArray()[0]) {println("${Ansi.RESET}⚳ " + agent.prompt(input))} // this is ugly
        // else {when (input){
          // "/new" -> //command
        // }}
      }
    }
  }
}
