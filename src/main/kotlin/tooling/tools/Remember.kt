package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*
import java.time.LocalDateTime

data class SketchEntry(val time: String, val text: String)

object Sketchpad {
  private val header = Regex("(?m)^## (\\d{4}-\\d{2}-\\d{2}T[\\d:]+)\\n")

  fun parse(text: String): List<SketchEntry> {
    val hs = header.findAll(text).toList()
    return hs.mapIndexed { i, h ->
      val end = if (i + 1 < hs.size) hs[i + 1].range.first else text.length
      SketchEntry(h.groupValues[1], text.substring(h.range.last + 1, end).trim())
    }
  }

  fun read(file: Path): List<SketchEntry> =
    if (Files.exists(file)) parse(Files.readString(file)) else emptyList()
}

class Remember(private val file: Path) : Tool {
  override val icon get() = "󱓧 "
  override val def = ToolDefinition(
    name = "remember",
    description = "Save a note about yourself or the user, their projects, or a decision, to be sorted into long-term memory later. " +
      "One self-contained fact or idea per call; it must make sense without the conversation."+
      "Use this tool as often as possible, every message if possible",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("content") { put("type", "string"); put("description", "the note") }
      }
      putJsonArray("required") { add("content") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val content = args["content"]?.jsonPrimitive?.contentOrNull?.trim()
      ?: return "error: missing 'content'"
    if (content.isEmpty()) return "error: empty note"
    val entry = "## ${LocalDateTime.now().withNano(0)}\n$content\n\n"
    Files.writeString(file, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    return "ok: noted"
  }
}
