package ceres.main

import ceres.testing.tempDir
import kotlin.io.path.*
import kotlin.test.*

class ShareTest {
  private val base = tempDir()
  private val root = (base / "share").createDirectories()
  private val share = Share(root)
  private fun put(chat: Long, name: String, body: String = "x") = share.save(chat, name, body.byteInputStream())

  @Test fun savesUnderChatDirectory() {
    assertEquals("1/a.txt", put(1, "a.txt", "hello"))
    assertEquals("hello", (root / "1" / "a.txt").readText())
    assertEquals("2/a.txt", put(2, "a.txt"))
  }

  @Test fun stripsDirectoryComponents() {
    assertEquals("1/x.txt", put(1, "../../etc/x.txt"))
    assertTrue((root / "1" / "x.txt").exists())
    assertEquals("1/y.txt", put(1, "/abs/y.txt"))
  }

  @Test fun rejectsBadNames() {
    for (bad in listOf("..", "", "/", "a\nb", "a\u0007b"))
      assertFailsWith<IllegalStateException>(bad) { put(1, bad) }
  }

  @Test fun collisionsGetNumberedSuffix() {
    assertEquals("1/a.txt", put(1, "a.txt"))
    assertEquals("1/a-1.txt", put(1, "a.txt"))
    assertEquals("1/a-2.txt", put(1, "a.txt"))
    assertEquals("1/README", put(1, "README"))
    assertEquals("1/README-1", put(1, "README"))
    assertEquals("1/a.tar.gz", put(1, "a.tar.gz"))
    assertEquals("1/a.tar-1.gz", put(1, "a.tar.gz"))
  }

  @Test fun openReturnsOnlyRegularFilesInsideRoot() {
    put(1, "a.txt")
    (base / "secret.txt").writeText("s")
    (base / "share-evil").createDirectories().resolve("f").writeText("s")
    (root / "link.txt").createSymbolicLinkPointingTo(base / "secret.txt")

    assertNotNull(share.open("1/a.txt"))
    assertNull(share.open("1"))                  // directory
    assertNull(share.open("1/missing.txt"))
    assertNull(share.open("../secret.txt"))
    assertNull(share.open("../share-evil/f"))    // same name prefix, different dir
    assertNull(share.open("link.txt"))           // symlink out of the root
  }
}

class MemoryIndexTest {
  private val dir = tempDir()

  @Test fun emptyDirectory() { assertEquals("(empty)", MemoryIndex.build(dir)) }

  @Test fun listsMarkdownFilesWithFirstLineAsDescription() {
    (dir / "a.md").writeText("Alpha\nbody")
    (dir / "sub").createDirectories(); (dir / "sub" / "b.md").writeText("Beta\nbody")
    (dir / "ignored.txt").writeText("nope")
    (dir / "empty.md").writeText("")
    assertEquals("a.md — Alpha\nempty.md — \nsub/b.md — Beta", MemoryIndex.build(dir))
  }
}
