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
        val modules = PsiTreeUtil.findChildrenOfType(root, PsiElement::class.java) // Naive traversal or use recursion
        
        // Actually since we have MODULE elements, we can find them
        collectDescriptors(root.node, descriptors)
        
        return descriptors.toTypedArray()
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
