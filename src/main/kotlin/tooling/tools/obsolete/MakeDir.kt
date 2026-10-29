package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*

class MakeDir : Tool {
  override val icon get() = "📂"
  override val def = ToolDefinition(
    name = "mkdir",
    description = "Create a directory (and missing parents).",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") {
          put("type", "string")
          put("description", "relative directory path")
        }
      }
      putJsonArray("required") { add("path") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'path'"
    val dir = ctx.resolve(rel)
    Files.createDirectories(dir)
    return "ok: created $rel"
  }
}
