package ceres.main

import ceres.cli.*
import kotlin.system.exitProcess
import ceres.system.*

fun main(args: Array<String>) {
  val xdg = Xdg()
  val config = Config(xdg)
  when (args.firstOrNull()) {
    "daemon" -> runDaemon(config, xdg)
    "stop"   -> Client(config, xdg).shutdown()
    "kill"   -> ProcessBuilder("systemctl", "--user", "kill", "ceres").inheritIO().start().waitFor()
    "prompt" -> {
      if (args.size < 3) { System.err.println("usage: ceres prompt <chat> <text>"); exitProcess(2) }
      promptOnce(config, xdg, chat = args[1], text = args[2])
    }
    else -> {
      val client = Client(config, xdg)
      client.ensureDaemon()
      Chat(xdg, config).start()
    }
  }
}

