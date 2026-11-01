package ceres.main

import ceres.tools.*
import kotlinx.serialization.json.*
import java.nio.file.*
import kotlin.io.path.div
import ceres.system.*
import ceres.core.*

import java.nio.file.*
import kotlin.io.path.div

object MemoryIndex {
  fun build(dir: Path): String {
    val files = Files.walk(dir).use { s ->
      s.iterator().asSequence()
        .filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
        .sorted().toList()
    }
    return files.joinToString("\n") { p ->
      val desc = Files.newBufferedReader(p).use { it.readLine() } ?: ""
      "${dir.relativize(p)} — $desc"
    }.ifEmpty { "(empty)" }
  }
}

class Dreamer(config: Config, private val xdg: Xdg) {
  private val llm: ApiV1 = ApiV1(config) { rel -> runCatching { Images.dataUrl(ctx.resolve(rel)) }.getOrNull() }
  private val seen = mutableSetOf<String>()
  private val ctx = ToolContext(
    root = xdg.memory.toRealPath(),
    config = config,
    seen = seen,
    ask = { msgs ->
      llm.request(msgs, emptyList(), think = false).content.orEmpty()
    },
  )
  private val tools = listOf<Tool>(ReadFile(), ListFiles(), SaveMemory(), WebSearch())
    .associateBy { it.def.name }

  fun dream(): String {
    val pad = xdg.config / "SKETCHPAD.md"
    val entries = Sketchpad.read(pad)
    if (entries.isEmpty()) return "nothing to process"

    val failed = mutableListOf<SketchEntry>()
    for (e in entries) {
      runCatching { process(e) }.onFailure {
        println("dream failed [${e.time}]: ${it.javaClass.name}: ${it.message}")
        println(it.stackTrace.take(5).joinToString("\n") { f -> "  at $f" })
        failed += e
      }
    }
    val done = entries - failed.toSet()
    Files.writeString(xdg.config / "SKETCHPAD.archive.md", fmt(done),
      StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    Files.writeString(pad, fmt(failed))
    return "processed ${done.size}, failed ${failed.size}"
  }

  private fun fmt(es: List<SketchEntry>) = es.joinToString("") { "## ${it.time}\n${it.text}\n\n" }

  private fun process(e: SketchEntry) {
    val history = mutableListOf<Msg>(
      Msg.Text(Role.SYSTEM, PROMPT),
      Msg.Text(Role.USER, "Memory index:\n${MemoryIndex.build(xdg.memory)}\n\nNote [${e.time}]:\n${e.text}"),
    )
    repeat(10) {
      val reply = llm.request(history, tools.values.map { it.def })
      val calls = reply.toolCalls
      if (calls.isNullOrEmpty()) {
        println("dream [${e.time}]: ${reply.content.orEmpty().take(120)}")
        return
      }
      history += Msg.ToolCalls(calls, reply.content)
      for (c in calls) history += call(c)
    }
    error("step limit")
  }

  private fun call(c: ToolCall): Msg.ToolResult {
    val tool = tools[c.name] ?: return Msg.ToolResult(c.id, "error: unknown tool ${c.name}", ok = false)
    val r = runCatching { tool.run(Json.parseToJsonElement(c.arguments).jsonObject, ctx) }
      .getOrElse { "error: ${it.message}" }
    return Msg.ToolResult(c.id, r, ok = !r.startsWith("error:"), source = tool.source)
  }

  companion object {
    val PROMPT = """
You maintain the long-term memory of an AI assistant.
You are given ONE raw note and an index of the existing memory files (path — description). 
Decide what to do with the note, do it with your tools, then reply with one short line saying what you did.

Options, in order of preference:
1. Discard it. Do this for anything temporary (current task state, one-off commands), trivial, vague, or already recorded. Check existing files if a particulat piece of information hasnt been previously recorded.
2. Merge it into an existing file. ALWAYS read the file first, then use save_memory. If the note contradicts a line in the file, replace the old line with the new fact. Do not keep both.
3. Create a new file, only if no existing file covers the subject.

Rules:
- Always read a file before editing it. Never edit from the index alone.
- One subject per file, named by topic (nixos.md, user-preferences.md), never by date.
- Write facts as short, self-contained lines that make sense without the conversation. Drop timestamps unless the date matters.
- The description is one line saying what the file contains and when to read it. Update it whenever the content changes meaning. A vague description makes the file unfindable.
- Always use web_search to verify data, if aplicable.
- Do not invent or infer facts the note does not state.
- ALWAYS READ THE FILES AND VERIFY VAGUE FACTS.


# Example:
Summary

Building a custom IDE-style Neovim config from scratch to replace NvChad, symlinked from the Nix flake repo

Details
Building a from-scratch Neovim config to replace NvChad, run in parallel via NVIM_APPNAME=nvim-new (alias/appname), symlinked from ~/Nix/home/vim via home-manager xdg.configFile
Directory layout: home/vim/{init.lua, colors/, lua/config/{options,keymaps,autocmds,lazy}.lua, lua/plugins/{treesitter,lsp,auto-completion}.lua}
Plugin manager: lazy.nvim, bootstrapped via standard clone-if-missing snippet, spec = { { import = "plugins" } }
Colorscheme: wrote a custom standalone theme in colors/ transcribed from NvChad's base46 flexoki theme (including its polish_hl overrides), rather than using a third-party flexoki plugin
Treesitter: nvim-treesitter pinned to branch = "master" (old config-module API) since default main branch is an incompatible rewrite; ensure_installed includes lua, nix, bash, vim, vimdoc, markdown, json, yaml, python, kotlin, java, cpp, c, c_sharp, typescript, javascript
LSP: mason.nvim + mason-lspconfig.nvim + nvim-lspconfig, servers configured: pyright, lua_ls, kotlin_language_server, jdtls, clangd, csharp_ls, ts_ls, html, cssls, plus manually-configured qmlls (not mason-managed, comes from Qt)
Moved several LSP servers off mason onto nixpkgs (home.packages) instead, since mason install of npm/dotnet-based servers failed with npm/dotnet not found in PATH (pyright confirmed moved to home.packages and working)
Completion: settled on nvim-cmp (not blink.cmp) with cmp-nvim-lsp, cmp-buffer, cmp-path, cmp-nvim-lsp-signature-help, LuaSnip, cmp_luasnip, friendly-snippets, lspkind.nvim, added bordered completion/documentation windows to match NvChad's doc-preview pane </content>

# End of the example
""".trim()
  }
}
