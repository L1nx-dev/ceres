package ceres.tools

import ceres.testing.*
import kotlin.test.*

class WebFetchTest {
  private fun run(url: String, seen: Boolean = true): String {
    val ctx = toolCtx(tempDir(), if (seen) mutableSetOf(url) else mutableSetOf())
    return WebFetch().run(args("url" to url), ctx)
  }

  private fun blockedReason(url: String): String? =
    runCatching { run(url) }.exceptionOrNull().let { e ->
      if (e is IllegalArgumentException && "blocked" in e.message.orEmpty()) null else "not blocked: $url -> $e"
    }

  @Test fun missingUrl() {
    assertTrue(WebFetch().run(args(), toolCtx(tempDir())).startsWith("error: missing 'url'"))
  }

  @Test fun urlMustHaveBeenSeen() {
    assertTrue(run("https://example.com/", seen = false).contains("not seen"))
  }

  @Test fun onlyHttpSchemes() {
    for (u in listOf("file:///etc/passwd", "ftp://example.com/x"))
      assertFailsWith<IllegalArgumentException>(u) { run(u) }
  }

  @Test fun privateAndLoopbackHostsAreBlocked() {
    val bad = listOf(
      "http://127.0.0.1:9/", "http://localhost/", "http://0.0.0.0/", "http://10.1.2.3/",
      "http://192.168.0.1/", "http://172.16.0.1/", "http://169.254.169.254/latest/meta-data/",
    ).mapNotNull(::blockedReason)
    assertTrue(bad.isEmpty(), bad.joinToString("\n"))
  }

  @Test fun ipv6UniqueLocalIsBlocked() { assertNull(blockedReason("http://[fc00::1]/")) }
  @Test fun cgnatIsBlocked() { assertNull(blockedReason("http://100.64.0.1/")) }
}
