package ceres.system

import kotlin.io.path.*
import java.nio.file.Files
import ceres.main.*
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class Xdg {
  val home = Path(System.getProperty("user.home"))

  val config =
    System.getenv("XDG_CONFIG_HOME")
      ?.let { Path(it) / "ceres" }
      ?: (home / ".config" / "ceres")

  val share =
    System.getenv("XDG_DATA_HOME")
      ?.let { Path(it) / "ceres" }
      ?: (home / ".local" / "share" / "ceres")

  val skills = config / "skills"
  val memory = config / "memory"

  private data class Resource(val name: String, val targetDir: Path)

  private val defaultResources = listOf(
    Resource("config.json", config),
    Resource("boot.script", config),
    Resource("SOUL.md", config),
    Resource("USER.md", config),
    Resource("SKETCHPAD.md", config),
  )

  init {
    listOf(config, share, skills, memory).forEach { it.createDirectories() }
    defaultResources.forEach { (name, dir) -> copyResourceIfMissing(name, dir) }
  }

  private fun copyResourceIfMissing(resourceName: String, targetDir: Path) {
      val target = targetDir / resourceName
      if (target.exists()) return
      target.parent?.createDirectories()
      javaClass.getResourceAsStream("/$resourceName")
          ?.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
          ?: error("missing resource: /$resourceName")
  }
}
