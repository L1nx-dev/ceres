package ceres.core

import kotlinx.serialization.json.*

fun encode(e: Event): String = buildJsonObject {
  when (e) {
    is Event.Delta     -> { put("type", "delta"); put("text", e.text) }
    // is Event.Reasoning -> { put("type", "reasoning"); put("text", e.text) }
    is Event.ToolStart -> { put("type", "tool_start"); put("id", e.id); put("label", e.label); put("icon", e.icon) }
    is Event.ToolEnd   -> { put("type", "tool_end"); put("id", e.id); put("ok", e.ok); put("output", e.output) }
    is Event.Done      -> { put("type", "done"); put("text", e.text) }
    is Event.Error     -> { put("type", "error"); put("message", e.message) }
    Event.Cancelled    -> put("type", "cancelled")
  }
}.toString()

fun decode(s: String): Event {
  val o = Json.parseToJsonElement(s).jsonObject
  fun str(k: String) = o.getValue(k).jsonPrimitive.content
  return when (str("type")) {
    "delta"      -> Event.Delta(str("text"))
    "tool_start" -> Event.ToolStart(str("id"), str("label"), str("icon"))
    "tool_end"   -> Event.ToolEnd(str("id"), o.getValue("ok").jsonPrimitive.boolean, str("output"))
    "done"       -> Event.Done(str("text"))
    "error"      -> Event.Error(str("message"))
    "cancelled"  -> Event.Cancelled
    else         -> Event.Error("unknown event: ${str("type")}")
  }
}
