package com.laowang.idea.immersive.renderer.inlay

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import kotlin.math.ceil
import kotlin.math.max

class TranslationInlayRenderer(
    val text: String,
) : EditorCustomElementRenderer {
    private val lines: List<String> = text.lines().ifEmpty { listOf("") }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val graphics = inlay.editor.contentComponent.graphics ?: return fallbackWidth(inlay.editor)
        val fontMetrics = graphics.getFontMetrics(resolveFont(inlay.editor))
        val widestLine = lines.maxOfOrNull { line ->
            fontMetrics.stringWidth(line.ifEmpty { " " })
        } ?: fontMetrics.charWidth(' ')
        return max(widestLine, fontMetrics.charWidth(' '))
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        val lineHeight = max(inlay.editor.lineHeight, 1)
        return max(lines.size * lineHeight, lineHeight)
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics2D,
        targetRegion: Rectangle2D,
        textAttributes: TextAttributes,
    ) {
        val editor = inlay.editor
        val previousFont = g.font
        val previousColor = g.color
        val font = resolveFont(editor)
        val fontMetrics = g.getFontMetrics(font)
        val ascent = fontMetrics.ascent
        val baseX = ceil(targetRegion.x).toInt()
        val baseY = ceil(targetRegion.y).toInt()

        g.font = font
        g.color = resolveColor(editor)

        lines.forEachIndexed { index, line ->
            val y = baseY + ascent + index * fontMetrics.height
            g.drawString(line, baseX, y)
        }

        g.font = previousFont
        g.color = previousColor
    }

    private fun resolveFont(editor: Editor): Font =
        editor.colorsScheme.getFont(EditorFontType.PLAIN).deriveFont(Font.ITALIC)

    private fun resolveColor(editor: Editor): Color = Color.GRAY

    private fun fallbackWidth(editor: Editor): Int {
        val approximateCharWidth = max(editor.lineHeight / 2, 1)
        return lines.maxOfOrNull { max(it.length, 1) * approximateCharWidth } ?: approximateCharWidth
    }
}
