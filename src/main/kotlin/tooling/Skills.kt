package ceres.tools

import java.nio.file.*

data class SkillInfo(val name: String, val description: String, val dir: Path)

class Skills(private val root: Path) {
  private val keyRe = Regex("""^([A-Za-z_-]+):\s*(.*)$""")

  fun list(): List<SkillInfo> {
    if (!Files.isDirectory(root)) return emptyList()
    return Files.list(root).use { s ->
      s.filter { Files.isRegularFile(it.resolve("SKILL.md")) }
        .sorted()
        .map { SkillInfo(it.fileName.toString(), describe(it), it) }
        .toList()
    }
  }

  fun find(name: String): SkillInfo? = list().firstOrNull { it.name == name }

  /** relative paths of everything in the skill except SKILL.md */
  fun files(s: SkillInfo): List<String> =
    Files.walk(s.dir).use { w ->
      w.filter { Files.isRegularFile(it) && it.fileName.toString() != "SKILL.md" || it.parent != s.dir && Files.isRegularFile(it) }
        .map { s.dir.relativize(it).toString() }
        .sorted().limit(100).toList()
    }

  /** resolves a file inside the skill, null if it escapes the skill dir */
  fun resolve(s: SkillInfo, file: String): Path? {
    val p = s.dir.resolve(file).normalize()
    return p.takeIf { it.startsWith(s.dir) && Files.isRegularFile(it) }
  }

  private fun describe(dir: Path): String {
    val lines = Files.readString(dir.resolve("SKILL.md")).lines()
    if (lines.firstOrNull()?.trim() != "---") return "(no description)"
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    if (end < 0) return "(no description)"
    var key: String? = null
    val out = linkedMapOf<String, String>()
    for (l in lines.subList(1, end + 1)) {
      val m = keyRe.matchEntire(l)
      if (m != null && !l.startsWith(" ")) { key = m.groupValues[1]; out[key] = m.groupValues[2] }
      else if (key != null && l.isNotBlank()) out[key] = out[key] + " " + l.trim()
    }
    return (out["description"] ?: "")
      .replace(Regex("""^[>|][+-]?\s*"""), "")
      .trim().trim('"', '\'')
      .ifEmpty { "(no description)" }
      .take(400)
  }
}
