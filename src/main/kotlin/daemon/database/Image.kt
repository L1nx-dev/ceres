package ceres.core

import java.nio.file.*
import java.util.Base64

object Images {
  private val mimes = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "webp" to "image/webp",
    "gif" to "image/gif"
  )

  fun mime(name: String) = mimes[name.substringAfterLast('.', "").lowercase()]
  fun isImage(name: String) = mime(name) != null
  fun dataUrl(p: Path): String {
    val m = mime(p.fileName.toString()) ?: error("not an image: $p")
    return "data:$m;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(p))
  }
}
