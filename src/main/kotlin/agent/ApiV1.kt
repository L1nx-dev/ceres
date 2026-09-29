package ceres.main

import java.net.http.*
import java.net.URI
import java.time.Duration
import kotlinx.serialization.json.*

data class Reply(
  val content: String?,
  val promptTokens: Int?,
  val toolCalls: List<ToolCall>?,
)

data class ToolCall(val id: String, val name: String, val arguments: String)

data class ToolDefinition(
  val name: String,
  val description: String,
  val parameters: JsonObject,
)

class ApiV1(val config: Config) {
  val client = HttpClient.newHttpClient()
  
  fun request(messages: List<Msg>): Reply {
    val builder = HttpRequest.newBuilder(URI.create(config.baseUrl + "/v1/chat/completions"))
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(config.timeout))

      val json = buildJsonObject {
        put("model", config.model)
        putJsonArray("messages") {
          for (msg in messages) {
            addJsonObject {
              when (msg) {
                is Msg.Text -> {
                  put("role", msg.role)
                  put("content", msg.content)
                }
                is Msg.ToolCalls -> {
                  put("role", "assistant")
                  put("content", JsonNull)
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
                is Msg.ToolResult -> {
                  put("role", "tool")
                  put("tool_call_id", msg.callId)
                  put("content", msg.content)
                }
              }
            }
          }
        }
      }
     
     
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
}
