package ceres.main

import ceres.core.Cancel
import ceres.system.Config
import ceres.system.JsonSource
import ceres.tools.ToolDefinition
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.serialization.json.*
import kotlin.test.*

private class FakeLlm(private val respond: (String) -> Pair<Int, String>) : AutoCloseable {
  private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  @Volatile var body = ""
  @Volatile var auth: String? = null

  init {
    server.createContext("/v1/chat/completions") { ex ->
      body = ex.requestBody.readBytes().decodeToString()
      auth = ex.requestHeaders.getFirst("Authorization")
      val (code, text) = respond(body)
      val bytes = text.toByteArray()
      ex.sendResponseHeaders(code, bytes.size.toLong())
      ex.responseBody.write(bytes)
      ex.close()
    }
    server.start()
  }

  val url get() = "http://127.0.0.1:${server.address.port}"
  override fun close() = server.stop(0)
}

private fun api(url: String, key: String = "", img: (String) -> String? = { null }) = ApiV1(
  Config(JsonSource.parse(buildJsonObject {
    put("baseUrl", url); put("apiKey", key); put("model", "m"); put("timeout", 5)
  }.toString())),
  img,
)

private fun sse(vararg events: String) = events.joinToString("") { "data: $it\n\n" }

private fun textChunk(t: String) = buildJsonObject {
  putJsonArray("choices") { addJsonObject { putJsonObject("delta") { put("content", t) } } }
}.toString()

private fun toolChunk(index: Int, id: String?, name: String?, arguments: String?) = buildJsonObject {
  putJsonArray("choices") {
    addJsonObject {
      putJsonObject("delta") {
        putJsonArray("tool_calls") {
          addJsonObject {
            put("index", index)
            if (id != null) put("id", id)
            putJsonObject("function") {
              if (name != null) put("name", name)
              if (arguments != null) put("arguments", arguments)
            }
          }
        }
      }
    }
  }
}.toString()

private fun usageChunk(tokens: Int) = buildJsonObject {
  putJsonArray("choices") {}
  putJsonObject("usage") { put("prompt_tokens", tokens) }
}.toString()

private const val OK_REPLY = """{"choices":[{"message":{"content":"ok"}}]}"""
private val HI = listOf<Msg>(Msg.Text(Role.USER, "hi"))

class ApiV1Test {
  private fun sent(s: FakeLlm) = Json.parseToJsonElement(s.body).jsonObject

  @Test fun streamAssemblesTextAndUsage() {
    FakeLlm { 200 to sse(textChunk("Hel"), textChunk("lo"), usageChunk(42), "[DONE]") }.use { s ->
      val got = mutableListOf<String>()
      val r = api(s.url).stream(HI, emptyList(), Cancel()) { got += it }
      assertEquals(listOf("Hel", "lo"), got)
      assertEquals("Hello", r.content)
      assertEquals(42, r.promptTokens)
      assertNull(r.toolCalls)
    }
  }

  @Test fun streamAssemblesToolCallsFromFragments() {
    val events = sse(
      toolChunk(0, "c1", "shell", "{\"command\""),
      toolChunk(0, null, null, ":\"ls\"}"),
      toolChunk(1, "c2", "web_search", "{\"query\":\"x\"}"),
      "[DONE]",
    )
    FakeLlm { 200 to events }.use { s ->
      val r = api(s.url).stream(HI, emptyList(), Cancel()) {}
      assertNull(r.content)
      assertEquals(
        listOf(ToolCall("c1", "shell", "{\"command\":\"ls\"}"), ToolCall("c2", "web_search", "{\"query\":\"x\"}")),
        r.toolCalls,
      )
    }
  }

  @Test fun streamSendsStreamingFlags() {
    FakeLlm { 200 to sse("[DONE]") }.use { s ->
      api(s.url).stream(HI, emptyList(), Cancel()) {}
      val b = sent(s)
      assertEquals(true, b["stream"]?.jsonPrimitive?.boolean)
      assertEquals(true, b["stream_options"]?.jsonObject?.get("include_usage")?.jsonPrimitive?.boolean)
      assertNull(b["chat_template_kwargs"])
    }
  }

  @Test fun httpErrorsThrow() {
    FakeLlm { 500 to "boom" }.use { s ->
      val e = assertFailsWith<IllegalStateException> { api(s.url).stream(HI, emptyList(), Cancel()) {} }
      assertTrue("HTTP 500" in e.message.orEmpty())
      assertFailsWith<IllegalStateException> { api(s.url).request(HI, emptyList()) }
    }
  }

  @Test fun streamEndingWithoutDoneIsAnError() {
    FakeLlm { 200 to sse(textChunk("par")) }.use { s ->
      assertFails { api(s.url).stream(HI, emptyList(), Cancel()) {} }
    }
  }

