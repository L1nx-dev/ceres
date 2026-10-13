package ceres.main

import ceres.system.*
import ceres.tools.Skills
import java.nio.file.*
import java.nio.file.attribute.*
import java.nio.file.Path

class StartupList(private val xdg: Xdg) {
  private fun listFiles(dir: Path): String {
    if (!Files.isDirectory(dir)) return "(empty)"
    val lines = Files.list(dir).use { s ->
      s.sorted().limit(500).map { p ->
        val a = Files.readAttributes(p, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val type = when { a.isDirectory -> 'd'; a.isSymbolicLink -> 'l'; else -> '-' }
        "$type${PosixFilePermissions.toString(a.permissions())} ${a.owner().name} ${a.size()} ${a.lastModifiedTime().toInstant()} ${p.fileName}"
      }.toList()
    }
    return lines.joinToString("\n").ifEmpty { "(empty)" }
  }

  private fun memories() = "# Available memories\n" + listFiles(xdg.memory)

  private fun skills(): String {
    val items = Skills(xdg.skills).list()
    val body = if (items.isEmpty()) "(none)" else items.joinToString("\n") { "- ${it.name}: ${it.description}" }
    return "# Available skills (load with the `skill` tool)\n$body"
  }

  fun startup(): String = memories() + "\n\n" + skills()
}

