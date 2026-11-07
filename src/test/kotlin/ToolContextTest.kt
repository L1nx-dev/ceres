package ceres.tools

import ceres.testing.*
import kotlin.io.path.*
import kotlin.test.*

class ToolContextTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)

  @Test fun resolvesInsidePaths() {
    assertEquals(root / "a" / "b.txt", ctx.resolve("a/b.txt"))
    assertEquals(root, ctx.resolve("."))
    assertEquals(root / "b", ctx.resolve("a/../b"))
  }

  @Test fun rejectsParentTraversal() {
    for (p in listOf("..", "../x", "a/../../x", "a/b/../../../x"))
      assertFailsWith<IllegalArgumentException>(p) { ctx.resolve(p) }
  }

  @Test fun rejectsAbsolutePaths() {
    assertFailsWith<IllegalArgumentException> { ctx.resolve("/etc/passwd") }
  }

  @Test fun rejectsSiblingWithSamePrefix() {
    val sibling = root.resolveSibling(root.fileName.toString() + "-evil").createDirectories()
    assertFailsWith<IllegalArgumentException> { ctx.resolve("../${sibling.fileName}/f") }
  }

  @Test fun rejectsSymlinkedDirectoryEscape() {
    val outside = tempDir()
    (root / "link").createSymbolicLinkPointingTo(outside)
    assertFailsWith<IllegalArgumentException> { ctx.resolve("link") }
    assertFailsWith<IllegalArgumentException> { ctx.resolve("link/new.txt") }
  }

  @Test fun rejectsSymlinkedFileEscape() {
    val outside = (tempDir() / "secret.txt").also { it.writeText("s") }
    (root / "f").createSymbolicLinkPointingTo(outside)
    assertFailsWith<IllegalArgumentException> { ctx.resolve("f") }
  }

  @Test fun allowsSymlinksThatStayInside() {
    val real = (root / "real").createDirectories()
    (root / "link").createSymbolicLinkPointingTo(real)
    assertEquals(root / "link" / "x", ctx.resolve("link/x"))
  }

  @Test fun attachAcceptsImagesAndDrainsOnce() {
    (root / "x.png").writeBytes(byteArrayOf(1))
    assertEquals("image attached: x.png", ctx.attach("x.png"))
    assertEquals(listOf("x.png"), ctx.drainImages())
    assertTrue(ctx.drainImages().isEmpty())
  }

  @Test fun attachRejectsNonImagesMissingFilesAndEscapes() {
    (root / "a.txt").writeText("x")
    assertFailsWith<IllegalArgumentException> { ctx.attach("a.txt") }
    assertFailsWith<IllegalArgumentException> { ctx.attach("missing.png") }
    assertFailsWith<IllegalArgumentException> { ctx.attach("../x.png") }
  }
}
