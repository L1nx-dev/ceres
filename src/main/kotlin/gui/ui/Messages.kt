package ceres.gui.common

import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.*
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

@Composable
fun MessageList(
  lines: List<Line>, 
  listState: LazyListState = rememberLazyListState(), 
  modifier: Modifier = Modifier
) {
  val grouped by remember(lines) { derivedStateOf { group(lines) } }
  val open = remember { mutableStateMapOf<Long, Boolean>() }

  LaunchedEffect(grouped.size) {
    if (grouped.isNotEmpty()) listState.animateScrollToItem(grouped.lastIndex)
  }

  SelectionContainer(modifier = modifier) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      state = listState,
      contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      items(grouped, key = { it.key }) { item ->
        when (item) {
          is Single -> MessageRow(item.line)
          is Steps -> StepsRow(item.lines, open[item.key] == true) { open[item.key] = open[item.key] != true }
        }
      }
    }
  }
}

@Composable
fun MessageRow(line: Line) {
  when (line.role) {
    LineRole.USER -> Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
      GlassPanel(Modifier.widthIn(max = 560.dp), blur = LocalTheme.current.blur, tint = LocalTheme.current.user) {
        Text(line.text, Modifier.padding(12.dp))
      }
    }

//    LineRole.AGENT -> Box(Modifier.fillMaxWidth(), Alignment.CenterStart) {
//      GlassPanel(Modifier.widthIn(max = 900.dp), blur = LocalTheme.current.blur) {
//        val parsed = rememberMarkdownState(line.text, retainState = true, immediate = true)
//        Markdown(parsed, modifier = Modifier.padding(12.dp))
//      }
//    }

    LineRole.AGENT -> Box(Modifier.fillMaxWidth(), Alignment.CenterStart) {
      GlassPanel(Modifier.widthIn(max = 900.dp), blur = LocalTheme.current.blur) {
      val theme = LocalTheme.current
      val hl = remember(theme.dark) { 
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = theme.dark)) 
      }
      val components = markdownComponents(
        codeBlock = { 
          MarkdownHighlightedCodeBlock(
            content = it.content, 
            node = it.node, 
            highlightsBuilder = hl,
            showHeader = true
          ) 
        },
        codeFence = { 
          MarkdownHighlightedCodeFence(
            content = it.content, 
            node = it.node, 
            highlightsBuilder = hl,
            showHeader = true
          )
        },
      )
      val parsed = rememberMarkdownState(line.text, retainState = true, immediate = true)
      Markdown(parsed, components = components, modifier = Modifier.padding(12.dp))
      }
    }

    LineRole.TOOL -> Box(Modifier.fillMaxWidth(), Alignment.CenterStart) {
      Text(
        line.text,
        Modifier.padding(horizontal = 8.dp).widthIn(max = 640.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontSize = 12.sp,
      )
    }

    LineRole.ERROR -> Box(Modifier.fillMaxWidth(), Alignment.Center) {
      GlassPanel(Modifier.fillMaxWidth().widthIn(max = 640.dp), blur = 0.dp, tint = LocalTheme.current.error) {
        Text(line.text, Modifier.padding(12.dp))
      }
    }
    LineRole.THINKING -> {}
  }
}

sealed interface Item { val key: Long }
class Single(val line: Line) : Item { override val key get() = line.id }
class Steps(val lines: List<Line>) : Item { override val key get() = lines.first().id }

fun group(lines: List<Line>): List<Item> = buildList {
  var run = mutableListOf<Line>()
  fun flush() { if (run.isNotEmpty()) { add(Steps(run)); run = mutableListOf() } }
  for (l in lines) {
    if (l.role == LineRole.TOOL || l.role == LineRole.THINKING) run += l
    else { flush(); add(Single(l)) }
  }
  flush()
}

@Composable
fun StepsRow(lines: List<Line>, open: Boolean, onToggle: () -> Unit) {
  val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
  val label = "${lines.size} " + if (lines.size == 1) "step" else "steps"
  Column(Modifier.padding(horizontal = 8.dp)) {
    Text((if (open) "▾ " else "▸ ") + label, Modifier.clickable(onClick = onToggle), color = muted, fontSize = 12.sp)
    AnimatedVisibility(open) {
      Column(Modifier.padding(start = 12.dp, top = 4.dp)) {
        lines.forEach { Text(it.text, color = muted, fontSize = 12.sp) }
      }
    }
  }
}
