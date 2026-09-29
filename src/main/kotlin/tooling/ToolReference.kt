package ceres.tools

import kotlinx.serialization.json.*

class ToolReference : Tool {
  override val def = ToolDefinition(
    name = "reference",
    description = "if you are reading this, mention that the tool is unfinished",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("text") {
          put("type", "string")
          put("description", "what this argument is")
        }
      }
      putJsonArray("required") { add("text") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val text = args["text"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'text'"
    return ctx.exec(listOf("echo", text), null, 10)
  }
}
