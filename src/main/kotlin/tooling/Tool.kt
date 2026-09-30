package ceres.tools

import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import ceres.main.Config
import java.util.concurrent.TimeUnit

data class ToolDefinition(val name: String, val description: String, val parameters: JsonObject)

class ToolContext(val root: Path) {
  fun resolve(rel: String): Path {
    val p = root.resolve(rel).normalize()
    require(p.startsWith(root)) { "path outside workspace" }
    return p
  }
  fun exec(argv: List<String>, stdin: String?, timeoutSec: Long): String {
    val bwrap = listOf(
      "bwrap",
      "--unshare-all",            // pid, net, ipc, uts, user, cgroup; add "--share-net" to allow network
      "--die-with-parent",
      "--new-session",
      "--clearenv",
      "--setenv", "PATH", "/run/current-system/sw/bin",
      "--setenv", "HOME", "/tmp",
      "--ro-bind", "/nix/store", "/nix/store",
      "--ro-bind", "/run/current-system/sw", "/run/current-system/sw",
      "--ro-bind", "/bin", "/bin",// nix only, TODO: fix
      "--ro-bind", "/usr", "/usr",// same
      "--proc", "/proc",
      "--dev", "/dev",
      "--tmpfs", "/tmp",
      "--bind", root.toString(), "/Ceres", // TODO: make configurable
      "--chdir", "/Ceres",
      "--",
    )
    val p = ProcessBuilder(bwrap + argv).redirectErrorStream(true).start()
    val out = java.util.concurrent.CompletableFuture.supplyAsync { p.inputStream.readAllBytes() }
    p.outputStream.use { if (stdin != null) it.write(stdin.toByteArray()) }

    if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
      p.destroyForcibly()
      return "error: timeout after ${timeoutSec}s"
    }
    val text = out.get().decodeToString().take(16_000)
    val code = p.exitValue()
    return if (code == 0) text else "error: exit $code\n$text"
  }
}

interface Tool {
  val def: ToolDefinition
  fun run(args: JsonObject, ctx: ToolContext): String
}

object Tools {
  val list = listOf<Tool>(
    ReadFile(), 
    ListFiles(),
    MakeDir(),
    EditFile(),
    MakeFile(),
    WebSearch(),
    RemoveFile(),
    Shell(),
  ).associateBy { it.def.name }
}
