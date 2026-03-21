package com.github.antigravity.efile.io

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.FileEncodingProvider
import com.intellij.openapi.project.Project
import java.nio.charset.Charset

class EEncodingProvider : FileEncodingProvider {
    override fun getEncoding(virtualFile: VirtualFile, project: Project?): Charset? {
        val ext = virtualFile.extension?.lowercase() ?: return null
        if (ext == "e" || ext == "dt" || ext == "cime") {
             return Charset.forName("GBK")
        }
        return null
    }
}
