package ceres.main

import java.net.http.*
import java.net.URI
import java.time.Duration
import kotlinx.serialization.json.*

data class Reply(
  val content: String?,    // null when the model answered with a tool call instead of text
  val promptTokens: Int?,  // null if the server did not send a usage object
)

class InterfaceV1(val config: Config) {
  val client = HttpClient.newHttpClient()
  val body = HttpRequest.BodyPublishers.ofString(message)
  
  fun request(messages: List<Pair<String, String>>): Reply {
    val builder = HttpRequest.newBuilder(URI.create(config.baseUrl + "/v1/chat/completions"))
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(config.timeout))

    val json = buildJsonObject {
      put("model", config.model)
      putJsonArray("messages") {
        for ((role, text) in messages) {
          addJsonObject {
            put("role", role)
            put("content", text)
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
    val message = root.getValue("choices")   // getValue names the missing key in its error
      .jsonArray[0]
      .jsonObject.getValue("message")
      .jsonObject


    println(content)


  }
}
