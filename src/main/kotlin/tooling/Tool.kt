package ceres.tools

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path

data class ToolDefinition(val name: String, val description: String, val parameters: JsonObject)

class ToolContext(val root: Path) {
  fun resolve(rel: String): Path {
    val p = root.resolve(rel).normalize()
    require(p.startsWith(root)) { "path outside workspace" }
    return p
  }
  fun exec(argv: List<String>, stdin: String?, timeoutSec: Long): String = TODO()
}

interface Tool {
  val def: ToolDefinition
  fun run(args: JsonObject, ctx: ToolContext): String
}

object Tools {
  val list = listOf<Tool>(
    ReadFile(), 
    ListFiles(),
    MakeDir(),
    EditFile(),
    MakeFile(),
    WebSearch(),
    RemoveFile(),
  ).associateBy { it.def.name }
}
