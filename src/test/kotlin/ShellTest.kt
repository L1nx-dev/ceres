package ceres.tools

import ceres.testing.*
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class ShellIconTest {
  private fun icon(cmd: String) = Shell().iconFor(args("command" to cmd))

  @Test fun unknownAndMissingCommandGetFallbackIcon() {
    assertEquals("🦪", icon("frobnicate --now"))
    assertEquals("🦪", Shell().iconFor(args()))
  }

  @Test fun recursiveRmDiffersFromPlainRm() {
    assertNotEquals(icon("rm x"), icon("rm -rf x"))
    assertEquals(icon("rm x"), icon("rm -f x"))
    assertEquals(icon("rm -rf x"), icon("rm --recursive x"))
  }

  @Test fun firstRecognisedSegmentWins() {
    assertEquals(icon("git status"), icon("cd x && git status"))
    assertEquals(icon("cat f"), icon("cat f | grep x"))
  }

  @Test fun missingCommandIsAnError() {
    assertTrue(Shell().run(args(), toolCtx(tempDir())).startsWith("error: missing 'command'"))
  }
}

class SandboxTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)

  private fun sh(cmd: String, timeout: Long = 10) = ctx.exec(listOf("sh", "-c", cmd), null, timeout)

  init {
    val probe = runCatching { ctx.exec(listOf("echo", "ok"), null, 10) }.getOrDefault("")
    assume(probe.trim() == "ok", "bwrap sandbox unavailable here")
  }

  @Test fun runsInRootMountedAtCeres() { assertEquals("/Ceres\n", ctx.exec(listOf("pwd"), null, 10)) }

  @Test fun writesInsideRootPersistOnHost() {
    assertEquals("", sh("echo hi > out.txt"))
    assertEquals("hi\n", (root / "out.txt").readText())
  }

  @Test fun systemPathsAreReadOnly() {
    assertTrue(sh("touch /usr/x").startsWith("error: exit"))
  }

  @Test fun hostHomeIsNotVisible() { assertEquals("", sh("test ! -e /home")) }

  @Test fun environmentIsCleared() { assertEquals("/tmp\n", sh("echo \$HOME")) }

  @Test fun nonZeroExitIsReportedWithOutput() {
    assertEquals("error: exit 3\nboom\n", sh("echo boom; exit 3"))
  }

  @Test fun timeoutKillsTheCommand() {
    assertEquals("error: timeout after 1s", ctx.exec(listOf("sleep", "5"), null, 1))
  }

  @Test fun stdinIsForwarded() { assertEquals("hello", ctx.exec(listOf("cat"), "hello", 5)) }

  @Test fun outputIsCappedAt16000Chars() {
    val out = sh("head -c 100000 /dev/zero | tr '\\0' a")
    assertTrue(out.startsWith("a".repeat(16_000)))
    assertTrue(out.endsWith("[output truncated at 16000 bytes, command killed]"))
    assertEquals(16_003, out.count { it == 'a' })
  }

  @Test fun shellToolRunsCommands() {
    assertEquals("hi\n", Shell().run(args("command" to "echo hi"), ctx))
  }
}
