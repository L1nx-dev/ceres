package ceres.main

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class Share(private val root: Path) {
  private val realRoot = root.toRealPath()

  fun open(rel: String): Path? {
    val p = runCatching { root.resolve(rel).toRealPath() }.getOrNull() ?: return null
    return p.takeIf { it.startsWith(realRoot) && Files.isRegularFile(it) }
  }

  fun openIn(chat: Long, rel: String): Path? {
    if (".." in rel.split('/')) return null
    val d = runCatching { dir(chat).toRealPath() }.getOrNull() ?: return null
    return open(rel)?.takeIf { it.startsWith(d) }
  }

  fun dir(chat: Long): Path = root / chat.toString()

  fun save(chat: Long, name: String, body: InputStream): String {
    val clean = Path.of(name).fileName?.toString()
      ?.takeIf { it.isNotBlank() && it != ".." && it.none { c -> c.isISOControl() } }
      ?: error("bad name")
    val dir = dir(chat).createDirectories()
    val stem = clean.substringBeforeLast('.', clean)
    val ext = clean.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
    var target = dir / clean
    var n = 1
    while (target.exists()) target = dir / "$stem-${n++}$ext"
    Files.copy(body, target)
    return "$chat/${target.fileName}"
  }
}
