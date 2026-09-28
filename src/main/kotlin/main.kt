package main.ceres

fun main() {
  val xdg = Xdg()
  val config = Config(xdg)
  println(config.test1)
  println(config.test2)
}
