package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.*
import java.time.LocalDateTime

class Recall(private val dir: Path, private val pad: Path) : Tool {
  override val icon get() = "󰡦 "
  override val def = ToolDefinition(
    name = "recall",
    description = "Search memory by keywords. Returns matching files with " +
      "snippets. Use load_memory to read a whole file. Check the memory index first; use this when the " +
      "index doesn't make the right file obvious."+
      "Use this as often as possible",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("query") { put("type", "string"); put("description", "keywords") }
      }
      putJsonArray("required") { add("query") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val words = (args["query"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'query'")
      .lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "error: empty query"

    val files = Files.walk(dir).use { s ->
      s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.toList()
    }
    val hits = files.map { p ->
      val text = Files.readString(p)
      val hay = (dir.relativize(p).toString() + "\n" + text).lowercase()
      Triple(p, text, words.count { it in hay })
    }.filter { it.third > 0 }.sortedByDescending { it.third }.take(5)

    val out = hits.map { (p, text, _) ->
      val lines = text.lines()
      val snippet = lines.drop(1).filter { l -> words.any { it in l.lowercase() } }.take(3)
      "${dir.relativize(p).toString().removeSuffix(".md")} — ${lines.first()}\n" +
        snippet.joinToString("\n") { "  $it" }
    }.toMutableList()

    Sketchpad.read(pad).filter { e -> words.any { it in e.text.lowercase() } }.take(3)
      .forEach { out += "[unsorted ${it.time}] ${it.text.take(200)}" }

    return out.joinToString("\n\n").ifEmpty { "no matches" }
  }
}

class LoadMemory(private val dir: Path) : Tool {
  override val icon get() = "󱤟 "
  override val def = ToolDefinition(
    name = "load_memory",
    description = "Load a memory file by its path from the memory index."+
    "Memories aren't stored in the regular path directory"+
    "Use this as often as possible.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("name") { put("type", "string"); put("description", "path, e.g. example.md") }
      }
      putJsonArray("required") { add("name") }
    },
  )

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val name = args["name"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'name'"
    val p = dir.resolve(name + ".md").normalize()
    if (!p.startsWith(dir) || !Files.isRegularFile(p)) return "error: no such memory file: $name"
    return Files.readString(p).take(16_000)
  }
}
