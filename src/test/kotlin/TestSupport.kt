package ceres.testing

import ceres.system.Config
import ceres.system.JsonSource
import ceres.tools.ToolContext
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.*

fun tempDir(): Path = createTempDirectory("ceres-test").toRealPath()

fun toolCtx(root: Path, seen: MutableSet<String> = mutableSetOf()): ToolContext =
  ToolContext(root, Config(JsonSource.parse("{}")), seen) { "" }

fun args(vararg p: Pair<String, Any>): JsonObject = buildJsonObject {
  for ((k, v) in p) when (v) {
    is String -> put(k, v)
    is Boolean -> put(k, v)
    is Int -> put(k, v)
    else -> error("unsupported arg type for $k")
  }
}

fun assume(cond: Boolean, msg: String = "precondition not met") {
  org.junit.jupiter.api.Assumptions.assumeTrue(cond, msg)
}