  @Test fun requestParsesContentToolCallsAndUsage() {
    val reply = buildJsonObject {
      putJsonArray("choices") {
        addJsonObject {
          putJsonObject("message") {
            put("content", "thinking")
            putJsonArray("tool_calls") {
              addJsonObject {
                put("id", "c1"); put("type", "function")
                putJsonObject("function") { put("name", "shell"); put("arguments", "{}") }
              }
            }
          }
        }
      }
      putJsonObject("usage") { put("prompt_tokens", 7) }
    }.toString()
    FakeLlm { 200 to reply }.use { s ->
      val r = api(s.url).request(HI, emptyList())
      assertEquals("thinking", r.content)
      assertEquals(7, r.promptTokens)
      assertEquals(listOf(ToolCall("c1", "shell", "{}")), r.toolCalls)
      assertNull(sent(s)["stream"])
    }
  }

  @Test fun thinkFalseDisablesThinkingViaTemplateKwargs() {
    FakeLlm { 200 to OK_REPLY }.use { s ->
      api(s.url).request(HI, emptyList(), think = false)
      assertEquals(false, sent(s)["chat_template_kwargs"]?.jsonObject?.get("enable_thinking")?.jsonPrimitive?.boolean)
      api(s.url).request(HI, emptyList(), think = true)
      assertNull(sent(s)["chat_template_kwargs"])
    }
  }

  @Test fun authorizationHeaderOnlyWhenKeyIsSet() {
    FakeLlm { 200 to OK_REPLY }.use { s ->
      api(s.url).request(HI, emptyList())
      assertNull(s.auth)
      api(s.url, key = "secret").request(HI, emptyList())
      assertEquals("Bearer secret", s.auth)
    }
  }

  @Test fun toolDefinitionsAreSent() {
    val def = ToolDefinition("shell", "run", buildJsonObject { put("type", "object") })
    FakeLlm { 200 to OK_REPLY }.use { s ->
      api(s.url).request(HI, listOf(def))
      val f = sent(s)["tools"]!!.jsonArray[0].jsonObject["function"]!!.jsonObject
      assertEquals("shell", f["name"]?.jsonPrimitive?.content)
      assertEquals("run", f["description"]?.jsonPrimitive?.content)
    }
  }

  @Test fun messageSerialisation() {
    val msgs = listOf(
      Msg.Text(Role.SYSTEM, "s"),
      Msg.Text(Role.USER, "u"),
      Msg.ToolCalls(listOf(ToolCall("1", "shell", "{\"command\":\"ls\"}")), "thinking"),
      Msg.ToolResult("1", "out", images = listOf("a.png", "b.png")),
      Msg.Image("c.png"),
    )
    FakeLlm { 200 to OK_REPLY }.use { s ->
      api(s.url, img = { p -> if (p == "a.png") "data:image/png;base64,AAA" else null }).request(msgs, emptyList())
      val m = sent(s)["messages"]!!.jsonArray.map { it.jsonObject }

      assertEquals("system", m[0]["role"]?.jsonPrimitive?.content)
      assertEquals("user", m[1]["role"]?.jsonPrimitive?.content)

      assertEquals("assistant", m[2]["role"]?.jsonPrimitive?.content)
      assertEquals("thinking", m[2]["content"]?.jsonPrimitive?.content)
      val call = m[2]["tool_calls"]!!.jsonArray[0].jsonObject
      assertEquals("1", call["id"]?.jsonPrimitive?.content)
      assertEquals("{\"command\":\"ls\"}", call["function"]!!.jsonObject["arguments"]?.jsonPrimitive?.content)

      assertEquals("tool", m[3]["role"]?.jsonPrimitive?.content)
      assertEquals("1", m[3]["tool_call_id"]?.jsonPrimitive?.content)
      val parts = m[3]["content"]!!.jsonArray.map { it.jsonObject }
      assertEquals(3, parts.size)
      assertEquals("text", parts[0]["type"]?.jsonPrimitive?.content)
      assertEquals("data:image/png;base64,AAA", parts[1]["image_url"]!!.jsonObject["url"]?.jsonPrimitive?.content)
      assertEquals("[image unavailable: b.png]", parts[2]["text"]?.jsonPrimitive?.content)

      assertEquals("user", m[4]["role"]?.jsonPrimitive?.content)
      assertEquals("[image unavailable: c.png]", m[4]["content"]!!.jsonArray[0].jsonObject["text"]?.jsonPrimitive?.content)
    }
  }

  @Test fun toolResultWithoutImagesIsPlainString() {
    FakeLlm { 200 to OK_REPLY }.use { s ->
      api(s.url).request(listOf(Msg.ToolResult("1", "out")), emptyList())
      assertEquals("out", sent(s)["messages"]!!.jsonArray[0].jsonObject["content"]?.jsonPrimitive?.content)
    }
  }
}
