package ceres.tools

import ceres.testing.*
import kotlin.io.path.*
import kotlin.test.*

class EditFileTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)
  private fun edit(vararg a: Pair<String, Any>) = EditFile().run(args(*a), ctx)
  private fun file(text: String) = (root / "f.txt").also { it.writeText(text) }

  @Test fun replacesOneMatch() {
    val f = file("a\nb\nc\n")
    assertEquals("ok: edited f.txt", edit("path" to "f.txt", "old_str" to "b", "new_str" to "B"))
    assertEquals("a\nB\nc\n", f.readText())
  }

  @Test fun replacementIsLiteral() {
    val f = file("x")
    edit("path" to "f.txt", "old_str" to "x", "new_str" to "\\1 \$1 .*")
    assertEquals("\\1 \$1 .*", f.readText())
  }

  @Test fun emptyNewStrDeletes() {
    val f = file("keep drop keep")
    edit("path" to "f.txt", "old_str" to " drop", "new_str" to "")
    assertEquals("keep keep", f.readText())
  }

  @Test fun errorsLeaveFileUntouched() {
    val f = file("a a")
    assertTrue(edit("path" to "f.txt", "old_str" to "zzz", "new_str" to "x").startsWith("error: old_str not found"))
    assertTrue(edit("path" to "f.txt", "old_str" to "a", "new_str" to "x").startsWith("error: old_str matches more than once"))
    assertTrue(edit("path" to "f.txt", "old_str" to "", "new_str" to "x").startsWith("error:"))
    assertTrue(edit("path" to "f.txt", "new_str" to "x").startsWith("error: missing 'old_str'"))
    assertTrue(edit("old_str" to "a", "new_str" to "x").startsWith("error: missing 'path'"))
    assertEquals("a a", f.readText())
  }

  @Test fun refusesPathsOutsideRoot() {
    assertFailsWith<IllegalArgumentException> { edit("path" to "../x", "old_str" to "a", "new_str" to "b") }
  }

  @Test fun missingFileThrows() {
    assertFails { edit("path" to "nope.txt", "old_str" to "a", "new_str" to "b") }
  }
}

class MakeFileTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)
  private fun make(vararg a: Pair<String, Any>) = MakeFile().run(args(*a), ctx)

  @Test fun createsFileWithParents() {
    assertEquals("ok: wrote a/b/c.txt (5 chars)", make("path" to "a/b/c.txt", "content" to "hello"))
    assertEquals("hello", (root / "a" / "b" / "c.txt").readText())
  }

  @Test fun contentIsOptional() {
    make("path" to "empty.txt")
    assertEquals("", (root / "empty.txt").readText())
  }

  @Test fun refusesToOverwriteNonEmptyFile() {
    (root / "f").writeText("old")
    assertTrue(make("path" to "f", "content" to "new").startsWith("error:"))
    assertEquals("old", (root / "f").readText())
  }

  @Test fun overwriteFlagReplaces() {
    (root / "f").writeText("old")
    make("path" to "f", "content" to "new", "overwrite" to true)
    assertEquals("new", (root / "f").readText())
  }

  @Test fun fillsExistingEmptyFile() {
    (root / "f").writeText("")
    assertTrue(make("path" to "f", "content" to "x").startsWith("ok"))
  }

  @Test fun rejectsDirectoriesMissingPathAndEscapes() {
    (root / "d").createDirectories()
    assertTrue(make("path" to "d").startsWith("error:"))
    assertTrue(make("content" to "x").startsWith("error: missing 'path'"))
    assertFailsWith<IllegalArgumentException> { make("path" to "../x", "content" to "x") }
  }

  @Test fun cannotCreateThroughSymlinkEscape() {
    val outside = tempDir()
    (root / "link").createSymbolicLinkPointingTo(outside)
    assertFailsWith<IllegalArgumentException> { make("path" to "link/evil.txt", "content" to "x") }
    assertFalse((outside / "evil.txt").exists())
  }
}

class ReadFileTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)

  @Test fun readsAndTruncatesTo20000Chars() {
    (root / "s.txt").writeText("hi")
    (root / "big.txt").writeText("a".repeat(30_000))
    assertEquals("hi", ReadFile().run(args("path" to "s.txt"), ctx))
    assertEquals(20_000, ReadFile().run(args("path" to "big.txt"), ctx).length)
  }

  @Test fun errorsAndEscapes() {
    assertTrue(ReadFile().run(args(), ctx).startsWith("error: missing 'path'"))
    assertFailsWith<IllegalArgumentException> { ReadFile().run(args("path" to "../x"), ctx) }
  }
}

class LoadImageToolTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)

  @Test fun attachesImage() {
    (root / "p.png").writeBytes(byteArrayOf(1))
    assertEquals("image attached: p.png", LoadImage().run(args("path" to "p.png"), ctx))
    assertEquals(listOf("p.png"), ctx.drainImages())
  }

  @Test fun rejectsNonImage() {
    (root / "a.txt").writeText("x")
    assertFailsWith<IllegalArgumentException> { LoadImage().run(args("path" to "a.txt"), ctx) }
  }
}
