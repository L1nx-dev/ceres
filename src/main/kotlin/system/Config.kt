package ceres.system

import kotlinx.serialization.json.*

interface ConfigSource {
  fun string(key: String): String?
  fun long(key: String): Long?
  fun bool(key: String): Boolean?
  fun strings(key: String): List<String>?
}

class JsonSource(private val root: JsonObject) : ConfigSource {
  private fun prim(k: String) = root[k] as? JsonPrimitive
  override fun string(key: String) = prim(key)?.contentOrNull
  override fun long(key: String) = prim(key)?.longOrNull
  override fun bool(key: String) = prim(key)?.booleanOrNull
  override fun strings(key: String) =
    (root[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

  companion object {
    fun parse(text: String) = JsonSource(
      runCatching { Json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    )
  }
}

/** For Android: build from DataStore/SharedPreferences values. */
class MapSource(private val m: Map<String, Any?>) : ConfigSource {
  override fun string(key: String) = m[key]?.toString()
  override fun long(key: String) = (m[key] as? Number)?.toLong() ?: m[key]?.toString()?.toLongOrNull()
  override fun bool(key: String) = m[key] as? Boolean ?: m[key]?.toString()?.toBooleanStrictOrNull()
  override fun strings(key: String) = (m[key] as? List<*>)?.map { it.toString() }
}

class Config(private val src: ConfigSource) {
  private fun str(k: String, d: String) = src.string(k) ?: d
  private fun lng(k: String, d: Long) = src.long(k) ?: d
  private fun int(k: String, d: Int) = src.long(k)?.toInt() ?: d
  private fun flag(k: String, d: Boolean) = src.bool(k) ?: d
  private fun list(k: String, d: List<String> = emptyList()) = src.strings(k) ?: d

  val baseUrl       = str("baseUrl", "http://localhost:8080")
  val apiKey        = str("apiKey", "")
  val model         = str("model", "default")
  val timeout       = lng("timeout", 180)

  val workDir       = str("workDir", "")
  val maxToolDepth  = int("maxToolDepth", 30)

  val searXNG       = str("searXNG", "http://127.0.0.1:8888")
  val summaryModel  = str("summaryModel", "")

  // daemon
  val listen        = str("listen", "127.0.0.1")
  val port          = int("port", 4000)
  val token         = str("token", "")

  // clients, where to connect (local | direct | tor)
  val host          = str("host", "127.0.0.1")
  val guiConnection = str("guiConnection", "local")
}
