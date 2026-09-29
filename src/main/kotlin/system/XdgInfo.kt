package ceres.main

import kotlin.io.path.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Xdg() {
  val home = Path(System.getProperty("user.home"))
  val config =
    System.getenv("XDG_CONFIG_HOME")
      ?.let { Path(it) / "ceres" }
      ?: (home / ".config" / "ceres")

  val share =
    System.getenv("XDG_DATA_HOME")
      ?.let { Path(it) / "ceres" }
      ?: (home / ".local" / "share" / "ceres")

  init {
    config.createDirectories()

    val file = config / "config.json"
    if (file.notExists()) {
      val stream = javaClass.getResourceAsStream("/config.json")
        ?: error("missing resource: /config.json")
      stream.use { Files.copy(it, file) }
    }
  }
}
