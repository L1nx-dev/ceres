package ceres.tools

import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.*
import java.nio.charset.StandardCharsets
import java.time.*
import ceres.main.*
import ceres.system.*

private val config = Config()

class WebSearch : Tool {
override val icon get() = "🌐"
override val source = Source.WEB
  override val def = ToolDefinition(
    name = "web_search",
    description = "Search the web and return a list of titles, URLs and snippets.",
    parameters = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("query") {
          put("type", "string")
          put("description", "search query")
        }
        putJsonObject("count") {
          put("type", "integer")
          put("description", "number of results to return (default 5, max 10)")
        }
        putJsonObject("language") {
          put("type", "string")
          put("description", "ISO language code filter, e.g. 'en' (optional)")
        }
      }
      putJsonArray("required") { add("query") }
    },
  )

  private val http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  override fun run(args: JsonObject, ctx: ToolContext): String {
    val query = args["query"]?.jsonPrimitive?.contentOrNull
      ?: return "error: missing 'query'"
    val count = (args["count"]?.jsonPrimitive?.intOrNull ?: 5).coerceIn(1, 10)
    val lang = args["language"]?.jsonPrimitive?.contentOrNull

    val base = config.searXNG.trim().trimEnd('/')
    if (base.isEmpty()) return "error: config.searXNG is not set"

    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
    val uri = URI.create(buildString {
      append("$base/search?q=$encoded&format=json")
      if (lang != null) append("&language=${URLEncoder.encode(lang, StandardCharsets.UTF_8)}")
    })

    val req = HttpRequest.newBuilder(uri)
      .header("Accept", "application/json")
      .header("User-Agent", "ceres-agent/1.0")
      .timeout(Duration.ofSeconds(10))
      .GET()
      .build()

    val resp = try {
      http.send(req, HttpResponse.BodyHandlers.ofString())
    } catch (e: HttpTimeoutException) {
      return "error: search timed out after 10s"
    } catch (e: Exception) {
      return "error: search request failed: ${e.message}"
    }

    when (resp.statusCode()) {
      200 -> {}
      403 -> return "error: SearXNG at $base has JSON format disabled (search.formats in settings.yml)"
      429 -> return "error: SearXNG rate-limited the request; retry once."
      in 500..599 -> return "error: SearXNG server error (HTTP ${resp.statusCode()})"
      else -> return "error: SearXNG returned HTTP ${resp.statusCode()}: " +
                      resp.body().take(300).replace(Regex("""\s+"""), " ")
    }

    val results = try {
      Json.parseToJsonElement(resp.body())
        .jsonObject["results"]?.jsonArray
        ?: return "error: unexpected SearXNG response format: " +
                  resp.body().take(300).replace(Regex("""\s+"""), " ")
    } catch (e: Exception) {
      return "error: failed to parse SearXNG response: ${e.message}"
    }

    if (results.isEmpty()) return "no results"

    return buildString {
      results.take(count).forEachIndexed { i, r ->
        val obj = r.jsonObject
        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "(untitled)"
        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
        val snippet = obj["content"]?.jsonPrimitive?.contentOrNull
        appendLine("${i + 1}. $title")
        appendLine("   $url")
        if (snippet != null) appendLine("   $snippet")
      }
    }
  }
}
