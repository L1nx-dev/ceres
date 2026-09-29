package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*

class ReadFile : Tool {
  override val def = ToolDefinition(
    name = "read_file",
    description = "Read a text file from the workspace. Path is relative to the workspace root.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") {
          put("type", "string")
          put("description", "relative file path")
        }
      }
      putJsonArray("required") { add("path") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'path'"
    return Files.readString(ctx.resolve(rel)).take(20_000)
  }
}
