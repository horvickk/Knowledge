package app.erudit.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.LocalReadingScale
import app.erudit.ui.theme.Serif

sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Numbered(val items: List<String>) : Block
    data class KeyIdea(val text: String) : Block
    data class Fact(val text: String) : Block
    data class Formula(val text: String) : Block
    data class Table(val rows: List<List<String>>) : Block
    data object Rule : Block
}

/** Разбор упрощённого markdown уроков в блоки. */
object Markdown {
    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = text.lines()
        var i = 0
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }

        while (i < lines.size) {
            val line = lines[i].trimEnd()
            when {
                line.isBlank() -> { flushParagraph(); i++ }
                line.startsWith("### ") -> { flushParagraph(); blocks += Block.Heading(3, line.substring(4)); i++ }
                line.startsWith("## ") -> { flushParagraph(); blocks += Block.Heading(2, line.substring(3)); i++ }
                line.startsWith("# ") -> { flushParagraph(); blocks += Block.Heading(2, line.substring(2)); i++ }
                line == "---" -> { flushParagraph(); blocks += Block.Rule; i++ }
                line.startsWith("$$ ") -> { flushParagraph(); blocks += Block.Formula(line.substring(3).trim()); i++ }
                line.startsWith("!> ") || line.startsWith("> ") -> {
                    flushParagraph()
                    val fact = line.startsWith("!> ")
                    val sb = StringBuilder(line.removePrefix("!> ").removePrefix("> "))
                    i++
                    while (i < lines.size && lines[i].startsWith(">") && !lines[i].startsWith(">!")) {
                        sb.append(' ').append(lines[i].removePrefix(">").trim())
                        i++
                    }
                    blocks += if (fact) Block.Fact(sb.toString()) else Block.KeyIdea(sb.toString())
                }
                line.startsWith("- ") || line.startsWith("• ") -> {
                    flushParagraph()
                    val items = mutableListOf<String>()
                    while (i < lines.size && (lines[i].startsWith("- ") || lines[i].startsWith("• ") || lines[i].startsWith("  "))) {
                        val l = lines[i]
                        if (l.startsWith("  ") && items.isNotEmpty()) items[items.lastIndex] += " " + l.trim()
                        else items += l.substring(2).trim()
                        i++
                    }
                    blocks += Block.Bullets(items)
                }
                NUMBERED.matches(line) -> {
                    flushParagraph()
                    val items = mutableListOf<String>()
                    while (i < lines.size && (NUMBERED.matches(lines[i]) || (lines[i].startsWith("   ") && items.isNotEmpty()))) {
                        val l = lines[i]
                        if (NUMBERED.matches(l)) items += l.substringAfter(". ").trim()
                        else items[items.lastIndex] += " " + l.trim()
                        i++
                    }
                    blocks += Block.Numbered(items)
                }
                line.startsWith("|") -> {
                    flushParagraph()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                        if (!cells.all { it.matches(Regex(":?-{2,}:?")) }) rows += cells
                        i++
                    }
                    blocks += Block.Table(rows)
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line.trim())
                    i++
                }
            }
        }
        flushParagraph()
        return blocks
    }

    private val NUMBERED = Regex("^\\d{1,2}\\. .*")

    /** `**жирный**`, `*курсив*`, `` `код` `` → AnnotatedString. */
    fun inline(text: String, accent: Color? = null): AnnotatedString = buildAnnotatedString {
        var i = 0
        var bold = false
        var italic = false
        val boldStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = accent ?: Color.Unspecified)
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("**", i) -> {
                    if (bold) pop() else pushStyle(boldStyle)
                    bold = !bold
                    i += 2
                }
                c == '*' && (italic || (i + 1 < text.length && !text[i + 1].isWhitespace())) -> {
                    if (italic) pop() else pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    italic = !italic
                    i++
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.9.em))
                        append(text.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append(c); i++
                    }
                }
                else -> { append(c); i++ }
            }
        }
    }
}

@Composable
fun MarkdownBlock(block: Block, accent: Color, modifier: Modifier = Modifier) {
    val scale = LocalReadingScale.current
    val body = TextStyle(
        fontFamily = Serif,
        fontSize = (17 * scale).sp,
        lineHeight = (27 * scale).sp,
        color = MaterialTheme.colorScheme.onBackground,
    )
    when (block) {
        is Block.Heading -> Text(
            block.text,
            style = if (block.level == 2) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            modifier = modifier.padding(top = if (block.level == 2) 28.dp else 20.dp, bottom = 8.dp),
        )
        is Block.Paragraph -> RichText(block.text, style = body, modifier = modifier.padding(vertical = 7.dp))
        is Block.Bullets -> Column(modifier.padding(vertical = 6.dp)) {
            block.items.forEach { item ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("—", style = body.copy(color = accent), modifier = Modifier.width(26.dp))
                    RichText(item, style = body)
                }
            }
        }
        is Block.Numbered -> Column(modifier.padding(vertical = 6.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${index + 1}.", style = body.copy(color = accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(30.dp))
                    RichText(item, style = body)
                }
            }
        }
        is Block.KeyIdea -> Row(
            modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent))
            Spacer(Modifier.width(16.dp))
            RichText(
                block.text,
                style = body.copy(fontSize = (18 * scale).sp, lineHeight = (28 * scale).sp, fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        is Block.Fact -> Column(
            modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(accent.copy(alpha = 0.08f))
                .padding(16.dp),
        ) {
            Text("ИНТЕРЕСНО", style = MaterialTheme.typography.labelSmall, color = accent)
            Spacer(Modifier.height(6.dp))
            RichText(block.text, style = body.copy(fontSize = (16 * scale).sp, lineHeight = (25 * scale).sp))
        }
        is Block.Formula -> Box(modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            RichText(
                block.text,
                style = body.copy(fontSize = (20 * scale).sp, lineHeight = (32 * scale).sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        is Block.Table -> TableBlock(block, scale, modifier)
        Block.Rule -> Box(
            modifier.fillMaxWidth().padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("· · ·", color = AppTheme.extra.faint, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TableBlock(block: Block.Table, scale: Float, modifier: Modifier) {
    if (block.rows.isEmpty()) return
    val columns = block.rows.maxOf { it.size }
    val line = AppTheme.extra.line
    Box(
        modifier
            .padding(vertical = 10.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            block.rows.forEachIndexed { r, row ->
                Row(
                    Modifier
                        .background(if (r == 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                        .height(IntrinsicSize.Min),
                ) {
                    for (c in 0 until columns) {
                        RichText(
                            row.getOrElse(c) { "" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = (14 * scale).sp,
                                lineHeight = (20 * scale).sp,
                                fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            modifier = Modifier
                                .widthIn(min = 72.dp, max = 220.dp)
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                }
                if (r < block.rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(line))
            }
        }
    }
}
