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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog

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
  val id: String,
  val name: String,
  val dark: Boolean,
  val background: Background,
  val frame: Color,
  val border: Color,
  val glass: Color,
  val input: Color,
  val user: Color,
  val error: Color,
  val glassBorder: Color,
  val blur: Dp,
  val text: Color,
  val accent: Color,
  val codeBg: Color,
  val sendBg: Color,
  val sendFg: Color,
  val builtin: Boolean = false,
  val thumbnail: () -> ByteArray? = {null},
) {
  companion object {
    val Default = Theme(
      id = "dark",
      name = "Fallback",
      dark = true,
      background = Background.Plain(Color(0xFF101014)),
      frame = Color(0xFF0B0B0F),
      border = Color(0xFF1C1C22),
      glass = Color(0xCC1A1A20),
      input = Color(0xEE1A1A20),
      user = Color(0xFF3A3A6A),
      error = Color(0xCC802020),
      glassBorder = Color(0x22FFFFFF),
      blur = 0.dp,
      text = Color(0xFFE6E1E5),
      accent = Color(0xFF8AB4F8),
      codeBg = Color(0x33FFFFFF),
      sendBg = Color.White,
      sendFg = Color.Black,
      builtin = true,
      thumbnail = {null}
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

  private fun parse(id: String, builtin: Boolean, json: String, file: (String) -> String): Theme {

    val o = Json.parseToJsonElement(json).jsonObject
    val d = Theme.Default
    val bg = o["background"]?.jsonObject

    fun col(k: String, def: Color): Color {
      val h = o[k]?.jsonPrimitive?.contentOrNull?.removePrefix("#") ?: return def
      val v = h.toLongOrNull(16) ?: return def
      return when (h.length) { 6 -> Color(0xFF000000 or v); 8 -> Color(v); else -> def }
  }

    val background = when (bg?.get("type")?.jsonPrimitive?.contentOrNull) {
      "shader" -> {
        val src = file(bg!!.getValue("file").jsonPrimitive.content)
        require("resolution" in src && "time" in src) { "shader must declare uniforms 'resolution' and 'time'" }
        RuntimeEffect.makeForShader(src).close() // throws on compile error
        Background.Shader(src)
      }
      else -> Background.Plain(
        bg?.get("color")?.jsonPrimitive?.contentOrNull?.let { runCatching { Color(it.removePrefix("#").toLong(16) or 0xFF000000) }.getOrNull() }
          ?: Color(0xFF101014))
    }

    return Theme(
      id = id,
      name = o["name"]?.jsonPrimitive?.contentOrNull ?: id,
      dark = o["dark"]?.jsonPrimitive?.booleanOrNull ?: true,
      background = background,
      frame = col("frame", d.frame), border = col("border", d.border),
      glass = col("glass", d.glass), input = col("input", d.input),
      user = col("user", d.user), error = col("error", d.error),
      glassBorder = col("glassBorder", d.glassBorder),
      blur = (o["blur"]?.jsonPrimitive?.floatOrNull ?: 12f).dp,
      text = col("text", d.text), accent = col("accent", d.accent), codeBg = col("codeBg", d.codeBg),
      sendBg = col("sendBg", d.sendBg), sendFg = col("sendFg", d.sendFg),
      builtin = builtin,
    )
  }

  fun load(dir: Path): Theme =
    parse(dir.fileName.toString(), false, (dir / "theme.json").readText()) { rel ->
      val f = dir.resolve(rel).normalize()
      require(f.startsWith(dir.normalize()) && f.isRegularFile()) { "bad shader path" }
      f.readText()
    }.copy(thumbnail = { runCatching { (dir / "thumbnail.jpeg").readBytes() }.getOrNull() })

  private fun res(path: String) =
    ThemeLoader::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }

  fun bundled(): List<Theme> {
    val index = res("/themes/index.txt") ?: return emptyList()
    return index.lines().map { it.trim() }.filter { it.endsWith("/theme.json") }.mapNotNull { rel ->
      val folder = rel.substringBefore('/')
        runCatching {
          parse("builtin:$folder", true, res("/themes/$rel")!!) { f -> res("/themes/$folder/$f") ?: error("bad shader path") }
        }.getOrNull()?.copy(thumbnail = {
          ThemeLoader::class.java.getResourceAsStream("/themes/$folder/thumbnail.jpeg")?.use { it.readBytes() }
        })
    }
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
    reload()
    select(runCatching { saved.readText().trim() }.getOrDefault(""))
  }

  private fun fallback() = themes.firstOrNull { it.id == "builtin:dark" } ?: themes.first()

  fun reload() {
    val user = Files.list(dir).use { s -> s.filter { it.isDirectory() }.sorted().toList() }
      .mapNotNull { runCatching { ThemeLoader.load(it) }.getOrNull() }
    themes = (ThemeLoader.bundled() + user).ifEmpty { listOf(Theme.Default) }
    current = themes.firstOrNull { it.id == current.id } ?: fallback()
  }

  fun select(id: String) {
    current = themes.firstOrNull { it.id == id } ?: fallback()
    runCatching { saved.parent.createDirectories(); saved.writeText(current.id) }
  }

  fun import(src: Path) {
    error = runCatching { val id = ThemeLoader.import(src, dir); reload(); select(id); null }
      .getOrElse { "import failed: ${it.message}" }
  }
}

private const val TW = 192
private const val TH = 120

object ThumbnailCache {
  private val map = ConcurrentHashMap<String, ImageBitmap>()
  fun get(id: String, load: () -> ImageBitmap?): ImageBitmap? =
    map[id] ?: load()?.also { map[id] = it }
  fun clear() = map.clear()
}

fun interface ThumbnailDecoder {
  fun decode(bytes: ByteArray, w: Int, h: Int): ImageBitmap
}

val LocalThumbnailDecoder = staticCompositionLocalOf<ThumbnailDecoder> {
  ThumbnailDecoder { _, _, _ -> error("no ThumbnailDecoder provided") }
}

@Composable
private fun ThemeThumbnail(t: Theme, modifier: Modifier = Modifier) {
  val decoder = LocalThumbnailDecoder.current
  val img by produceState<ImageBitmap?>(null, t.id, decoder) {
    value = withContext(Dispatchers.IO) {
      runCatching { ThumbnailCache.get(t.id) { t.thumbnail()?.let { decoder.decode(it, TW, TH) } } }.getOrNull()
    }
  }
  val shape = RoundedCornerShape(6.dp)
  Box(modifier.aspectRatio(1.6f).clip(shape).border(1.dp, t.border, shape)) {
    val i = img
    if (i != null) {
      Image(i, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      Box(Modifier.fillMaxSize().background((t.background as? Background.Plain)?.color ?: t.frame))
      Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(50.dp, 8.dp).background(t.accent, RoundedCornerShape(6.dp)))
    }
  }
}

@Composable
private fun ThemeCard(t: Theme, selected: Boolean, onClick: () -> Unit) {
  val accent = LocalTheme.current.accent
  val shape = RoundedCornerShape(10.dp)
  Column(
    Modifier.clip(shape)
      .border(2.dp, if (selected) accent else Color.Transparent, shape)
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
      .padding(6.dp),
  ) {
    ThemeThumbnail(t, Modifier.fillMaxWidth())
    Text(t.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
  }
}

@Composable
fun ThemePicker(themes: ThemeState, onImport: () -> Unit, modifier: Modifier = Modifier) {
  val builtin = themes.themes.filter { it.builtin }
  val custom = themes.themes.filterNot { it.builtin }

  GlassPanel(
    Modifier.widthIn(min = 720.dp).heightIn(max = 560.dp).fillMaxHeight(0.85f),
    blur = 0.dp, tint = LocalTheme.current.frame, // opaque
  ) {
    Column(Modifier.fillMaxSize()) {
      LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.weight(1f),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(builtin, key = { it.id }) { t ->
          ThemeCard(t, t.id == themes.current.id) { themes.select(t.id) }
        }
        if (custom.isNotEmpty()) {
          item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Custom", Modifier.padding(top = 8.dp), fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
          }
          items(custom, key = { it.id }) { t ->
            ThemeCard(t, t.id == themes.current.id) { themes.select(t.id) }
          }
        }
      }
      HorizontalDivider()
      Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onImport) { Text("Import") }
        TextButton({ themes.reload() }) { Text("Reload") }
      }
    }
  }
}
