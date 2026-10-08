package ceres.main

import ceres.system.*
import ceres.core.*
 
enum class Role(val wire: String) {
  SYSTEM("system"), USER("user"), ASSISTANT("assistant")
}
 
enum class BlockType { CHAT, FILE_COPY, STUB, PINNED }
 
enum class Source { USER, MODEL, TOOL, WEB, SYSTEM }
 
data class ToolCall(val id: String, val name: String, val arguments: String)
 
sealed class Msg {
  abstract val type: BlockType
  abstract val source: Source
  abstract val ref: String? // file path / hash the message is about; null = chat
  var id: Long? = null // set by Store 

  data class Text(
    val role: Role,
    val content: String,
    override val type: BlockType = BlockType.CHAT,
    override val source: Source = Source.USER,
    override val ref: String? = null,
  ) : Msg()
 
  data class ToolCalls(
    val calls: List<ToolCall>,
    val content: String? = null,
    override val type: BlockType = BlockType.CHAT,
    override val source: Source = Source.MODEL,
    override val ref: String? = null,
  ) : Msg()
 
  data class ToolResult(
    val callId: String,
    val content: String,
    val ok: Boolean = true,
    override val type: BlockType = BlockType.CHAT,
    override val source: Source = Source.TOOL,
    override val ref: String? = null,
  ) : Msg()
}

