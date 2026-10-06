package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*

class MakeFile : Tool {
  override val icon get() = "✏️"
  override val def = ToolDefinition(
    name = "create",
    description = "Create a file with the given content (empty if omitted). " +
      "Creates missing parent directories. An existing empty file is filled in. " +
      "A non-empty file is only replaced when overwrite=true. Do not abuse overwrite; use edit_file for changes to existing files.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") { put("type", "string"); put("description", "relative file path") }
        putJsonObject("content") { put("type", "string"); put("description", "initial file content") }
        putJsonObject("overwrite") {
          put("type", "boolean")
          put("description", "replace an existing non-empty file (default false)")
        }
      }
      putJsonArray("required") { add("path") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'path'"
    val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""
    val overwrite = args["overwrite"]?.jsonPrimitive?.booleanOrNull ?: false
    val file = ctx.resolve(rel)

    if (Files.isDirectory(file)) return "error: $rel is a directory"
    if (Files.exists(file) && Files.size(file) > 0 && !overwrite)
      return "error: $rel exists and is not empty. Use edit_file for changes, or set overwrite=true to replace it entirely."

    Files.createDirectories(file.parent)
    Files.writeString(file, content)
    return "ok: wrote $rel (${content.length} chars)"
  }
}
