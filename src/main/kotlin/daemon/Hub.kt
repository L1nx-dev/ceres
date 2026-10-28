package ceres.main

import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread
import ceres.core.*
import ceres.system.*

class Item(val id: Long, val chat: Long, val event: Event)

class Hub(private val agent: Agent) {
  private val lock = Any()
  private var seq = 0L
  private var active = false
  val busy: Boolean get() = synchronized(lock) { active }
  private val buffer = ArrayList<Item>()
  private val subs = HashSet<LinkedBlockingQueue<Item>>()
  @Volatile private var cancel: Cancel? = null

  fun start(chat: Long, text: String, files: List<String>): Boolean {
    val c = Cancel()
    synchronized(lock) {
      if (active) return false
      active = true; seq = 0; buffer.clear(); cancel = c
    }
    thread(name = "run") {
      val emit = { e: Event -> publish(chat, e) }
      try { agent.prompt(chat, text, files, c, emit) }
      catch (e: Exception) { emit(Event.Error(e.message ?: "internal error")) }
      finally { synchronized(lock) { active = false } }
    }
    return true
  }

  private fun publish(chat: Long, e: Event) = synchronized(lock) {
    val it = Item(++seq, chat, e)
    buffer += it
    subs.forEach { q -> q.offer(it) }
  }

  fun cancel() { cancel?.cancel() }

  fun subscribe(lastId: Long): LinkedBlockingQueue<Item> = synchronized(lock) {
    val q = LinkedBlockingQueue<Item>()
    buffer.filter { lastId >= 0 && it.id > lastId }.forEach { q.offer(it) }
    subs += q
    q
  }

  fun unsubscribe(q: LinkedBlockingQueue<Item>) = synchronized(lock) { subs -= q }
}
