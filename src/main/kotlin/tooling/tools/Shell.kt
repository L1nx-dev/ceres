package ceres.tools

import kotlinx.serialization.json.*

class Shell : Tool {
  override val def = ToolDefinition(
    name = "shell",
    description = "Run a shell command in a sandbox. Working dir is the workspace. No network.",
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

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val cmd = args["command"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'command'"
    return ctx.exec(listOf("sh", "-c", cmd), null, 30)
  }
}
