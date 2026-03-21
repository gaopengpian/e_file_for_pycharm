package com.github.antigravity.efile.structure

import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.ide.structureView.*
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class EStructureViewModel(psiFile: PsiFile) : TextEditorBasedStructureViewModel(psiFile) {
    override fun getRoot(): StructureViewTreeElement {
        return EFileTreeElement(psiFile)
    }

    override fun getSuitableClasses(): Array<Class<*>> {
        return arrayOf(PsiElement::class.java)
    }
}

class EFileTreeElement(private val element: PsiElement) : StructureViewTreeElement {
    override fun getValue(): Any = element
    override fun navigate(requestFocus: Boolean) {}
    override fun canNavigate(): Boolean = false
    override fun canNavigateToSource(): Boolean = false

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? = "E File"
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon? = null
        }
    }

    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        var child = element.firstChild
        while (child != null) {
            if (child.node.elementType == ETokenTypes.MODULE) {
                children.add(EModuleTreeElement(child))
            }
            child = child.nextSibling
        }
        return children.toTypedArray()
    }
}

class EModuleTreeElement(private val element: PsiElement) : StructureViewTreeElement {
    override fun getValue(): Any = element
    override fun navigate(requestFocus: Boolean) {
        val nav = element as? com.intellij.pom.Navigatable
        nav?.navigate(requestFocus)
    }
    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? {
                val text = element.text
                val firstLineEnd = text.indexOf('\n')
                val headerText = if (firstLineEnd > 0) text.substring(0, firstLineEnd) else text
                
                var moduleName = "Module"
                val s = headerText.indexOf('<')
                val e = headerText.indexOf('>')
                if (s >= 0 && e > s) {
                    moduleName = headerText.substring(s + 1, e).trim()
                }
                return moduleName
            }
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon? = null
        }
    }

    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        var child = element.firstChild
        while (child != null) {
            if (child.node.elementType == ETokenTypes.DATA_SECTION) {
                children.add(EDataSectionTreeElement(child))
            }
            child = child.nextSibling
        }
        return children.toTypedArray()
    }
}

class EDataSectionTreeElement(private val element: PsiElement) : StructureViewTreeElement {
    override fun getValue(): Any = element
    override fun navigate(requestFocus: Boolean) {
        val nav = element as? com.intellij.pom.Navigatable
        nav?.navigate(requestFocus)
    }
    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String? {
                val text = element.text
                val firstLineEnd = text.indexOf('\n')
                val headerText = if (firstLineEnd > 0) text.substring(0, firstLineEnd) else text
                // Return exactly the '@ id name' style text for sticky lines
                return headerText.replace(Regex("\\s+"), " ").trim()
            }
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon? = null
        }
    }

    override fun getChildren(): Array<TreeElement> {
        return emptyArray()
    }
}
