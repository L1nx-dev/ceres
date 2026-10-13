package ceres.main

import kotlinx.serialization.json.*
import java.nio.file.attribute.*
import java.nio.file.*
import ceres.tools.*
import ceres.core.*
import java.nio.file.Path
import kotlin.io.path.div
import ceres.cli.Spinner
import kotlin.io.path.*
	
class StartupList(
  xdg: Xdg
) {
  private fun listFiles(dir) {
    val lines = Files.list(dir).use { s ->
      s.sorted().limit(500).map { p ->
        val a = Files.readAttributes(p, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val type = when { a.isDirectory -> 'd'; a.isSymbolicLink -> 'l'; else -> '-' }
        "$type${PosixFilePermissions.toString(a.permissions())} ${a.owner().name} ${a.size()} ${a.lastModifiedTime().toInstant()} ${p.fileName}"
      }.toList()
    }
    return lines.joinToString("\n").ifEmpty { "(empty)" }
  }
  
  private fun memories() {
    var f = "\n# Available memories"
    f = f + listFiles(xdg.memory)
    return f
  }

  private fun skills(final) {
    var f = final + "# Available skills"
    f = f + listFiles(xdg.skills)
    return f
  }

  fun startup() {return = memories() + skills()}
}

  // private fun checkPath(dir: String) {
  //   private fun where(type: String) {
  //     if (type = "mem") {
  //       println("memory register"); return
  //     } else println("skill index"); return
  //   }}
  //   val p = root.resolve(dir).normalize()
  //   require(p.startsWith(root)) where(type)
  //   var probe = p
  //   while (!Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) probe = probe.parent
  //   require(probe.toRealPath().startsWith(root)) where(type)
  //   return p
  // }
