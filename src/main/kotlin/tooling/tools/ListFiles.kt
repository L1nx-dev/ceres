package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.attribute.*
import java.nio.file.*

class ListFiles : Tool {
  override val def = ToolDefinition(
    name = "ls",
    description = "List files in a directory.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") {
          put("type", "string")
          put("description", "relative file path, default '.'")
        }
      }
      putJsonArray("required") { add("path") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: "."
    val dir = ctx.resolve(rel)
    if (!Files.isDirectory(dir)) return "error: not a directory: $rel"

    val lines = Files.list(dir).use { s ->
      s.sorted().limit(500).map { p ->
        val a = Files.readAttributes(p, PosixFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val type = when { a.isDirectory -> 'd'; a.isSymbolicLink -> 'l'; else -> '-' }
        "$type${PosixFilePermissions.toString(a.permissions())} ${a.owner().name} ${a.size()} ${a.lastModifiedTime().toInstant()} ${p.fileName}"
      }.toList()
    }
    return lines.joinToString("\n").ifEmpty { "(empty)" }
  }
}
