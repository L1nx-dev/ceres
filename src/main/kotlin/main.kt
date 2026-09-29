package ceres.main

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
    print("⚳ > ")
    val line = readlnOrNull() ?: break
    println("✔ | " + agent.prompt(line))
  }
}
