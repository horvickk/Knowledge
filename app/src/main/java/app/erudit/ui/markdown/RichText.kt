package app.erudit.ui.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Текст уроков и вопросов: разметка (`**жирный**`, `*курсив*`, `` `код` ``) и формулы — дроби
 * двухэтажные, корни с чертой над подкоренным выражением, степени «a^(1/2)» надстрочные.
 */
@Composable
fun RichText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    accent: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = style.copy(
        color = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } },
        fontSize = if (style.fontSize.isSpecified) style.fontSize else 16.sp,
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val laid = remember(text, resolved, accent, density, measurer) {
        val builder = RichBuilder(measurer, density)
        val rich = builder.build(text, resolved, accent, markdown = true, fractions = true)
        Laid(rich, builder.paragraph(rich, resolved))
    }
    Text(
        laid.rich.text,
        modifier = modifier.padding(top = laid.paragraph.top, bottom = laid.paragraph.bottom),
        style = laid.paragraph.style,
        inlineContent = laid.rich.inline,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private class Laid(val rich: Rich, val paragraph: Paragraph)

/** Стиль абзаца с формулами и запас сверху и снизу, если формула выше первой или последней строки. */
private class Paragraph(val style: TextStyle, val top: Dp, val bottom: Dp)

/** Готовый текст: строка с местами под формулы, сами формулы и стиль, которым всё измерено. */
private class Rich(
    val text: AnnotatedString,
    val inline: Map<String, InlineTextContent>,
    val placeholders: List<AnnotatedString.Range<Placeholder>>,
    val style: TextStyle,
    /** Высоты формул, px. */
    val heights: List<Float>,
    /** Символы, которые рисуются шрифтом, — по ним видно, есть ли хвосты ниже строки. */
    val glyphs: String,
    val raised: Boolean,
    val lowered: Boolean,
)

/**
 * Одна строка внутри дроби или корня. [ascent] и [descent] — сколько её видно над базовой линией
 * и под ней: шрифт оставляет над цифрами пустое место, и по его размерам формулы выходят рыхлыми.
 */
private class Line(val rich: Rich, val layout: TextLayoutResult, val ascent: Float, val descent: Float) {
    val width get() = layout.size.width.toFloat()
    val height get() = ascent + descent
}

/** Строка формулы — без переносов и без межстрочных отступов. */
@Composable
private fun RichLine(rich: Rich) {
    Text(rich.text, style = rich.style, inlineContent = rich.inline, softWrap = false, maxLines = 1)
}

/**
 * Формула из строк, поставленных базовыми линиями в заданные точки, и линий, нарисованных под ними.
 * Строки меряются без ограничений: место под формулу у́же их полей, и иначе текст обрезался бы.
 */
@Composable
private fun Formula(lines: List<Pair<Line, Offset>>, draw: DrawScope.() -> Unit) {
    Layout(
        content = { lines.forEach { RichLine(it.first.rich) } },
        modifier = Modifier.fillMaxSize().drawBehind(draw),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val (line, at) = lines[i]
                p.place(at.x.roundToInt(), (at.y - line.layout.firstBaseline).roundToInt())
            }
        }
    }
}

/** Буквы с хвостами вниз и скобки — с ними строка видна ниже базовой линии. */
private const val DEEP = "gjpqyQ(),;[]{}|βγζημξρςφχψруфдцщзДЦЩ₀₁₂₃₄₅₆₇₈₉"

private class RichBuilder(private val measurer: TextMeasurer, private val density: Density) {
    private var next = 0
    private val centers = mutableMapOf<Float, Float>()

    fun build(source: String, style: TextStyle, accent: Color?, markdown: Boolean, fractions: Boolean): Rich {
        val b = AnnotatedString.Builder()
        val inline = mutableMapOf<String, InlineTextContent>()
        val placeholders = mutableListOf<AnnotatedString.Range<Placeholder>>()
        val heights = mutableListOf<Float>()
        val glyphs = StringBuilder()
        var raised = false
        var lowered = false
        var bold = false
        var italic = false
        val boldStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = accent ?: Color.Unspecified)

