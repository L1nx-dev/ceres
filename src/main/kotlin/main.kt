package ceres.main

fun main() {
  val xdg = Xdg()
  val config = Config(xdg)
  val agent = Agent(config)
}
