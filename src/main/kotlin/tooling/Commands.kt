package ceres.main

import ceres.main.*

fun checkChatName(name: String): String? = when {
  name.isBlank() -> "name is empty"
  name.length > 64 -> "name too long (max 64)"
  name.any { it.isISOControl() } -> "name contains control characters"
  name.all { it.isDigit() } -> "name can't be only digits (that is how ids are written)"
  else -> null
}

class Commands(
  private val agent: Agent,
  private val dreamer: Dreamer,
  private val store: Store
) {
  class CmdResult(val text: String, val chat: Long? = null)

  fun checkName(name: String) = checkChatName(name)

  private val usage =
    "usage: /chat list | new [name] | rename <id|name> [new name] | switch <id|name> | remove <id|name>"

  fun run(line: String, cur: Long): CmdResult {
    val p = line.trim().split(Regex("\\s+"), limit = 2)
    val rest = p.getOrElse(1) { "" }
    return when (p[0]) {
      "/chat" -> chat(rest, cur)
      "/help" -> CmdResult(help(rest))
      "/dream" -> CmdResult(dreamer.dream())
      else -> CmdResult("unknown command: ${p[0]}. use /help to list all available commands.")
    }
  }

  private fun chat(rest: String, cur: Long): CmdResult {
    val p = rest.trim().split(Regex("\\s+"), limit = 2)
    val arg = p.getOrElse(1) { "" }.trim()
    return when (p[0]) {
      "list" -> CmdResult(list(cur))
      "new" -> create(arg.ifEmpty { null })
      "rename" -> CmdResult(rename(arg))
      "switch" -> switch(arg, cur)
      "remove" -> CmdResult(remove(arg, cur))
      else -> CmdResult(usage)
    }
  }

  private fun help(a: String): String = when (a.trim().substringBefore(' ')) {
    "chat" -> usage
    "" -> "commands:\n/chat - manage chats\n/dream - sort notes into memory\n/clear - clears the screen"
    else -> "use /help to list all commands, or /help [command] for detailed instructions."
  }

  private fun list(cur: Long): String =
    agent.chats(cur).joinToString("\n") {
      val mark = if (it.id == cur) "*" else " "
      "$mark ${it.id.toString().padEnd(4)} ${(it.name ?: "-").padEnd(16)} " +
        "${it.messages.toString().padStart(4)} msgs  ${it.startedAt.take(16)}  " +
        (it.preview?.replace('\n', ' ') ?: "")
    }.ifEmpty { "(no chats)" }

  private fun find(ref: String): Result<Long> {
    val ids = store.resolve(ref)
    return when (ids.size) {
      0 -> Result.failure(NoSuchElementException("no such chat: $ref"))
      1 -> Result.success(ids[0])
      else -> Result.failure(IllegalStateException(
        "ambiguous: ${ids.size} chats named \"$ref\" (ids ${ids.joinToString()}); use an id"))
    }
  }

  private fun create(name: String?): CmdResult {
    if (name != null) checkChatName(name)?.let { return CmdResult(it) }
    val id = agent.createChat(name)
    return CmdResult("chat $id${name?.let { " ($it)" } ?: ""}", id)
  }

  private fun switch(ref: String, cur: Long): CmdResult {
    if (ref.isEmpty()) return CmdResult("usage: /chat switch <id|name>")
    val id = find(ref).getOrElse { return CmdResult(it.message.orEmpty()) }
    if (id == cur) return CmdResult("already in chat $id")
    return CmdResult("switched to chat $id", id)
  }

  private fun remove(ref: String, cur: Long): String {
    if (ref.isEmpty()) return "usage: /chat remove <id|name>"
    val id = find(ref).getOrElse { return it.message.orEmpty() }
    if (id == cur) return "can't remove the current chat; switch away first"
    agent.removeChat(id)
    return "removed chat $id"
  }

  private fun rename(arg: String): String {
    val p = arg.split(Regex("\\s+"), limit = 2)
    if (p[0].isEmpty()) return "usage: /chat rename <id|name> [new name]"
    val name = p.getOrElse(1) { "" }.trim().ifEmpty { null }
    val id = find(p[0]).getOrElse { return it.message.orEmpty() }
    if (name != null) checkChatName(name)?.let { return it }
    store.rename(id, name)
    return if (name == null) "cleared name of chat $id" else "renamed chat $id -> $name"
  }
}
