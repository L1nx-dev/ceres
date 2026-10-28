package ceres.gui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextLayoutResult

private val headingRe = Regex("""(?m)^#{1,6} .*$""")
private val boldRe    = Regex("""\*\*(.+?)\*\*""")
private val italicRe  = Regex("""(?<![*\w])\*(?!\*)(.+?)(?<!\*)\*(?![*\w])""")
private val codeRe    = Regex("""`[^`\n]+`""")
private val fenceRe   = Regex("""```[\s\S]*?(```|$)""")

private val monoFont = SpanStyle(fontFamily = FontFamily.Monospace)
private val listRe = Regex("""(?m)^[ \t]*([-*+]|\d+[.)])(?= )""")

private val mono = SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33FFFFFF))

private val mdTransformation = VisualTransformation { text ->
  val s = text.text
  val styled = buildAnnotatedString {
    append(s)
    fun mark(re: Regex, st: SpanStyle) =
      re.findAll(s).forEach { addStyle(st, it.range.first, it.range.last + 1) }
    mark(headingRe, SpanStyle(fontWeight = FontWeight.Bold))
    mark(boldRe, SpanStyle(fontWeight = FontWeight.Bold))
    mark(italicRe, SpanStyle(fontStyle = FontStyle.Italic))
    mark(codeRe, mono)
    listRe.findAll(s).forEach { m ->
      m.groups[1]?.range?.let {
        addStyle(SpanStyle(color = Color(0xFF8AB4F8), fontWeight = FontWeight.Bold), it.first, it.last + 1)
      }
    }
    mark(fenceRe, monoFont)
  }
  TransformedText(styled, OffsetMapping.Identity)
}

@Composable
fun InputBar(
  busy: Boolean,
  onSend: (String) -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  var tf by remember { mutableStateOf(TextFieldValue()) }

  var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

  fun send() {
    val t = tf.text.trim()
    if (busy || t.isEmpty()) return
    onSend(t)
    tf = TextFieldValue()
    expanded = false
  }

  val fg = MaterialTheme.colorScheme.onSurface
  val style = TextStyle(color = fg, fontSize = 15.sp, lineHeight = 20.sp)

  GlassPanel(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    blur = 12.dp, tint = Color(0xCC15151A)
  ) {
    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
      BasicTextField(
        value = tf,
        visualTransformation = mdTransformation,
        onValueChange = { tf = it; if (it.text.isEmpty()) expanded = false },
        textStyle = style,
        cursorBrush = SolidColor(fg),
        maxLines = 30,
        onTextLayout = { layout = it; if (it.lineCount > 1) expanded = true },
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = 18.dp,
            end = if (expanded) 18.dp else 60.dp,
            top = 14.dp,
            bottom = if (expanded) 52.dp else 14.dp,
          )
          .drawBehind {
            val l = layout ?: return@drawBehind
            val text = l.layoutInput.text.text
            fenceRe.findAll(text).forEach { m ->
              val top = l.getLineTop(l.getLineForOffset(m.range.first))
              val bottom = l.getLineBottom(l.getLineForOffset(m.range.last))
              drawRoundRect(
                Color(0x33FFFFFF),
                topLeft = Offset(-6.dp.toPx(), top),
                size = Size(size.width + 12.dp.toPx(), bottom - top),
                cornerRadius = CornerRadius(8.dp.toPx()),
              )
            }
          }
          .onPreviewKeyEvent {
            if (it.type != KeyEventType.KeyDown || it.key != Key.Enter) return@onPreviewKeyEvent false
            if (it.isShiftPressed) {
              val s = tf.selection
              tf = TextFieldValue(tf.text.replaceRange(s.min, s.max, "\n"), TextRange(s.min + 1))
            } else send()
            true
          },
        decorationBox = { inner ->
          Box {
            if (tf.text.isEmpty()) Text("message", style = style.copy(color = fg.copy(alpha = 0.4f)))
            inner()
          }
        },
      )
      Button(
        onClick = { if (busy) onStop() else send() },
        modifier = Modifier
          .align(if (expanded) Alignment.BottomEnd else Alignment.CenterEnd)
          .padding(6.dp)
          .size(36.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
      ) { Text(if (busy) "■" else "↵") }
    }
  }
}
