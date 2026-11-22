package ceres.gui.common

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.foundation.layout.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.RuntimeEffect
import java.nio.file.*
import java.util.zip.ZipInputStream
import kotlin.io.path.*

sealed interface Background {
  data class Plain(val color: Color) : Background
  data class Shader(val sksl: String) : Background
}

fun Theme.colorScheme(): ColorScheme =
  (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent,
    onSurface = text, onBackground = text, onSurfaceVariant = text,
    surfaceContainer = frame, surfaceContainerHigh = frame,
  )

data class Theme(
  val id: String, val name: String, val dark: Boolean,
  val background: Background,
  val frame: Color, val border: Color,
  val glass: Color, val input: Color, val user: Color, val error: Color,
  val glassBorder: Color, val blur: Dp,
  val text: Color, val accent: Color, val codeBg: Color, val sendBg: Color, val sendFg: Color,
) {
  companion object {
    val Default = Theme(
      "dark",
      "Fallback",
      true,
      Background.Plain(Color(0xFF101014)),
      Color(0xFF0B0B0F),
      Color(0xFF1C1C22),
      Color(0xCC1A1A20),
      Color(0xEE1A1A20),
      Color(0xFF3A3A6A),
      Color(0xCC802020),
      Color(0x22FFFFFF),
      0.dp,
      Color(0xFFE6E1E5),
      Color(0xFF8AB4F8),
      Color(0x33FFFFFF),
      Color.White,
      Color.Black,
    )
  }
}

val LocalTheme = staticCompositionLocalOf { Theme.Default }

object ThemeLoader {

  fun installBundled(themesDir: Path, overwrite: Boolean = false) {
    val index = ThemeLoader::class.java.getResourceAsStream("/themes/index.txt")
      ?.use { it.readBytes().decodeToString() } ?: return
    index.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { rel ->
      val target = (themesDir / rel).normalize()
      if (!target.startsWith(themesDir.normalize()) || (target.exists() && !overwrite)) return@forEach
      target.parent.createDirectories()
      ThemeLoader::class.java.getResourceAsStream("/themes/$rel")
        ?.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
    }
  }

  fun load(dir: Path): Theme {
    val o = Json.parseToJsonElement((dir / "theme.json").readText()).jsonObject
    val d = Theme.Default
    fun col(k: String, def: Color): Color {
      val h = o[k]?.jsonPrimitive?.contentOrNull?.removePrefix("#") ?: return def
      val v = h.toLongOrNull(16) ?: return def
      return when (h.length) { 6 -> Color(0xFF000000 or v); 8 -> Color(v); else -> def }
    }
    val bg = o["background"]?.jsonObject
    val background = when (bg?.get("type")?.jsonPrimitive?.contentOrNull) {
      "shader" -> {
        val f = dir.resolve(bg!!.getValue("file").jsonPrimitive.content).normalize()
        require(f.startsWith(dir.normalize()) && f.isRegularFile()) { "bad shader path" }
        val src = f.readText()
        require("resolution" in src && "time" in src) { "shader must declare uniforms 'resolution' and 'time'" }
        RuntimeEffect.makeForShader(src).close() // throws on compile error
        Background.Shader(src)
      }
      else -> Background.Plain(
        bg?.get("color")?.jsonPrimitive?.contentOrNull?.let { runCatching { Color(it.removePrefix("#").toLong(16) or 0xFF000000) }.getOrNull() }
          ?: Color(0xFF101014))
    }
    return Theme(
      id = dir.fileName.toString(),
      name = o["name"]?.jsonPrimitive?.contentOrNull ?: dir.fileName.toString(),
      dark = o["dark"]?.jsonPrimitive?.booleanOrNull ?: true,
      background = background,
      frame = col("frame", d.frame), border = col("border", d.border),
      glass = col("glass", d.glass), input = col("input", d.input),
      user = col("user", d.user), error = col("error", d.error),
      glassBorder = col("glassBorder", d.glassBorder),
      blur = (o["blur"]?.jsonPrimitive?.floatOrNull ?: 12f).dp,
      text = col("text", d.text), accent = col("accent", d.accent), codeBg = col("codeBg", d.codeBg),
      sendBg = col("sendBg", d.sendBg), sendFg = col("sendFg", d.sendFg),
    )
  }

  fun import(src: Path, themesDir: Path): String {
    val tmp = Files.createTempDirectory("ceres-theme")
    try {
      if (src.isDirectory()) src.toFile().copyRecursively(tmp.toFile(), overwrite = true)
      else ZipInputStream(Files.newInputStream(src)).use { z ->
        generateSequence { z.nextEntry }.forEach { e ->
          val t = tmp.resolve(e.name).normalize()
          require(t.startsWith(tmp)) { "bad zip entry" }
          if (e.isDirectory) t.createDirectories()
          else { t.parent.createDirectories(); Files.copy(z, t) }
        }
      }
      val root = if ((tmp / "theme.json").exists()) tmp
         else Files.list(tmp).use { s -> s.filter { (it / "theme.json").exists() }.findFirst().orElse(null) }
           ?: error("no theme.json found")
      load(root) // validate
      val id = (src.fileName.toString().substringBeforeLast('.')).replace(Regex("[^A-Za-z0-9_-]"), "_")
      val target = themesDir / id
      target.toFile().deleteRecursively()
      root.toFile().copyRecursively(target.toFile())
      return id
    } finally { tmp.toFile().deleteRecursively() }
  }
}

class ThemeState(private val dir: Path, private val saved: Path) {
  var themes by mutableStateOf(listOf(Theme.Default)); private set
  var current by mutableStateOf(Theme.Default); private set
  var error by mutableStateOf<String?>(null); private set

  init {
    dir.createDirectories()
    val marker = dir / ".bundled"
    if (!marker.exists()) { ThemeLoader.installBundled(dir); marker.writeText("") }
    reload()
    select(runCatching { saved.readText().trim() }.getOrDefault(""))
  }

  private fun fallback() = themes.firstOrNull { it.id == "dark" } ?: themes.first()

  fun reload() {
    val found = Files.list(dir).use { s -> s.filter { it.isDirectory() }.sorted().toList() }
      .mapNotNull { runCatching { ThemeLoader.load(it) }.getOrNull() }
    themes = found.ifEmpty { listOf(Theme.Default) }
    current = themes.firstOrNull { it.id == current.id } ?: fallback()
  }

  fun select(id: String) {
    current = themes.firstOrNull { it.id == id } ?: fallback()
    runCatching { saved.parent.createDirectories(); saved.writeText(current.id) }
  }

  fun restoreBundled() { ThemeLoader.installBundled(dir, overwrite = true); reload() }

  fun import(src: Path) {
    error = runCatching { val id = ThemeLoader.import(src, dir); reload(); select(id); null }
      .getOrElse { "import failed: ${it.message}" }
  }
}

@Composable
fun ThemePicker(themes: ThemeState, onImport: () -> Unit, modifier: Modifier = Modifier) {
  var open by remember { mutableStateOf(false) }
  Column(modifier.fillMaxWidth().padding(8.dp)) {
    HorizontalDivider()
    Box {
      TextButton({ open = true }, Modifier.fillMaxWidth()) { Text("Theme: ${themes.current.name}") }
      DropdownMenu(open, { open = false }) {
        themes.themes.forEach { t -> DropdownMenuItem({ Text(t.name) }, { themes.select(t.id); open = false }) }
        HorizontalDivider()
        DropdownMenuItem({ Text("Import…") }, { open = false; onImport() })
        DropdownMenuItem({ Text("Reload") }, { open = false; themes.reload() })
      }
    }
    themes.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
  }
}
