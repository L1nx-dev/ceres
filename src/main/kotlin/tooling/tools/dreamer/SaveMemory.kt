package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.* 

class SaveMemory : Tool {
  override val icon get() = "🧠"
  override val def = ToolDefinition(
    name = "save_memory",
    description = "Create or edit a memory file. The description is stored as the file's first line and is " +
      "required for new files; update it whenever the content changes meaning. " +
      "Empty old_str appends to the file body. Otherwise old_str must match exactly once in the body.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") { put("type", "string"); put("description", "relative file path, e.g. example") }
        putJsonObject("old_str") { put("type", "string"); put("description", "exact text to replace; empty to append") }
        putJsonObject("new_str") { put("type", "string"); put("description", "replacement or appended text") }
        putJsonObject("description") { put("type", "string"); put("description", "one-line summary of the file") }
      }
      putJsonArray("required") { add("path"); add("new_str") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val rel = args["path"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'path'"
    val old = args["old_str"]?.jsonPrimitive?.contentOrNull ?: ""
    val new = args["new_str"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'new_str'"
    val desc = args["description"]?.jsonPrimitive?.contentOrNull
      ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }

    val file = ctx.resolve(rel + ".md")
    val exists = Files.exists(file)
    if (!exists && desc == null) return "error: description required for new file"

    val text = if (exists) Files.readString(file) else ""
    val nl = text.indexOf('\n')
    val oldDesc = if (nl < 0) text else text.substring(0, nl)
    val body = if (nl < 0) "" else text.substring(nl + 1)

    val newBody = if (old.isEmpty()) {
      body + new
    } else {
      val i = body.indexOf(old)
      if (i < 0) return "error: old_str not found in $rel"
      if (body.indexOf(old, i + 1) >= 0) return "error: old_str matches more than once; add surrounding context"
      body.substring(0, i) + new + body.substring(i + old.length)
    }

    file.parent?.let { Files.createDirectories(it) }
    Files.writeString(file, (desc ?: oldDesc) + "\n" + newBody)
    return "ok: saved $rel"
  }
}
