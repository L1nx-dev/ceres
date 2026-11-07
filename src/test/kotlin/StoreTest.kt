package ceres.main

import kotlin.io.path.*
import kotlin.test.*

class StoreTest {
  private fun store() = Store(createTempDirectory("ceres") / "s.db")

  @Test fun migratesFreshDb() { store().close() }

  @Test fun roundTripAllKinds() {
    store().use { s ->
      val sid = s.newSession()
      val msgs = listOf(
        Msg.Text(Role.USER, "hi"),
        Msg.ToolCalls(listOf(ToolCall("1", "shell", "{}")), content = null),
        Msg.ToolResult("1", "ok", images = listOf("a.png")),
        Msg.Image("b.png"),
        Msg.Text(Role.ASSISTANT, "done", source = Source.MODEL),
      )
      msgs.forEach { s.append(sid, it) }
      assertEquals(msgs, s.load(sid))
    }
  }

  @Test fun metadataSurvives() {
    store().use { s ->
      val sid = s.newSession()
      val m = Msg.Text(Role.USER, "x", BlockType.PINNED, Source.SYSTEM, "ref1")
      s.append(sid, m)
      assertEquals(m, s.load(sid).single())
    }
  }

  @Test fun failedToolResultKeepsOkFlag() {
    store().use { s ->
      val sid = s.newSession()
      s.append(sid, Msg.ToolCalls(listOf(ToolCall("1", "shell", "{}"))))
      s.append(sid, Msg.ToolResult("1", "error: x", ok = false))
      assertFalse((s.load(sid)[1] as Msg.ToolResult).ok)
    }
  }

  @Test fun appendAssignsIncreasingIds() {
    store().use { s ->
      val sid = s.newSession()
      val a = Msg.Text(Role.USER, "a"); val b = Msg.Text(Role.USER, "b")
      s.append(sid, a); s.append(sid, b)
      assertTrue(a.id!! < b.id!!)
    }
  }

  @Test fun repairFillsMissingResultsAndIsIdempotent() {
    store().use { s ->
      val sid = s.newSession()
      s.append(sid, Msg.ToolCalls(listOf(ToolCall("1", "shell", "{}"), ToolCall("2", "shell", "{}"))))
      s.append(sid, Msg.ToolResult("1", "ok"))
      val m = s.load(sid)
      assertEquals(3, m.size)
      assertEquals("error: interrupted", (m.last() as Msg.ToolResult).content)
      assertEquals("2", (m.last() as Msg.ToolResult).callId)
      assertEquals(3, s.load(sid).size)
    }
  }

  @Test fun idsNotReusedAfterDelete() {
    store().use { s ->
      s.newSession()
      val b = s.newSession()
      s.delete(b)
      assertNotEquals(b, s.newSession())
    }
  }

  @Test fun deleteRemovesSessionAndMessages() {
    store().use { s ->
      val sid = s.newSession()
      s.append(sid, Msg.Text(Role.USER, "hi"))
      s.delete(sid)
      assertFalse(s.exists(sid))
      assertTrue(s.load(sid).isEmpty())
    }
  }

  @Test fun sessionNamesAreUnique() {
    store().use { s ->
      s.newSession("a")
      assertFails { s.newSession("a") }
    }
  }

  @Test fun resolveByNameOrId() {
    store().use { s ->
      val id = s.newSession("work")
      assertEquals(id, s.resolve("work"))
      assertEquals(id, s.resolve(id.toString()))
      assertNull(s.resolve("nope"))
      assertNull(s.resolve("99999"))
    }
  }

  @Test fun renameChangesName() {
    store().use { s ->
      val id = s.newSession("old")
      s.rename(id, "new")
      assertNull(s.byName("old"))
      assertEquals(id, s.byName("new"))
    }
  }

  @Test fun sessionListHidesEmptyUnnamedChatsExceptCurrent() {
    store().use { s ->
      s.newSession()
      val named = s.newSession("named")
      val talked = s.newSession()
      s.append(talked, Msg.Text(Role.USER, "hello"))
      val current = s.newSession()
      val list = s.sessions(current)
      assertEquals(listOf(current, talked, named), list.map { it.id })
      assertEquals("hello", list.first { it.id == talked }.preview)
    }
  }
}
