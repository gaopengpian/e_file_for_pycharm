package com.github.antigravity.efile

import com.intellij.lang.Language

object ELanguage : Language("E-File") {
    private fun readResolve(): Any = ELanguage
}
