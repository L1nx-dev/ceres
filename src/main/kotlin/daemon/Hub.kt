package ceres.main

import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

class Item(val id: Long, val event: Event)

class Hub(private val agent: Agent) {
  private val lock = Any()
  private var seq = 0L
  private var active = false
  val busy: Boolean get() = synchronized(lock) { active }
  private val buffer = ArrayList<Item>()
  private val subs = HashSet<LinkedBlockingQueue<Item>>()
  @Volatile private var cancel: Cancel? = null

  fun start(text: String): Boolean {
    val c = Cancel()
    synchronized(lock) {
      if (active) return false
      active = true; seq = 0; buffer.clear(); cancel = c
    }
    thread(name = "run") {
      try { agent.prompt(text, c, ::publish) }
      catch (e: Exception) { publish(Event.Error(e.message ?: "internal error")) }
      finally { synchronized(lock) { active = false } }
    }
    return true
  }

  fun cancel() { cancel?.cancel() }

  private fun publish(e: Event) = synchronized(lock) {
    val it = Item(++seq, e)
    buffer += it
    subs.forEach { q -> q.offer(it) }
  }

  fun subscribe(lastId: Long): LinkedBlockingQueue<Item> = synchronized(lock) {
    val q = LinkedBlockingQueue<Item>()
    buffer.filter { it.id > lastId }.forEach { q.offer(it) }
    subs += q
    q
  }
  fun unsubscribe(q: LinkedBlockingQueue<Item>) = synchronized(lock) { subs -= q }
}
