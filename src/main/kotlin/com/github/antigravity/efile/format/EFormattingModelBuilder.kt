package com.github.antigravity.efile.format

import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

class EFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val file = formattingContext.psiElement.containingFile
        
        if (file.textLength > 1 * 1024 * 1024) {
             NotificationGroupManager.getInstance()
                .getNotificationGroup("E-File Notification")
                .createNotification("Formatting Skipped", "File is larger than 1MB. Formatting is disabled for performance.", NotificationType.WARNING)
                .notify(file.project)
        }
    
        return FormattingModelProvider.createFormattingModelForPsiFile(
            file,
            EBlock(formattingContext.node, null, null, formattingContext.codeStyleSettings),
            formattingContext.codeStyleSettings
        )
    }
}

class EBlock(
    node: ASTNode,
    wrap: Wrap?,
    alignment: Alignment?,
    private val settings: CodeStyleSettings
) : AbstractBlock(node, wrap, alignment) {

    // Flag to determine if this block is too large to format
    private fun isTooLarge(): Boolean {
        // Threshold: 1MB
        val THRESHOLD = 1 * 1024 * 1024
        
        if (myNode.elementType == ETokenTypes.MODULE) {
             val psi = myNode.psi
             if (psi != null && psi.containingFile.textLength > THRESHOLD) return true
             if (myNode.textLength > THRESHOLD) return true
        }
        return false
    }

    override fun buildChildren(): List<Block> {
        // If this is a leaf (IDENTIFIER, etc) or too large, no children
        if (isLeaf()) return emptyList()

        if (myNode.elementType == ETokenTypes.MODULE || myNode.psi is com.intellij.psi.PsiFile) {
            return buildModuleChildren(myNode)
        }

        // Default behavior for other nodes
        val blocks = mutableListOf<Block>()
        var child = myNode.firstChildNode
        
        while (child != null) {
            if (child.elementType != ETokenTypes.WHITE_SPACE) {
                blocks.add(EBlock(child, null, null, settings))
            }
            child = child.treeNext
        }
        return blocks
    }
    
    private fun buildModuleChildren(moduleNode: ASTNode): List<Block> {
        val blocks = mutableListOf<Block>()
        
        // Context-aware alignment: Generate alignments on the fly
        val columnAlignments = ArrayList<Alignment>()
        
        var isDataLine = false
        var colIndex = 0
        var sawSpace = false
        var hasSeenContent = false
        
        fun processNode(node: ASTNode) {
            if (node.elementType == ETokenTypes.DATA_SECTION || node.elementType == ETokenTypes.DATA_ROW) {
                var c = node.firstChildNode
                while (c != null) {
                    processNode(c)
                    c = c.treeNext
                }
            } else {
                val type = node.elementType
                
                // Handle Whitespace/Newline
                if (type == ETokenTypes.WHITE_SPACE) {
                    if (node.text.contains('\n')) {
                        // Reset for next line
                        isDataLine = false
                        colIndex = 0
                        sawSpace = false
                        hasSeenContent = false
                    } else {
                        sawSpace = true
                    }
                    return
                }
                
                // Content Block
                // Determine if this is a data line start (Markers)
                if (!hasSeenContent) {
                    if (type == ETokenTypes.MARKER_HASH || type == ETokenTypes.MARKER_AT || type == ETokenTypes.MARKER_AT_AT) {
                        isDataLine = true
                    }
                    hasSeenContent = true
                }
                
                // Alignment Logic
                var myAlignment: Alignment? = null
                val isMarker = type == ETokenTypes.MARKER_HASH || type == ETokenTypes.MARKER_AT || type == ETokenTypes.MARKER_AT_AT
                
                if (isDataLine && !isMarker && type != ETokenTypes.COMMENT) {
                     // Check gap (if we saw space since last content, increment column)
                     if (sawSpace) {
                         colIndex++
                         sawSpace = false // consumed
                     }
                     
                     // Get/Create Alignment
                     while (colIndex >= columnAlignments.size) {
                         // Allow backward shift (standard)
                         columnAlignments.add(Alignment.createAlignment(true))
                     }
                     myAlignment = columnAlignments[colIndex]
                }

                blocks.add(EBlock(node, null, myAlignment, settings))
                sawSpace = false 
            }
        }
        
        var child = moduleNode.firstChildNode
        while (child != null) {
            processNode(child)
            child = child.treeNext
        }
        return blocks
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (child1 !is EBlock || child2 !is EBlock) return null
        
        val node1 = child1.node
        val node2 = child2.node
        val type1 = node1.elementType
        val type2 = node2.elementType
        
        // Tags: < tag >, </ tag >
        // < (TAG_START) -> No space
        if (type1 == ETokenTypes.TAG_START || type1 == ETokenTypes.TAG_CLOSE_START || type1 == ETokenTypes.TAG_INFO_START) {
            return Spacing.createSpacing(0, 0, 0, false, 0)
        }
        // -> > (TAG_END) No space
        if (type2 == ETokenTypes.TAG_END || type2 == ETokenTypes.TAG_INFO_END) {
             return Spacing.createSpacing(0, 0, 0, false, 0)
        }
        
        // General:
        // 1 space min, 100 max (alignment)
        // 0 minLines (don't force newline)
        // keepLineBreaks = true (don't delete newline)
        // keepBlankLines = 100 (allow blank lines)
        return Spacing.createSpacing(1, 100, 0, true, 100)
    }

    override fun getIndent(): Indent? {
        return Indent.getNoneIndent()
    }

    override fun isLeaf(): Boolean {
        if (isTooLarge()) return true
        return myNode.firstChildNode == null
    }
}
