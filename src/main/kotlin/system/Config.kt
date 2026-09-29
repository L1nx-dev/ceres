package ceres.main

import kotlinx.serialization.json.*
import kotlin.io.path.*

class Config(xdg: Xdg = Xdg()) {

  private val root: JsonObject =
    runCatching {
      Json.parseToJsonElement((xdg.config / "config.json").readText()).jsonObject
    }.getOrDefault(JsonObject(emptyMap()))

  private inner class Entry<T>(
    key: String,
    default: T,
    read: (JsonElement) -> T,
  ) {

    val value: T = root[key]
      ?.let { runCatching { read(it) }.getOrNull() }
      ?: default
  }

  val baseUrl         = Entry("baseUrl", "http://localhost:8080")     { it.jsonPrimitive.content }.value
  val apiKey          = Entry("apiKey", "")                           { it.jsonPrimitive.content }.value
  val model           = Entry("model", "default")                     { it.jsonPrimitive.content }.value
  val timeout         = Entry("timeout", 180)                         { it.jsonPrimitive.long }.value

  val workDir         = Entry("workDir", "")                          { it.jsonPrimitive.content }.value
  val maxToolDepth    = Entry("maxToolDepth", 10)                     { it.jsonPrimitive.int }.value

  // template
  // val someString = Entry("some_string", "default") { it.jsonPrimitive.content }.value
  // val someInt    = Entry("some_int", 0)            { it.jsonPrimitive.int }.value
  // val someFlag   = Entry("some_flag", false)       { it.jsonPrimitive.boolean }.value
  // val someList   = Entry("some_list", listOf<String>()) { it.jsonArray.map { e -> e.jsonPrimitive.content } }.value
}
