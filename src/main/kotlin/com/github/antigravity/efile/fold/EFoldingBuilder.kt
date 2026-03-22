package com.github.antigravity.efile.fold

import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class EFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        
        // 1. Try PSI based (Standard)
        collectDescriptors(root.node, descriptors)
        
        // 2. If nothing found (likely Large File mode), use text scanning fallback
        if (descriptors.isEmpty()) {
            buildLargeFileFoldRegions(root, document, descriptors)
        }
        
        return descriptors.toTypedArray()
    }

    private fun buildLargeFileFoldRegions(root: PsiElement, document: Document, descriptors: MutableList<FoldingDescriptor>) {
        val text = document.text
        val lines = text.split('\n')
        var currentOffset = 0
        
        val startRegex = Regex("^\\s*<([^:>!/\\s]+)(?:::[^>]+)?>")
        val openModules = mutableListOf<Pair<String, Int>>() // Name to Offset
        
        for (line in lines) {
            val trimmed = line.trimStart()
            val startMatch = startRegex.find(line)
            if (startMatch != null) {
                val name = startMatch.groupValues[1]
                openModules.add(Pair(name, currentOffset + line.indexOf('<')))
            } else if (trimmed.startsWith("</")) {
                val endIdx = trimmed.indexOf('>')
                if (endIdx > 2) {
                    val name = trimmed.substring(2, endIdx).trim()
                    val lastMatchIdx = openModules.indexOfLast { it.first == name }
                    if (lastMatchIdx >= 0) {
                        val (startName, startPos) = openModules.removeAt(lastMatchIdx)
                        val endPosInLine = line.indexOf('>')
                        if (endPosInLine >= 0) {
                            val endPos = currentOffset + endPosInLine + 1
                            if (endPos > startPos + 5) {
                                // Extract placeholder from text
                                val placeholder = text.substring(startPos, text.indexOf('>', startPos) + 1) + "..."
                                descriptors.add(FoldingDescriptor(root.node, TextRange(startPos, endPos), null, placeholder))
                            }
                        }
                    }
                }
            }
            currentOffset += line.length + 1
        }
    }

    private fun collectDescriptors(node: ASTNode, descriptors: MutableList<FoldingDescriptor>) {
        if (node.elementType == ETokenTypes.MODULE) {
            // Fold the whole module, but verify valid range
            val textRange = node.textRange
            if (textRange.length > 2) { // minimal check
                 // We want to show the start tag in the collapsed text ideally
                 // Or just collapse the body.
                 // For now, collapse the whole thing or just body.
                 
                 // Let's find the header <...>
                 // We can't easily find children by type on ASTNode easily without iteration.
                 // Let's fold the entire MODULE.
                 descriptors.add(FoldingDescriptor(node, textRange))
            }
        }
        
        for (child in node.getChildren(null)) {
            collectDescriptors(child, descriptors)
        }
    }

    override fun getPlaceholderText(node: ASTNode): String? {
        // Try to extract name
        // The Module starts with < Name :: Type >
        // We can grab the text up to just >
        val text = node.text
        val firstClose = text.indexOf('>')
        if (firstClose > 0) {
            return text.substring(0, firstClose + 1) + "..."
        }
        return "<Module>..."
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
