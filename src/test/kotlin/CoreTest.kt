package ceres.core

import ceres.testing.tempDir
import kotlin.io.path.*
import kotlin.test.*

class CodecTest {
  private val samples = listOf<Event>(
    Event.Delta("a \"quoted\"\nnewline \\ backslash ünï"),
    Event.ToolStart("1", "shell: ls", "x"),
    Event.ToolEnd("1", true, "out"),
    Event.ToolEnd("2", false, "error: boom"),
    Event.Done("all"),
    Event.Error("bad"),
    Event.Cancelled,
  )

  @Test fun roundTrip() { for (e in samples) assertEquals(e, decode(encode(e))) }

  @Test fun encodedEventsAreSingleLine() { for (e in samples) assertFalse('\n' in encode(e), "$e") }

  @Test fun unknownTypeBecomesError() {
    val e = decode("""{"type":"nope"}""")
    assertTrue(e is Event.Error && "nope" in e.message)
  }

  @Test fun malformedInputThrows() {
    assertFails { decode("not json") }
    assertFails { decode("""{"type":"delta"}""") }
  }
}

class CancelTest {
  @Test fun hookRunsOnCancel() {
    val c = Cancel(); var n = 0
    c.setHook { n++ }
    c.cancel()
    assertEquals(1, n)
    assertTrue(c.cancelled)
  }

  @Test fun hookSetAfterCancelRunsImmediately() {
    val c = Cancel(); var n = 0
    c.cancel()
    c.setHook { n++ }
    assertEquals(1, n)
  }

  @Test fun clearedHookIsNotCalled() {
    val c = Cancel(); var n = 0
    c.setHook { n++ }
    c.setHook(null)
    c.cancel()
    assertEquals(0, n)
  }
}

class ImagesTest {
  @Test fun mimeIsCaseInsensitiveAndRejectsOthers() {
    assertEquals("image/png", Images.mime("A.PNG"))
    assertEquals("image/jpeg", Images.mime("x.jpeg"))
    assertNull(Images.mime("x.txt"))
    assertNull(Images.mime("noext"))
    assertTrue(Images.isImage("a.webp"))
    assertFalse(Images.isImage("a.svg"))
  }

  @Test fun dataUrlEncodesBytes() {
    val p = tempDir() / "a.png"
    p.writeBytes(byteArrayOf(1, 2, 3))
    assertEquals("data:image/png;base64,AQID", Images.dataUrl(p))
  }

  @Test fun dataUrlRejectsNonImages() {
    val p = tempDir() / "a.txt"
    p.writeText("x")
    assertFailsWith<IllegalStateException> { Images.dataUrl(p) }
  }
}
