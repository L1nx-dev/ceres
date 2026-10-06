package ceres.main

import ceres.cli.*

fun main(args: Array<String>) {
  if (args.firstOrNull() == "daemon") {
    val xdg = Xdg()
    val config = Config(xdg)
    val agent = Agent(config, xdg)
    val dreamer = Dreamer(config, xdg)
    val daemon = Daemon(config, Hub(agent), Commands(agent, dreamer))
    daemon.start()
    println("listening on ${config.listen}:${config.port}")
    Thread.currentThread().join()
  } else {
    Chat().start()
  }
}