        /** Стиль формулы в текущем месте текста: внутри **жирного** формула тоже жирная. */
        fun here(): TextStyle = style.copy(
            fontWeight = if (bold) FontWeight.SemiBold else style.fontWeight,
            fontStyle = if (italic) FontStyle.Italic else style.fontStyle,
            color = if (bold && accent != null) accent else style.color,
        )

        fun element(raw: String, e: Element) {
            heights += e.height
            val id = "math${next++}"
            val placeholder = Placeholder(
                with(density) { e.width.toSp() },
                with(density) { e.height.toSp() },
                PlaceholderVerticalAlign.TextCenter,
            )
            val start = b.length
            b.appendInlineContent(id, raw)
            placeholders += AnnotatedString.Range(placeholder, start, b.length)
            inline[id] = InlineTextContent(placeholder) { e.content() }
        }

        for (seg in MathParser.parse(source, fractions)) {
            when (seg) {
                is MathSeg.Plain -> {
                    glyphs.append(seg.text)
                    if (!markdown) {
                        b.append(seg.text)
                    } else {
                        val text = seg.text
                        var i = 0
                        while (i < text.length) {
                            val c = text[i]
                            when {
                                text.startsWith("**", i) -> {
                                    if (bold) b.pop() else b.pushStyle(boldStyle)
                                    bold = !bold
                                    i += 2
                                }
                                c == '*' && (italic || (i + 1 < text.length && !text[i + 1].isWhitespace())) -> {
                                    if (italic) b.pop() else b.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                                    italic = !italic
                                    i++
                                }
                                c == '`' && text.indexOf('`', i + 1) > i -> {
                                    val end = text.indexOf('`', i + 1)
                                    b.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.9.em)) { append(text.substring(i + 1, end)) }
                                    i = end + 1
                                }
                                else -> { b.append(c); i++ }
                            }
                        }
                    }
                }
                is MathSeg.Sup -> {
                    raised = true
                    glyphs.append(seg.text)
                    b.withStyle(SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(0.45f))) { append(seg.text) }
                }
                is MathSeg.Sub -> {
                    lowered = true
                    b.withStyle(SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(-0.2f))) { append(seg.text) }
                }
                is MathSeg.Frac -> element(seg.raw, fraction(seg, here()))
                is MathSeg.Root -> element(seg.raw, root(seg, here()))
            }
        }
        return Rich(b.toAnnotatedString(), inline, placeholders, style, heights, glyphs.toString(), raised, lowered)
    }

    /**
     * Строка с заданной высотой не раздвигается под формулу, поэтому межстрочный интервал абзаца
     * увеличивается, только если формула иначе наедет на соседние строки.
     */
    fun paragraph(rich: Rich, style: TextStyle): Paragraph {
        val tall = rich.heights.sortedDescending()
        if (tall.isEmpty()) return Paragraph(style, 0.dp, 0.dp)
        val em = px(style)
        val c = center(style)
        val line = when {
            style.lineHeight.isSp -> with(density) { style.lineHeight.toPx() }
            style.lineHeight.isEm -> style.lineHeight.value * em
            else -> 1.25f * em
        }
        // Над формулой — хвосты букв предыдущей строки, а если формулы в соседних строках, то друг над другом.
        var need = tall[0] / 2 + c + 0.29f * em
        if (tall.size > 1) need = max(need, (tall[0] + tall[1]) / 2 + 0.08f * em)
        val final = if (need > line) style.copy(lineHeight = with(density) { need.toSp() }) else style
        val natural = measurer.measure("0", plain(style))
        val ascent = natural.firstBaseline
        val descent = natural.size.height - ascent
        return with(density) {
            Paragraph(
                final,
                max(0f, tall[0] / 2 + c - ascent).toDp(),
                max(0f, tall[0] / 2 - c - descent).toDp(),
            )
        }
    }

    private class Element(val width: Float, val height: Float, val content: @Composable () -> Unit)

    private fun px(style: TextStyle): Float = with(density) { style.fontSize.toPx() }

    /** Стиль строки формулы: высота строки — по шрифту, без интервала абзаца. */
    private fun plain(style: TextStyle) = style.copy(lineHeight = TextUnit.Unspecified, lineHeightStyle = null)

    /** На сколько выше базовой линии середина строки: по ней выравниваются формулы. */
    private fun center(style: TextStyle): Float = centers.getOrPut(px(style)) {
        val layout = measurer.measure("0", plain(style))
        layout.firstBaseline - layout.size.height / 2f
    }

    private fun line(source: String, style: TextStyle, fractions: Boolean): Line {
        val rich = build(source, plain(style), null, markdown = false, fractions = fractions)
        val layout = measurer.measure(rich.text, rich.style, softWrap = false, maxLines = 1, placeholders = rich.placeholders)
        val em = px(style)
        val c = center(style)
        val brackets = rich.glyphs.any { it in "()[]{}|" }
        var ascent = em * if (rich.raised) 0.85f else if (brackets) 0.8f else 0.74f
        var descent = em * if (rich.lowered || rich.glyphs.any { it in DEEP }) 0.21f else 0.04f
        rich.heights.forEach { h ->
            ascent = max(ascent, c + h / 2)
            descent = max(descent, h / 2 - c)
        }
        return Line(rich, layout, ascent, descent)
    }

    /** Дробь: числитель над чертой, знаменатель под ней; черта — на середине строки. */
    private fun fraction(seg: MathSeg.Frac, style: TextStyle): Element {
        val em = px(style)
        val part = style.copy(fontSize = style.fontSize * 0.76f)
        val num = line(seg.num, part, fractions = false)
        val den = line(seg.den, part, fractions = false)
        val pad = 0.1f * em
        val gap = 0.07f * em
        val bar = max(1f, 0.055f * em)
        val half = max(num.height, den.height) + gap
        val width = max(num.width, den.width) + 2 * pad
        val height = 2 * half + bar
        val color = style.color
        val lines = listOf(
            num to Offset((width - num.width) / 2, half - gap - num.descent),
            den to Offset((width - den.width) / 2, half + bar + gap + den.ascent),
        )
        return Element(width, height) {
            Formula(lines) { drawRect(color, Offset(pad / 2, half), Size(width - pad, bar)) }
        }
    }

    /** Корень: знак рисуется линией и переходит в черту над всем подкоренным выражением. */
    private fun root(seg: MathSeg.Root, style: TextStyle): Element {
        val em = px(style)
        val c = center(style)
        val r = line(seg.radicand, style, fractions = true)
        val index = when (seg.sign) {
            '∛' -> "3"
            '∜' -> "4"
            else -> null
        }
        val stroke = max(1f, 0.06f * em)
        val above = stroke + 0.08f * em
        val hook = if (index != null) 0.22f * em else 0f
        val sign = 0.5f * em + hook
        val width = sign + r.width + 0.08f * em
        // Середина формулы встаёт на середину строки, а базовая линия подкоренного выражения — на её базовую линию.
        val half = max(r.ascent + above - c, c + r.descent)
        val height = 2 * half
        val baseline = half + c
        val top = baseline - r.ascent
        val bottom = baseline + r.descent
        val color = style.color
        val indexLayout = index?.let { measurer.measure(it, plain(style.copy(fontSize = style.fontSize * 0.45f))) }
        return Element(width, height) {
            Formula(listOf(r to Offset(sign, baseline))) {
                val tall = bottom - top
                val path = Path().apply {
                    moveTo(hook + 0.02f * em, top + tall * 0.62f)
                    lineTo(hook + 0.12f * em, top + tall * 0.54f)
                    lineTo(hook + 0.27f * em, bottom)
                    lineTo(sign - 0.04f * em, stroke / 2)
                    lineTo(width - 0.02f * em, stroke / 2)
                }
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (indexLayout != null) {
                    drawText(indexLayout, color, Offset(0f, top + tall * 0.5f - indexLayout.firstBaseline))
                }
            }
        }
    }
}
