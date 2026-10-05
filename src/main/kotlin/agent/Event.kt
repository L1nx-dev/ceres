package ceres.main

sealed class Event {
  data class Delta(val text: String) : Event()
  data class ToolStart(val id: String, val label: String, val icon: String) : Event()
  data class ToolEnd(val id: String, val ok: Boolean, val output: String) : Event()
  data class Done(val text: String) : Event()
  data class Error(val message: String) : Event()
  object Cancelled : Event()
}

class Cancel {
  @Volatile var cancelled = false; private set
  @Volatile private var hook: (() -> Unit)? = null

  fun setHook(h: (() -> Unit)?) { hook = h; if (cancelled) h?.invoke() }
  fun cancel() { cancelled = true; hook?.invoke() }
}
