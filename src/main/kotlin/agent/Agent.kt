package ceres.main

class Agent(config: Config, xdg: Xdg) {
  private val llm = ApiV1(config)
 
  private val history = mutableListOf<Msg>()
 
  fun prompt(input: String): String {
    history += Msg.Text("user", input)
    val reply = llm.request(history)
    val text = reply.content ?: ""
    if (text.isNotBlank()) history += Msg.Text("assistant", text)
    return text
  }
}
