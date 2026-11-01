package ceres.tools

import kotlinx.serialization.json.*

class Shell : Tool {
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

  private class Rule(val cmd: String, val icon: String, val args: Regex? = null)

  // first matching rule wins
  private val rules = listOf(
    Rule("rm",      "󰉘 ", Regex("""-\w*[rR]|--recursive""")),
    Rule("cp",      "󰉕 ", Regex("""-\w*[rR]|--recursive""")),
    Rule("rm",      " "),
    Rule("cat",     " "),
    Rule("git",     " "),
    Rule("mkdir",   "󰉗 "),
    Rule("ls",      "󰥨 "),
    Rule("tar",     " "),
    Rule("zip",     " "),
    Rule("unzip",   " "),
    Rule("find",    " "),
    Rule("grep",    " "),
    Rule("mv",      "󰦻 "),
    Rule("cp",      " "),
    Rule("curl",    "󰖟 "),
    /*   LANGUAGES   */
    Rule("python",  " "),
    Rule("python3", " "),
    Rule("uv",      " "),
    Rule("pip",     " "),
    Rule("lua",     "󰢱 "),
    Rule("luarocks","󰢱 "),
    Rule("kotlinc", " "),
    Rule("javac",   " "),
    Rule("gcc",     " "),
    Rule("g++",     " "),
    Rule("rustc",   " "),
    Rule("cargo",   " "),
    Rule("node",    " "), // fuck this language
    Rule("npm",     " "),
    Rule("npx",     " "),
    Rule("deno",    " "),
  )

  private val splitter = Regex("""&&|\|\||;|\|""")
  private val ws = Regex("""\s+""")

  override fun iconFor(args: JsonObject): String {
    val cmd = args["command"]?.jsonPrimitive?.contentOrNull ?: return "🦪"
    for (segment in cmd.split(splitter)) {
      val parts = segment.trim().split(ws, limit = 2)
      val name = parts[0]
      val rest = parts.getOrElse(1) { "" }
      rules.firstOrNull { it.cmd == name && (it.args?.containsMatchIn(rest) ?: true) }
        ?.let { return it.icon }
    }
    return "🦪"
  }

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val cmd = args["command"]?.jsonPrimitive?.contentOrNull ?: return "error: missing 'command'"
    return ctx.exec(listOf("sh", "-c", cmd), null, 30)
  }
}
