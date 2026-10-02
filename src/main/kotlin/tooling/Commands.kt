package ceres.main

import ceres.cli.*
import org.jline.utils.InfoCmp

class Commands(private val agent: Agent) {
  private val nameRe = Regex("[A-Za-z][A-Za-z0-9_.-]{0,31}")

  fun run(line: String): String {
    val p = line.trim().split(Regex("\\s+"))
    return when (p[0]) {
      "/chat" -> chat(p.drop(1))
      "/help" -> help(p.drop(1))
      else -> "unknown command: ${p[0]}. use /help to list all available commands."
    }
  }

  private fun chat(a: List<String>): String = when (a.firstOrNull()) {
    "list" -> list()
    "new" -> create(a.getOrNull(1))
    "rename" -> rename(a.getOrNull(1), a.getOrNull(2))
    "switch" -> switch(a.getOrNull(1))
    "remove" -> remove(a.getOrNull(1))
    else -> "usage: /chat list | new [name] | rename <old> <new> | switch <name|id> | remove <name|id>"
  }

  private fun help(a: List<String>): String = when (a.firstOrNull()) {
    "chat" -> "usage: /chat list | new [name] | rename <old> <new> | switch <name|id> | remove <name|id>"
    null -> "commands: \n/chat - manage sessions \n/clear - clears the screen"
    else -> "use /help to list all commands, or /help [command] for detailed instructions."
  }

  private fun list(): String =
    agent.chats().joinToString("\n") {
      val mark = if (it.id == agent.currentId) "*" else " "
      "$mark ${it.id.toString().padEnd(4)} ${(it.name ?: "-").padEnd(16)} " +
        "${it.messages.toString().padStart(4)} msgs  ${it.startedAt.take(16)}  " +
        (it.preview?.replace('\n', ' ') ?: "")
    }.ifEmpty { "(no chats)" }

  private fun create(name: String?): String {
    if (name != null) checkName(name)?.let { return it }
    val id = agent.newChat(name)
    return "chat $id${name?.let { " ($it)" } ?: ""}"
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

  private fun switch(ref: String?): String {
    if (ref == null) return "usage: /chat switch <name|id>"
    val id = agent.resolveChat(ref) ?: return "no such chat: $ref"
    if (id == agent.currentId) return "already in $ref"
    agent.switchChat(id)
    return "switched to $ref"
  }

  private fun remove(ref: String?): String {
    if (ref == null) return "usage: /chat remove <name|id>"
    val id = agent.resolveChat(ref) ?: return "no such chat: $ref"
    if (id == agent.currentId) return "can't remove the current chat; switch away first"
    agent.removeChat(id)
    return "removed $ref"
  }

  /** null = valid */
  private fun checkName(name: String): String? = when {
    !nameRe.matches(name) -> "invalid name: start with a letter; letters, digits, _ . - only (max 32)"
    agent.chatByName(name) != null -> "name already used: $name"
    else -> null
  }
}
