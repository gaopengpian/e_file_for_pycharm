package com.github.antigravity.efile.lang

import com.github.antigravity.efile.EFileType
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.extapi.psi.ASTWrapperPsiElement

class EParserDefinition : ParserDefinition {
    companion object {
        val FILE = IFileElementType(EFileType.INSTANCE.language)
        val COMMENTS = TokenSet.create(ETokenTypes.COMMENT)
        val STRINGS = TokenSet.create(ETokenTypes.STRING)
    }

    override fun createLexer(project: Project?): Lexer = ELexer()

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    // Recursive Parser
    override fun createParser(project: Project?): PsiParser {
        return PsiParser { root, builder ->
             val rootMarker = builder.mark()
             var moduleMarker: com.intellij.lang.PsiBuilder.Marker? = null
             var sectionMarker: com.intellij.lang.PsiBuilder.Marker? = null
             var rowMarker: com.intellij.lang.PsiBuilder.Marker? = null

             fun closeRow() {
                 rowMarker?.done(ETokenTypes.DATA_ROW)
                 rowMarker = null
             }
             fun closeSection() {
                 closeRow()
                 sectionMarker?.done(ETokenTypes.DATA_SECTION)
                 sectionMarker = null
             }
             fun closeModule() {
                 closeSection()
                 moduleMarker?.done(ETokenTypes.MODULE)
                 moduleMarker = null
             }

             while (!builder.eof()) {
                 val type = builder.tokenType
                 
                 if (type == ETokenTypes.TAG_START) {
                     closeModule()
                     moduleMarker = builder.mark()
                     builder.advanceLexer() // Consume <
                 } else if (type == ETokenTypes.TAG_CLOSE_START) {
                     closeSection()
                     builder.advanceLexer()
                     while (!builder.eof() && builder.tokenType != ETokenTypes.TAG_END) {
                         builder.advanceLexer()
                     }
                     if (!builder.eof() && builder.tokenType == ETokenTypes.TAG_END) {
                         builder.advanceLexer() // Consume >
                     }
                     moduleMarker?.done(ETokenTypes.MODULE)
                     moduleMarker = null
                 } else if (type == ETokenTypes.MARKER_AT || type == ETokenTypes.MARKER_AT_AT) {
                     closeSection()
                     sectionMarker = builder.mark()
                     builder.advanceLexer()
                 } else if (type == ETokenTypes.MARKER_HASH || type == ETokenTypes.MARKER_DOLLAR || type == ETokenTypes.MARKER_PERCENT || type == ETokenTypes.MARKER_COMMENT) {
                     closeRow()
                     rowMarker = builder.mark()
                     builder.advanceLexer()
                 } else {
                     builder.advanceLexer()
                 }
             }
             
             closeModule()
             
             rootMarker.done(root)
             builder.treeBuilt
        }
    }

    override fun getFileNodeType(): IFileElementType = FILE

    override fun createFile(viewProvider: FileViewProvider): PsiFile = EFile(viewProvider)

    override fun createElement(node: ASTNode): PsiElement {
        if (node.elementType == ETokenTypes.MODULE) {
            return ASTWrapperPsiElement(node)
        }
        return ASTWrapperPsiElement(node)
    }
}

// We need EFile

class EFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, EFileType.INSTANCE.language) {
    override fun getFileType() = EFileType.INSTANCE
    override fun toString() = "E File"
}
