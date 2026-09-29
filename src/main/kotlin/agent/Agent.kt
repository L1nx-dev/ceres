package ceres.main

class Agent(config: Config) {
  private val llm = InterfaceV1(config)
 
  private val history = mutableListOf<Pair<String, String>>()
 
  fun prompt(input: String): String {
    history += "user" to input
    val reply = llm.request(history)
    val text = reply.content ?: ""
    if (text.isNotBlank()) history += "assistant" to text  // store the reply
    return text
  }
}
