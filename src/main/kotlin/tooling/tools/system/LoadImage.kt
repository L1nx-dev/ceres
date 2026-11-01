package ceres.tools

import kotlinx.serialization.json.*
import java.nio.file.attribute.*
import java.nio.file.*

class LoadImage : Tool {
  override val icon = "󰋩 "
  override val def = ToolDefinition(
    "load_image", "View an image. Never lie about the contents of the image, when unsure say so.",
    buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("path") { put("type", "string"); put("description", "path to the image") }
      }
      putJsonArray("required") { add("path") }
    }
  )

  override fun run(args: JsonObject, ctx: ToolContext) = ctx.attach(args.getValue("path").jsonPrimitive.content)
}
