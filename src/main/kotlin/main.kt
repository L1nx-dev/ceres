package ceres.main

fun main() {
  val xdg = Xdg()
  val config = Config(xdg)
  val agent = Agent(config)

  // ==== DEBUG DUMP ====

  println(config.baseUrl)
  println(config.model)

  // ==== MAIN LOOP ====

  while (true) {
    print("⚳ > ")
    val line = readlnOrNull() ?: break
    println("✔ | " + agent.prompt(line))
  }
}
