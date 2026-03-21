package com.github.antigravity.efile.breadcrumbs

import com.github.antigravity.efile.ELanguage
import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider

class EBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(ELanguage)

    override fun acceptElement(e: PsiElement): Boolean {
        val type = e.node.elementType
        return type == ETokenTypes.MODULE || type == ETokenTypes.DATA_SECTION
    }

    override fun getElementInfo(e: PsiElement): String {
        val type = e.node.elementType
        val text = e.text
        val firstLineEnd = text.indexOf('\n')
        val headerText = if (firstLineEnd > 0) text.substring(0, firstLineEnd) else text

        if (type == ETokenTypes.MODULE) {
            var moduleName = "Module"
            val s = headerText.indexOf('<')
            val eIdx = headerText.indexOf('>')
            if (s >= 0 && eIdx > s) {
                moduleName = headerText.substring(s + 1, eIdx).trim()
            }
            return moduleName
        }
        
        if (type == ETokenTypes.DATA_SECTION) {
            return headerText.replace(Regex("\\s+"), " ").trim()
        }
        
        return "Unknown"
    }

    override fun getElementTooltip(e: PsiElement): String? = null
}
