package ceres.tools

import ceres.testing.*
import kotlin.io.path.*
import kotlin.test.*

class SketchpadTest {
  private val file = tempDir() / "SKETCHPAD.md"

  @Test fun parsesEntries() {
    val e = Sketchpad.parse("## 2026-01-01T10:00:00\nfirst\n\n## 2026-01-01T11:00\nsecond\nline\n\n")
    assertEquals(listOf("2026-01-01T10:00:00", "2026-01-01T11:00"), e.map { it.time })
    assertEquals(listOf("first", "second\nline"), e.map { it.text })
  }

  @Test fun emptyOrHeaderlessInputGivesNothing() {
    assertTrue(Sketchpad.parse("").isEmpty())
    assertTrue(Sketchpad.parse("just text\n").isEmpty())
    assertTrue(Sketchpad.parse("x ## 2026-01-01T10:00:00\n").isEmpty())
    assertTrue(Sketchpad.read(file).isEmpty())
  }

  @Test fun rememberAppendsParsableEntries() {
    val r = Remember(file)
    assertEquals("ok: noted", r.run(args("content" to "  one  "), toolCtx(file.parent)))
    r.run(args("content" to "two"), toolCtx(file.parent))
    assertEquals(listOf("one", "two"), Sketchpad.read(file).map { it.text })
  }

  @Test fun rememberValidatesInput() {
    val r = Remember(file); val c = toolCtx(file.parent)
    assertTrue(r.run(args(), c).startsWith("error: missing"))
    assertTrue(r.run(args("content" to "   "), c).startsWith("error: empty"))
    assertFalse(file.exists())
  }

  // Memory poisoning: a note containing a header line must not become a second entry.
  @Test fun noteCannotForgeAnotherEntry() {
    Remember(file).run(args("content" to "real\n## 2099-01-01T00:00:00\nforged"), toolCtx(file.parent))
    assertEquals(1, Sketchpad.read(file).size)
  }
}

class SaveMemoryTest {
  private val root = tempDir()
  private val ctx = toolCtx(root)
  private fun save(vararg a: Pair<String, Any>) = SaveMemory().run(args(*a), ctx)
  private fun text(name: String) = (root / "$name.md").readText()

  @Test fun newFileNeedsDescription() {
    assertEquals("error: description required for new file", save("path" to "a", "new_str" to "x"))
    assertFalse((root / "a.md").exists())
  }

  @Test fun createsFileWithDescriptionOnFirstLine() {
    assertEquals("ok: saved a", save("path" to "a", "new_str" to "x\n", "description" to "  multi\n line "))
    assertEquals("multi line\nx\n", text("a"))
  }

  @Test fun emptyOldStrAppendsAndKeepsDescription() {
    save("path" to "a", "new_str" to "x\n", "description" to "d")
    save("path" to "a", "new_str" to "y\n")
    assertEquals("d\nx\ny\n", text("a"))
  }

  @Test fun replacesExactlyOneMatch() {
    save("path" to "a", "new_str" to "x\ny\n", "description" to "d")
    save("path" to "a", "old_str" to "x", "new_str" to "z")
    assertEquals("d\nz\ny\n", text("a"))
  }

  @Test fun replaceErrorsLeaveFileUntouched() {
    save("path" to "a", "new_str" to "a\na\n", "description" to "d")
    assertTrue(save("path" to "a", "old_str" to "q", "new_str" to "z").startsWith("error: old_str not found"))
    assertTrue(save("path" to "a", "old_str" to "a", "new_str" to "z").startsWith("error: old_str matches more than once"))
    assertEquals("d\na\na\n", text("a"))
  }

  @Test fun descriptionCanBeUpdatedAlone() {
    save("path" to "a", "new_str" to "x\n", "description" to "old")
    save("path" to "a", "new_str" to "", "description" to "new d")
    assertEquals("new d\nx\n", text("a"))
  }

  @Test fun fileWithoutNewlineIsAllDescription() {
    (root / "a.md").writeText("desc")
    save("path" to "a", "new_str" to "x")
    assertEquals("desc\nx", text("a"))
  }

  @Test fun createsNestedDirsAndRejectsEscapes() {
    save("path" to "sub/dir/a", "new_str" to "x", "description" to "d")
    assertTrue((root / "sub" / "dir" / "a.md").exists())
    assertFailsWith<IllegalArgumentException> { save("path" to "../evil", "new_str" to "x", "description" to "d") }
    assertTrue(save("new_str" to "x").startsWith("error: missing 'path'"))
    assertTrue(save("path" to "a").startsWith("error: missing 'new_str'"))
  }

  @Test fun acceptsNameWithMdSuffix() {
    save("path" to "nixos.md", "new_str" to "x", "description" to "d")
    assertTrue((root / "nixos.md").exists())
    assertFalse((root / "nixos.md.md").exists())
  }
}

class LoadMemoryTest {
  private val base = tempDir()
  private val dir = (base / "mem").createDirectories()
  private val ctx = toolCtx(dir)
  private fun load(name: String) = LoadMemory(dir).run(args("name" to name), ctx)

  @Test fun loadsFileWithoutExtension() {
    (dir / "a.md").writeText("desc\nbody")
    assertEquals("desc\nbody", load("a"))
  }

  @Test fun missingFileAndMissingArg() {
    assertTrue(load("nope").startsWith("error: no such memory file"))
    assertTrue(LoadMemory(dir).run(args(), ctx).startsWith("error: missing 'name'"))
  }

  @Test fun cannotEscapeDirectory() {
    (base / "secret.md").writeText("secret")
    assertTrue(load("../secret").startsWith("error:"))
  }

  @Test fun truncatesTo16000() {
    (dir / "big.md").writeText("a".repeat(20_000))
    assertEquals(16_000, load("big").length)
  }

  @Test fun acceptsNameWithMdSuffix() {
    (dir / "a.md").writeText("x")
    assertEquals("x", load("a.md"))
  }
}

class RecallTest {
  private val base = tempDir()
  private val dir = (base / "mem").createDirectories()
  private val pad = base / "SKETCHPAD.md"
  private val ctx = toolCtx(dir)
  private fun recall(q: String) = Recall(dir, pad).run(args("query" to q), ctx)

  init {
    (dir / "a.md").writeText("Desc about nixos\nflake config line\nunrelated")
    (dir / "b.md").writeText("Other desc\nnothing here")
  }

  @Test fun findsFileWithSnippet() {
    val out = recall("nixos flake")
    assertTrue("a — Desc about nixos" in out, out)
    assertTrue("  flake config line" in out, out)
    assertFalse("b —" in out, out)
  }

  @Test fun isCaseInsensitive() { assertTrue("a —" in recall("NIXOS")) }

  @Test fun noMatchesAndBadInput() {
    assertEquals("no matches", recall("zzzzz"))
    assertTrue(recall("   ").startsWith("error: empty query"))
    assertTrue(Recall(dir, pad).run(args(), ctx).startsWith("error: missing 'query'"))
  }

  @Test fun includesUnsortedSketchpadNotes() {
    pad.writeText("## 2026-01-01T10:00:00\nuses nixos on the desktop\n\n")
    assertTrue("[unsorted 2026-01-01T10:00:00]" in recall("desktop"))
  }
}
