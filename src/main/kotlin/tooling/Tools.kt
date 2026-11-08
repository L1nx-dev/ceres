package ceres.tools

import kotlinx.serialization.json.JsonObject
import java.nio.file.*
import java.util.concurrent.TimeUnit
import ceres.system.*
import ceres.core.*
import ceres.main.Source
import ceres.system.Xdg
import ceres.main.Msg
import java.nio.file.Path
import kotlin.io.path.div
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.*

data class ToolDefinition(val name: String, val description: String, val parameters: JsonObject)

internal fun memName(s: String) = s.trim().removeSuffix(".md")

class ToolContext(
  val root: Path,
  val config: Config,
  val seen: MutableSet<String>,
  val ask: (List<Msg>) -> String
) {
  var cancel: Cancel? = null
  private val pendingImages = mutableListOf<String>()

  fun resolve(rel: String): Path {
    val p = root.resolve(rel).normalize()
    require(p.startsWith(root)) { "path outside workspace" }
    var probe = p
    while (!Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) probe = probe.parent
    require(probe.toRealPath().startsWith(root)) { "path outside workspace" }
    return p
  }

  fun attach(rel: String): String {
    val p = resolve(rel)
    require(Files.isRegularFile(p) && Images.isImage(p.fileName.toString())) { "not an image file: $rel" }
    pendingImages += root.relativize(p).toString()
    return "image attached: $rel"
  }
  fun drainImages(): List<String> = pendingImages.toList().also { pendingImages.clear() }

  fun exec(argv: List<String>, stdin: String?, timeoutSec: Long): String {
    val bwrap = listOf(
      "bwrap",
      "--unshare-all",
      "--share-net",
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
      "--tmpfs", "/etc",
      "--ro-bind", "/etc/resolv.conf", "/etc/resolv.conf",
      "--ro-bind", "/etc/ssl", "/etc/ssl",
      "--ro-bind", "/etc/static", "/etc/static",   // NixOS: /etc/ssl, /etc/resolv.conf etc. often symlink into here
      "--ro-bind", "/etc/passwd", "/etc/passwd",
      "--ro-bind", "/etc/localtime", "/etc/localtime",
      "--",
    )

    val p = ProcessBuilder(bwrap + argv).redirectErrorStream(true).start()
    cancel?.setHook { p.destroyForcibly() }

    try {
      val out = java.util.concurrent.CompletableFuture.supplyAsync { p.inputStream.readNBytes(16_000) }
      p.outputStream.use { if (stdin != null) it.write(stdin.toByteArray()) }

      val bytes = try {
        out.get(timeoutSec, TimeUnit.SECONDS)
      } catch (_: java.util.concurrent.TimeoutException) {
        p.destroyForcibly()
        return "error: timeout after ${timeoutSec}s"
      }
      val truncated = bytes.size >= 16_000
      if (truncated) p.destroyForcibly()
      if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly().waitFor()

      val text = bytes.decodeToString()
      if (cancel?.cancelled == true) return "error: cancelled"
      if (truncated) return "$text\n[output truncated at 16000 bytes, command killed]"
      val code = p.exitValue()
      return if (code == 0) text else "error: exit $code\n$text"
    } 

    finally {
      cancel?.setHook(null)
    }
  }
}

interface Tool {
  val source: Source get() = Source.TOOL
  val icon: String? get() = null
  val def: ToolDefinition
  fun iconFor(args: JsonObject): String? = icon
  fun run(args: JsonObject, ctx: ToolContext): String
}


class Tools(xdg: Xdg) {
  val skills = Skills(xdg.skills)
  val list = listOf<Tool>(
    EditFile(),
    MakeFile(),
    WebSearch(),
    WebFetch(),
    Shell(),
    LoadImage(),
    Recall(xdg.memory, xdg.config / "SKETCHPAD.md"),
    LoadMemory(xdg.memory),
    LoadSkill(skills),
    Remember(xdg.config / "SKETCHPAD.md"),
  ).associateBy { it.def.name }
}
