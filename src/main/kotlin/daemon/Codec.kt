package ceres.main

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
