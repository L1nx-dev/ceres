package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*
import java.time.LocalDateTime

class LoadSkill(private val dir: Path) : Tool {
  override val icon get() = "📖"
  override val def = ToolDefinition(
    name = "skill",
    description = "Load a skill file by its path from the skill index."+
    "Skills aren't stored in the regular path directory"+
    "Use this as often as possible.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("name") { put("type", "string"); put("description", "skill name") }
      }
      putJsonArray("required") { add("name") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val name = args["name"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'name'"
    val p = dir.resolve(name + ".md").normalize()
    if (!p.startsWith(dir) || !Files.isRegularFile(p)) return "error: no such skill: $name; create or download it first"
    return Files.readString(p).take(16_000)
  }
}
