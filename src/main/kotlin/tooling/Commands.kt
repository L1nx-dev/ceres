package ceres.main

import ceres.system.*
import ceres.cli.*
import org.jline.utils.InfoCmp

class Commands(private val agent: Agent, private val dreamer: Dreamer) {
  private val nameRe = Regex("[A-Za-z][A-Za-z0-9_.-]{0,31}")

  class CmdResult(val text: String, val chat: Long? = null)

  fun run(line: String, cur: Long): CmdResult {
    val p = line.trim().split(Regex("\\s+"))
    return when (p[0]) {
      "/chat" -> chat(p.drop(1), cur)
      "/help" -> CmdResult(help(p.drop(1)))
      "/dream" -> CmdResult(dreamer.dream())
      else -> CmdResult("unknown command: ${p[0]}. use /help to list all available commands.")
    }
  }

  private val usage = "usage: /chat list | new [name] | rename <old> <new> | switch <name|id> | remove <name|id>"

  private fun chat(a: List<String>, cur: Long): CmdResult = when (a.firstOrNull()) {
    "list" -> CmdResult(list(cur))
    "new" -> create(a.getOrNull(1))
    "rename" -> CmdResult(rename(a.getOrNull(1), a.getOrNull(2)))
    "switch" -> switch(a.getOrNull(1), cur)
    "remove" -> CmdResult(remove(a.getOrNull(1), cur))
    else -> CmdResult(usage)
  }

  private fun help(a: List<String>): String = when (a.firstOrNull()) {
    "chat" -> "usage: /chat list | new [name] | rename <old> <new> | switch <name|id> | remove <name|id>"
    null -> "commands: \n/chat - manage sessions \n/clear - clears the screen"
    else -> "use /help to list all commands, or /help [command] for detailed instructions."
  }

  private fun list(cur: Long): String =
    agent.chats(cur).joinToString("\n") {
      val mark = if (it.id == cur) "*" else " "
      "$mark ${it.id.toString().padEnd(4)} ${(it.name ?: "-").padEnd(16)} " +
        "${it.messages.toString().padStart(4)} msgs  ${it.startedAt.take(16)}  " +
        (it.preview?.replace('\n', ' ') ?: "")
    }.ifEmpty { "(no chats)" }

  private fun create(name: String?): CmdResult {
    if (name != null) checkName(name)?.let { return CmdResult(it) }
    val id = agent.createChat(name)
    return CmdResult("chat $id${name?.let { " ($it)" } ?: ""}", id)
  }

  private fun switch(ref: String?, cur: Long): CmdResult {
    if (ref == null) return CmdResult("usage: /chat switch <name|id>")
    val id = agent.resolveChat(ref) ?: return CmdResult("no such chat: $ref")
    if (id == cur) return CmdResult("already in $ref")
    return CmdResult("switched to $ref", id)
  }

  private fun remove(ref: String?, cur: Long): String {
    if (ref == null) return "usage: /chat remove <name|id>"
    val id = agent.resolveChat(ref) ?: return "no such chat: $ref"
    if (id == cur) return "can't remove the current chat; switch away first"
    agent.removeChat(id)
    return "removed $ref"
  }

  fun checkName(name: String): String? = when {
    !nameRe.matches(name) -> "invalid name: start with a letter; letters, digits, _ . - only (max 32)"
    agent.chatByName(name) != null -> "name already used: $name"
    else -> null
  }

  private fun rename(old: String?, new: String?): String {
    if (old == null || new == null) return "usage: /chat rename <old> <new>"
    val id = agent.resolveChat(old) ?: return "no such chat: $old"
    val owner = agent.chatByName(new)
    if (owner == id) return "unchanged"
    checkName(new)?.let { return it }
    agent.renameChat(id, new)
    return "renamed $old -> $new"
  }
}
