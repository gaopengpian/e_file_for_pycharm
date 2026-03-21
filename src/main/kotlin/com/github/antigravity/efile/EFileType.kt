package com.github.antigravity.efile

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon
import com.intellij.icons.AllIcons

object EFileType : LanguageFileType(ELanguage) {
    val INSTANCE = EFileType

    override fun getName(): String = "EFILE"

    override fun getDescription(): String = "Electric power system data markup language file"

    override fun getDefaultExtension(): String = "e"

    override fun getIcon(): Icon = AllIcons.FileTypes.Text
}
