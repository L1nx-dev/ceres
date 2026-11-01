package ceres.main

import java.net.http.*
import java.net.URI
import java.time.Duration
import kotlinx.serialization.json.*
import ceres.system.*
import ceres.core.*


import ceres.tools.ToolDefinition

data class Reply(
  val content: String?,
  val promptTokens: Int?,
  val toolCalls: List<ToolCall>?,
)

class ApiV1(val config: Config, private val imageUrl: (String) -> String?) {
  val client: HttpClient = HttpClient.newHttpClient()

  private fun JsonObjectBuilder.imagePart(path: String) {
    val url = imageUrl(path)
    if (url == null) { put("type", "text"); put("text", "[image unavailable: $path]") }
    else { put("type", "image_url"); putJsonObject("image_url") { put("url", url) } }
  }

  private fun body(messages: List<Msg>,
    tools: List<ToolDefinition>,
    think: Boolean,
    model: String,
    stream: Boolean
  ): JsonObject = buildJsonObject {
    put("model", model)
    if (stream) {
      put("stream", true)
      putJsonObject("stream_options") { put("include_usage", true) }
    }
    if (!think) putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
    putJsonArray("messages") {
      for (msg in messages) {
        addJsonObject {
          when (msg) {
            is Msg.Text -> {
              put("role", msg.role.wire)
              put("content", msg.content)
            }
            is Msg.ToolCalls -> {
              put("role", "assistant")
              put("content", msg.content)
              putJsonArray("tool_calls") {
                for (call in msg.calls) {
                  addJsonObject {
                    put("id", call.id)
                    put("type", "function")
                    putJsonObject("function") {
                      put("name", call.name)
                      put("arguments", call.arguments)
                    }
                  }
                }
              }
            }
            is Msg.Image -> {
              put("role", "user")
              putJsonArray("content") { addJsonObject { imagePart(msg.path) } }
            }
            is Msg.ToolResult -> {
              put("role", "tool")
              put("tool_call_id", msg.callId)
              if (msg.images.isEmpty()) put("content", msg.content)
              else putJsonArray("content") {
                addJsonObject { put("type", "text"); put("text", msg.content) }
                for (p in msg.images) addJsonObject { imagePart(p) }
              }
            }
          }
        }
      }
    }
    if (tools.isNotEmpty()) putJsonArray("tools") {
      for (t in tools) addJsonObject {
        put("type", "function")
        putJsonObject("function") {
          put("name", t.name)
          put("description", t.description)
          put("parameters", t.parameters)
        }
      }
    }
  }
  
  fun request(
    messages: List<Msg>,
    tools: List<ToolDefinition>,
    think: Boolean = true,
    model: String = config.model,
  ): Reply {
    val builder = HttpRequest.newBuilder(URI.create(config.baseUrl + "/v1/chat/completions"))
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(config.timeout))

      val json = body(messages, tools, think, model, false)
     
     
    if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
     
    val input = builder.POST(HttpRequest.BodyPublishers.ofString(json.toString())).build()
    val output = client.send(input, HttpResponse.BodyHandlers.ofString())
     
    check(output.statusCode() == 200) { "HTTP ${output.statusCode()}: ${output.body()}" }

    // ==== JSON ====

    val root = Json.parseToJsonElement(output.body()).jsonObject
    val message = root.getValue("choices")
      .jsonArray[0]
      .jsonObject.getValue("message")
      .jsonObject

    val content = message["content"]?.jsonPrimitive?.contentOrNull
    val tokens = root["usage"]?.jsonObject?.get("prompt_tokens")?.jsonPrimitive?.intOrNull
    val requestedCalls = message["tool_calls"]?.jsonArray?.map { callJson ->
      val callObject = callJson.jsonObject
      val function = callObject.getValue("function").jsonObject
      ToolCall(
        id = callObject.getValue("id").jsonPrimitive.content,
        name = function.getValue("name").jsonPrimitive.content,
        arguments = function.getValue("arguments").jsonPrimitive.content,
      )
    }
    return Reply(content = content, promptTokens = tokens, toolCalls = requestedCalls)
  }

  fun stream(
    messages: List<Msg>,
    tools: List<ToolDefinition>,
    cancel: Cancel,
    onDelta: (String) -> Unit
  ): Reply {
    val builder = HttpRequest.newBuilder(URI.create(config.baseUrl + "/v1/chat/completions"))
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(config.timeout))
    if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
    val json = body(messages, tools, true, config.model, true)
    val req = builder.POST(HttpRequest.BodyPublishers.ofString(json.toString())).build()

    val res = client.send(req, HttpResponse.BodyHandlers.ofInputStream())
    check(res.statusCode() == 200) { "HTTP ${res.statusCode()}: ${res.body().readBytes().decodeToString()}" }
    cancel.setHook { runCatching { res.body().close() } }

    val text = StringBuilder()
    val ids = sortedMapOf<Int, String>()
    val names = sortedMapOf<Int, String>()
    val args = sortedMapOf<Int, StringBuilder>()
    var tokens: Int? = null

    res.body().bufferedReader().use { r ->
      for (line in r.lineSequence()) {
        if (!line.startsWith("data:")) continue
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") break
        val root = Json.parseToJsonElement(data).jsonObject
        root["usage"]?.jsonObject?.get("prompt_tokens")?.jsonPrimitive?.intOrNull?.let { tokens = it }
        val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
          ?.get("delta")?.jsonObject ?: continue
        delta["content"]?.jsonPrimitive?.contentOrNull?.let { text.append(it); onDelta(it) }
        delta["tool_calls"]?.jsonArray?.forEach { t ->
          val o = t.jsonObject
          val i = o["index"]?.jsonPrimitive?.int ?: 0
          o["id"]?.jsonPrimitive?.contentOrNull?.let { ids[i] = it }
          o["function"]?.jsonObject?.let { f ->
            f["name"]?.jsonPrimitive?.contentOrNull?.let { names[i] = it }
            f["arguments"]?.jsonPrimitive?.contentOrNull
              ?.let { args.getOrPut(i) { StringBuilder() }.append(it) }
          }
        }
      }
    }
    cancel.setHook(null)
    val calls = ids.keys.map { ToolCall(ids[it]!!, names[it] ?: "", args[it]?.toString() ?: "{}") }
    return Reply(text.toString().ifEmpty { null }, tokens, calls.ifEmpty { null })
  }
}
