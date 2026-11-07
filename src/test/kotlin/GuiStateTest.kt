package ceres.gui.common

import ceres.core.*
import ceres.testing.tempDir
import java.nio.file.Path
import kotlin.test.*

private class FakeApi : ChatApi {
  override var chat = 1L
  val prompts = mutableListOf<Pair<String, List<String>>>()
  var reject: String? = null
  var cancelled = 0

  override fun prompt(text: String, files: List<String>, onReject: (String) -> Unit) {
    prompts += text to files
    reject?.let(onReject)
  }
  override fun command(id: Long, text: String) = ""
  override fun upload(file: Path) = "1/${file.fileName}"
  override fun cancel() { cancelled++ }
  override fun history(id: Long) = emptyList<HistoryLine>()
  override fun chats() = emptyList<ChatInfo>()
  override fun create(name: String?) = 2L
  override fun select(id: Long) { chat = id }
  override fun connect() {}
}

class ChatStateTest {
  private val api = FakeApi()
  private val state = ChatState(api, Cache(tempDir()))
  private fun roles() = state.lines.map { it.role }

  @Test fun deltasMergeIntoOneAgentLine() {
    state.onEvent(1, Event.Delta("a"))
    state.onEvent(1, Event.Delta("b"))
    assertEquals(1, state.lines.size)
    assertEquals("ab", state.lines[0].text)
    assertTrue(state.busy)
  }

  @Test fun toolStartBreaksTheTextRun() {
    state.onEvent(1, Event.Delta("a"))
    state.onEvent(1, Event.ToolStart("1", "ls", "i"))
    state.onEvent(1, Event.Delta("b"))
    assertEquals(listOf(LineRole.AGENT, LineRole.TOOL, LineRole.AGENT), roles())
  }

  @Test fun doneAndCancelledClearBusy() {
    state.onEvent(1, Event.Delta("a"))
    state.onEvent(1, Event.Done("a"))
    assertFalse(state.busy)
    state.onEvent(1, Event.Delta("b"))
    state.onEvent(1, Event.Cancelled)
    assertFalse(state.busy)
  }

  @Test fun errorAddsErrorLineAndClearsBusy() {
    state.onEvent(1, Event.Delta("a"))
    state.onEvent(1, Event.Error("boom"))
    assertEquals(LineRole.ERROR, state.lines.last().role)
    assertEquals("boom", state.lines.last().text)
    assertFalse(state.busy)
  }

  @Test fun sendAddsUserLineAndCallsApi() {
    state.send("hi")
    assertEquals(listOf("hi" to emptyList<String>()), api.prompts)
    assertEquals(LineRole.USER, state.lines.last().role)
    assertTrue(state.busy)
  }

  @Test fun sendIsIgnoredWhileBusy() {
    state.send("one")
    state.send("two")
    assertEquals(1, api.prompts.size)
  }

  @Test fun rejectedPromptShowsErrorAndFreesTheInput() {
    api.reject = "HTTP 409"
    state.send("hi")
    assertFalse(state.busy)
    assertEquals(LineRole.ERROR, state.lines.last().role)
    assertEquals("HTTP 409", state.lines.last().text)
  }

  @Test fun eventsForOtherChatsDoNotTouchTheCurrentView() {
    state.onEvent(2, Event.Delta("x"))
    assertTrue(state.lines.isEmpty())
  }

  @Test fun cancelIsForwarded() {
    state.cancel()
    assertEquals(1, api.cancelled)
  }
}

class CacheTest {
  private val cache = Cache(tempDir())

  @Test fun historyRoundTrip() {
    val h = listOf(HistoryLine("USER", "hi \"there\"\n"), HistoryLine("AGENT", "yo"))
    cache.save(5, h)
    assertEquals(h, cache.load(5))
    assertNull(cache.load(6))
  }

  @Test fun chatsRoundTripIncludingNullNames() {
    val c = listOf(ChatInfo(1, null, 5), ChatInfo(2, "work", 9))
    cache.saveChats(c)
    assertEquals(c, cache.loadChats())
  }
}

class GroupTest {
  private fun line(id: Long, role: LineRole) = Line(id, role, "t$id")

  @Test fun groupsConsecutiveToolAndThinkingLinesIntoSteps() {
    val items = group(listOf(
      line(0, LineRole.USER),
      line(1, LineRole.TOOL), line(2, LineRole.THINKING), line(3, LineRole.TOOL),
      line(4, LineRole.AGENT),
      line(5, LineRole.TOOL),
    ))
    assertEquals(4, items.size)
    assertIs<Single>(items[0])
    assertEquals(3, (items[1] as Steps).lines.size)
    assertIs<Single>(items[2])
    assertEquals(1, (items[3] as Steps).lines.size)
    assertEquals(listOf(0L, 1L, 4L, 5L), items.map { it.key })
  }

  @Test fun emptyInput() { assertTrue(group(emptyList()).isEmpty()) }
}
