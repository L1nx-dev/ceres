package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*

class RemoveFile : Tool {
  override val def = ToolDefinition(
    name = "remove",
    description = "Removes the specified file or directory; be careful.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") { put("type", "string"); put("description", "relative file path") }
      }
      putJsonArray("required") { add("path") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'path'"
    val target = ctx.resolve(rel)

    if (target == ctx.root) return "error: refusing to delete the workspace root"
    if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return "error: $rel does not exist"

    val kind = if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) "directory" else "file"
    return try {
      Files.delete(target)
      "ok: deleted $kind $rel"
    } catch (e: DirectoryNotEmptyException) {
      "error: $rel is not empty; remove its contents first"
    }
  }
}
