package main.ceres

import java.net.http.*
import java.net.URI
import java.time.Duration
import kotlinx.serialization.json.*

class LlmClient(val config: Config, message: String = "test") {
  val client = HttpClient.newHttpClient()
  val body = HttpRequest.BodyPublishers.ofString(message)
  
  fun request() {
    val input = HttpRequest.newBuilder(URI.create(config.baseUrl + "/v1/chat/completions")) // baseUrl = "http://localhost:8080"
      .header("Content-Type", "application/json")
      .timeout(Duration.ofSeconds(config.timeout)) // this is in seconds, right? aslo long is expected, how do i convert Int to Long?
      .POST(body)
      .build()

    val output = client.send(input, HttpResponse.BodyHandlers.ofString())

    when (output.statusCode) { // lsp is throwing a watning here `Function invocation 'statusCode()' expected [FUNCTION_CALL_EXPECTED]`
      //TODO: find error codes
    }

    val response = Json.parseToJsonElement(body)
      .jsonObject
      .jsonArray[0]

  }
}
