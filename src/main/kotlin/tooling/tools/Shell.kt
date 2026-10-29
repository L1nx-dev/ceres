package ceres.tools

import kotlinx.serialization.json.*

class Shell : Tool {
  // override val icon get() = "🦪"
  override val def = ToolDefinition(
    name = "shell",
    description = "Run a shell command in a sandbox. Do not use for web scraping.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("command") {
          put("type", "string")
          put("description", "command, run via sh -c")
        }
      }
      putJsonArray("required") { add("command") }
    },
  )

  private val defIcon = def.parameters["properties"]
    ?.jsonObject
    ?.get("command")
    ?.jsonObject
    ?.get("description")
    ?.jsonPrimitive
    ?.contentOrNull
    .orEmpty()
  
  override val icon get() = when {
    def_icon.startsWith("git") -> "G"
    def_icon.startsWith("mkdir") -> "D"
    else -> "🦪"
  }

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val cmd = args["command"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'command'"
    return ctx.exec(listOf("sh", "-c", cmd), null, 30)
  }
}
