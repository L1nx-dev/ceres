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


@Composable
fun InputBar(
  busy: Boolean,
  onSend: (String) -> Unit,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  var tf by remember { mutableStateOf(TextFieldValue()) }

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
        onValueChange = { tf = it; if (it.text.isEmpty()) expanded = false },
        textStyle = style,
        cursorBrush = SolidColor(fg),
        maxLines = 30,
        onTextLayout = { if (it.lineCount > 1) expanded = true },
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            start = 18.dp,
            end = if (expanded) 18.dp else 60.dp,
            top = 14.dp,
            bottom = if (expanded) 52.dp else 14.dp,
          )
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
