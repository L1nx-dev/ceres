package ceres.system

import ceres.testing.assume
import kotlin.io.path.*
import kotlin.test.*

class ConfigTest {
  @Test fun defaults() {
    val c = Config(JsonSource.parse(""))
    assertEquals(4000, c.port)
    assertEquals(30, c.maxToolDepth)
    assertEquals("http://localhost:8080", c.baseUrl)
    assertEquals("127.0.0.1", c.listen)
    assertEquals("", c.token)
  }

  @Test fun overrides() {
    val c = Config(JsonSource.parse("""{"port": 5000, "token": "t", "timeout": 5, "workDir": "/w"}"""))
    assertEquals(5000, c.port)
    assertEquals("t", c.token)
    assertEquals(5L, c.timeout)
    assertEquals("/w", c.workDir)
  }

  @Test fun brokenOrWrongShapedJsonFallsBackToDefaults() {
    assertEquals(4000, Config(JsonSource.parse("{oops")).port)
    assertEquals(4000, Config(JsonSource.parse("[1, 2]")).port)
    assertEquals(4000, Config(JsonSource.parse("""{"port": "abc"}""")).port)
  }

  @Test fun mapSource() {
    val c = Config(MapSource(mapOf("port" to 7, "token" to "x", "baseUrl" to "u")))
    assertEquals(7, c.port)
    assertEquals("x", c.token)
    assertEquals("u", c.baseUrl)
  }
}

class XdgTest {
  private val cfgEnv: String? = System.getenv("XDG_CONFIG_HOME")
  private val dataEnv: String? = System.getenv("XDG_DATA_HOME")

  private fun sandboxed() {
    assume(cfgEnv != null && dataEnv != null && "test-xdg" in cfgEnv && "test-xdg" in dataEnv,
      "XDG_* not redirected to build/test-xdg")
    Path(cfgEnv!!).resolve("ceres").toFile().deleteRecursively()
  }

  @Test fun createsDirectories() {
    sandboxed()
    val x = Xdg(seed = false)
    listOf(x.config, x.share, x.skills, x.memory).forEach { assertTrue(it.isDirectory(), "$it") }
  }

  @Test fun seedsDefaultsWithoutOverwriting() {
    sandboxed()
    val x = Xdg()
    for (f in listOf("config.json", "boot.script", "SOUL.md", "USER.md", "SKETCHPAD.md"))
      assertTrue((x.config / f).exists(), f)
    (x.config / "USER.md").writeText("custom")
    Xdg()
    assertEquals("custom", (x.config / "USER.md").readText())
  }

  @Test fun configReadsConfigJson() {
    sandboxed()
    val x = Xdg(seed = false)
    assertEquals(4000, Config(x).port)
    (x.config / "config.json").writeText("""{"port": 1234}""")
    assertEquals(1234, Config(x).port)
    (x.config / "config.json").writeText("{broken")
    assertEquals(4000, Config(x).port)
  }
}
