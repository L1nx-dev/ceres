package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.attribute.*
import java.nio.file.*

class EditFile : Tool {
  override val def = ToolDefinition(
    name = "edit_file",
    description = "Replace one exact occurrence of old_str with new_str in a file. " +
      "old_str must match exactly once, including whitespace; include surrounding lines to make it unique. " +
      "Empty new_str deletes the match. Read the file using ls first.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") { put("type", "string"); put("description", "relative file path") }
        putJsonObject("old_str") { put("type", "string"); put("description", "exact text to replace") }
        putJsonObject("new_str") { put("type", "string"); put("description", "replacement text") }
      }
      putJsonArray("required") { add("path"); add("old_str"); add("new_str") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'path'"
    val old = args["old_str"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'old_str'"
    val new = args["new_str"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'new_str'"
    if (old.isEmpty()) return "error: old_str is empty"

    val file = ctx.resolve(rel)
    val text = Files.readString(file)
    val i = text.indexOf(old)
    if (i < 0) return "error: old_str not found in $rel"
    if (text.indexOf(old, i + 1) >= 0) return "error: old_str matches more than once; add surrounding context"

    Files.writeString(file, text.substring(0, i) + new + text.substring(i + old.length))
    return "ok: edited $rel"
  }
}
