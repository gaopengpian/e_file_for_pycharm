package com.github.antigravity.efile.highlight

import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.PsiElement
import com.intellij.openapi.editor.colors.TextAttributesKey

class ERainbowAnnotator : Annotator {
    private val RAINBOW_COLORS = arrayOf(
        DefaultLanguageHighlighterColors.KEYWORD, // Orange (Idx)
        DefaultLanguageHighlighterColors.STRING, // Green (Name)
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION, // Gold
        DefaultLanguageHighlighterColors.NUMBER, // Blue
        DefaultLanguageHighlighterColors.METADATA, // Yellow-ish
        DefaultLanguageHighlighterColors.INSTANCE_FIELD, // Purple
        DefaultLanguageHighlighterColors.CONSTANT // Purple/Bold
    )

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.node.elementType == ETokenTypes.IDENTIFIER || element.node.elementType == ETokenTypes.STRING || element.node.elementType == ETokenTypes.NUMBER || element.node.elementType == ETokenTypes.DOUBLE_COLON) {

            // 检查元素是否在 < 和 > 标签内容部（即模块声明头部）
            var isInsideTag = false
            var checkSibling = element.prevSibling
            while (checkSibling != null) {
                // 遇到换行停止搜索
                if (checkSibling.node.elementType == ETokenTypes.WHITE_SPACE && checkSibling.text.contains('\n')) break
                // 如果前面有 < 或 </ ，说明内部属于标签声明
                if (checkSibling.node.elementType == ETokenTypes.TAG_START || checkSibling.node.elementType == ETokenTypes.TAG_CLOSE_START) {
                    isInsideTag = true
                    break
                }
                checkSibling = checkSibling.prevSibling
            }

            if (isInsideTag) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .textAttributes(EHighlighterColors.TAG)
                    .create()
                return
            }
            
            // 非TAG内部的 DOUBLE_COLON 不需要彩虹着色
            if (element.node.elementType == ETokenTypes.DOUBLE_COLON) return

            // Logic: we need to determine if we are in a data scope.
            // Simplified logic: Check previous delimiter.
            // If the element is on a line that starts with '#', we treat it as data.
            // Or simpler: count the index of this element in the line.
            
            // Getting previous sibling to check for # is expensive if we traverse far.
            // But since this is a simple text based language for now...
            
            val text = element.containingFile.text
            val offset = element.textRange.startOffset
            val lineStart = text.lastIndexOf('\n', offset - 1) + 1
            if (lineStart < 0) return
            
            // Check if linestart has #
            var i = lineStart
            while (i < offset && Character.isWhitespace(text[i])) {
                i++
            }
            if (i >= text.length) return
            
            // If the line doesn't start with '#' or '@', maybe we shouldn't color.
            // The requirement says " 每个模块不同列和列数据自动着色".
            // Typically @ lines define columns, # lines define data.
            
            if (text[i] == '#' || text[i] == '@') {
                // Calculate column index (token index in line)
                var colIndex = 0
                var curr = i
                
                // We need to parse line simply properly to count tokens.
                // Re-lexing the line might be safer but slow?
                // Text basic split by whitespace is an approximation.
                
                // Let's rely on PSI siblings if possible.
                // But our ParserDefinition produces a flat list of LeafPsiElements.
                
                var sibling = element.prevSibling
                var count = 0
                var sawSpace = false
                
                while (sibling != null) {
                    val type = sibling.node.elementType
                    
                    // Stop at newline
                    if (type == ETokenTypes.WHITE_SPACE && sibling.text.contains('\n')) {
                        break
                    }
                    
                    // Markers @ and # stop the counting (they are not columns)
                    if (type == ETokenTypes.MARKER_AT || type == ETokenTypes.MARKER_AT_AT || type == ETokenTypes.MARKER_HASH) {
                        break
                    }
                    
                    if (type == ETokenTypes.WHITE_SPACE) {
                        sawSpace = true
                    } else if (type != ETokenTypes.COMMENT) {
                        // It is a token (IDENTIFIER, NUMBER, STRING, etc.)
                        // If we saw a space before this token, it means we jumped a column gap.
                        if (sawSpace) {
                            count++
                            sawSpace = false
                        }
                        // If sawSpace is false, it means this sibling is adjacent to the next one (e.g. A(B -> A is adjacent to (),
                        // so they belong to the same column group. We don't increment.
                    }
                    
                    sibling = sibling.prevSibling
                }
                
                val colorAttr = RAINBOW_COLORS[count % RAINBOW_COLORS.size]
                
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .textAttributes(colorAttr)
                    .create()
            }
        }
    }
}
