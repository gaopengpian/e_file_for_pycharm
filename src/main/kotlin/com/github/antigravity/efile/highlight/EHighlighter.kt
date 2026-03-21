package com.github.antigravity.efile.highlight

import com.github.antigravity.efile.lang.ELexer
import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

object EHighlighterColors {
    val COMMENT = TextAttributesKey.createTextAttributesKey("E_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val TAG = TextAttributesKey.createTextAttributesKey("E_TAG", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
    val MARKER = TextAttributesKey.createTextAttributesKey("E_MARKER", DefaultLanguageHighlighterColors.METADATA)
    val STRING = TextAttributesKey.createTextAttributesKey("E_STRING", DefaultLanguageHighlighterColors.STRING)
    val NUMBER = TextAttributesKey.createTextAttributesKey("E_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val IDENTIFIER = TextAttributesKey.createTextAttributesKey("E_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
}

class EHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = ELexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            ETokenTypes.COMMENT, ETokenTypes.MARKER_COMMENT -> arrayOf(EHighlighterColors.COMMENT)
            ETokenTypes.TAG_START, ETokenTypes.TAG_END, ETokenTypes.TAG_CLOSE_START,
            ETokenTypes.TAG_INFO_START, ETokenTypes.TAG_INFO_END, ETokenTypes.TAG_IDENTIFIER -> arrayOf(EHighlighterColors.TAG)
            ETokenTypes.MARKER_AT, ETokenTypes.MARKER_AT_AT, ETokenTypes.MARKER_HASH,
            ETokenTypes.MARKER_DOLLAR, ETokenTypes.MARKER_PERCENT -> arrayOf(EHighlighterColors.MARKER)
            ETokenTypes.STRING -> arrayOf(EHighlighterColors.STRING)
            ETokenTypes.NUMBER -> arrayOf(EHighlighterColors.NUMBER)
            ETokenTypes.IDENTIFIER -> arrayOf(EHighlighterColors.IDENTIFIER)
            ETokenTypes.BAD_CHARACTER -> arrayOf(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE)
            else -> emptyArray()
        }
    }
}

class EHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = EHighlighter()
}
