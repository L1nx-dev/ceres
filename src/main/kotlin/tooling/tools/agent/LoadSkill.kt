package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*

class LoadSkill(private val skills: Skills) : Tool {
  override val icon get() = " "
  override val def = ToolDefinition(
    name = "skill",
    description = "Load a skill from the skill index. Without 'file' it returns the skill's SKILL.md " +
      "and the list of extra files in that skill. Pass 'file' (relative path, e.g. references/modules-api.md) " +
      "to load one of them. Skills are not in the workspace; use this tool, not read_file.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("name") { put("type", "string"); put("description", "skill name from the index") }
        putJsonObject("file") { put("type", "string"); put("description", "optional path inside the skill, default SKILL.md") }
      }
      putJsonArray("required") { add("name") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val name = args["name"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'name'"
    val skill = skills.find(name) ?: return "error: no such skill: $name"
    val file = args["file"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    val path = if (file == null) skill.dir.resolve("SKILL.md")
               else skills.resolve(skill, file) ?: return "error: no such file in $name: $file"

    val text = Files.readString(path)
    val body = if (text.length > 16_000) text.take(16_000) + "\n[truncated]" else text
    if (file != null) return body

    val extra = skills.files(skill)
    return if (extra.isEmpty()) body
           else body + "\n\n# Files in this skill (load with the 'file' argument)\n" + extra.joinToString("\n")
  }
}
