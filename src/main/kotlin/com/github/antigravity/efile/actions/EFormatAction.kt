package com.github.antigravity.efile.actions

import com.github.antigravity.efile.EFileType
import com.github.antigravity.efile.EPluginLogger
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.openapi.command.WriteCommandAction

import com.github.antigravity.efile.lang.ELexer
import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager

class EFormatAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) ?: return

        // Only run on E-Files
        if (file.fileType != EFileType.INSTANCE) return

        EPluginLogger.marker("========== FORMAT ACTION STARTED ==========")
        EPluginLogger.info("File: ${file.name}, Size: ${file.textLength} chars")

        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val text = document.text

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Formatting E-File", true) {
            @Volatile var formattedText: String? = null
            @Volatile var error: String? = null

            override fun run(indicator: ProgressIndicator) {
                try {
                    formattedText = formatContent(text, indicator)
                } catch (ex: Exception) {
                    error = ex.message
                    EPluginLogger.error("Format failed: ${ex.message}")
                    ex.printStackTrace()
                }
            }

            override fun onSuccess() {
                if (error != null) {
                    Messages.showErrorDialog(project, "Formatting failed: $error", "Error")
                    return
                }

                val newText = formattedText ?: return

                WriteCommandAction.runWriteCommandAction(project) {
                    if (document.isWritable) {
                        document.setText(newText)
                        EPluginLogger.marker("========== FORMAT COMPLETED ==========")
                    } else {
                         Messages.showInfoMessage(project, "File is read-only.", "Cannot Format")
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = (file?.fileType == EFileType.INSTANCE)
    }
    
    // Standalone formatting logic using Lexer
    private fun formatContent(text: String, indicator: ProgressIndicator): String {
        val lexer = ELexer()
        lexer.start(text)
        
        // Structure: List of Modules. Module = List of Lines. Line = List of Tokens.
        // We traverse linearly.
        
        val modules = mutableListOf<MutableList<Line>>()
        var currentModule = mutableListOf<Line>()
        var currentLineTokens = mutableListOf<TokenData>()
        
        var tokenType = lexer.tokenType
        while (tokenType != null) {
            indicator.checkCanceled()
            
            val tokenText = lexer.tokenText
            
            // Handle Newline
            if (tokenType == ETokenTypes.WHITE_SPACE && tokenText.contains('\n')) {
                if (currentLineTokens.isNotEmpty()) {
                    currentModule.add(Line(currentLineTokens.toMutableList()))
                    currentLineTokens.clear()
                }
            } else if (tokenType != ETokenTypes.WHITE_SPACE) {
                currentLineTokens.add(TokenData(tokenText, tokenType))
            }
            
            // Detect Module Boundary (End of module)?
            // Or just treat file as list of modules?
            // EFormattingModelBuilder separated modules by 2 lines.
            // We can detect Module Start (<) to split modules?
            // Actually, let's group by "Module Context".
            // If we see TAG_START ('<'), it likely starts a module or section.
            // If we are currently in a module and see '<', it might be next module?
            
            if (tokenType == ETokenTypes.TAG_START) {
                 // But wait, TAG_START is inside the line we just added? 
                 // No, we haven't flushed the line yet if it's the current token.
                 // Actually relying on TAG structure is complex if we just want to align columns.
                 // Let's just create a new module partition if we see a line starting with top-level tag?
                 // Simpler: Just collect ALL lines.
                 // Then iterate lines. 
                 // Identify blocks of "Data/Header" lines to align together.
                 // Reset alignment context on blank lines or Comments or Tags?
            }

            lexer.advance()
            tokenType = lexer.tokenType
        }
        // Flush last
        if (currentLineTokens.isNotEmpty()) {
            currentModule.add(Line(currentLineTokens))
        }
        if (currentModule.isNotEmpty()) {
            modules.add(currentModule)
        }
        
        // Now Process Lines to build String
        // We want to align "Adjacent Data Lines". 
        // Interrupted by Comments or Tags or Blank Lines?
        // E-File usually: 
        // <Module...>
        // @Header...
        // #Data... or Data...
        // Data...
        // </Module>
        
        // Strategy:
        // Iterate all lines.
        // Identify "Alignable Block". 
        // An Alignable Block consists of Header (@) and subsequent Data lines.
        // Terminated by `</Module>` or `<Module` or `//` (maybe?).
        // Actually comments usually align too if inline.
        // Let's align continuous blocks of "Table-like" lines.
        
        val result = StringBuilder()
        
        // Flattened lines from the single module bucket we created above (since we didn't split modules yet)
        val allLines = if (modules.isNotEmpty()) modules[0] else emptyList()
        
        var i = 0
        while (i < allLines.size) {
            indicator.checkCanceled()

            // Gather a block of alignable lines
            // Criteria: Lines that are NOT Tags (<...>) and have content.
            // Loop until we hit a tag or end.
            
            var end = i
            val blockLines = mutableListOf<Line>()
            
            while (end < allLines.size) {
                val line = allLines[end]
                if (isTagLine(line)) {
                     if (blockLines.isEmpty()) {
                         // This tag line is a block of its own (unaligned simple line)
                         end++
                     }
                     break
                }
                blockLines.add(line)
                end++
            }
            
            if (blockLines.isEmpty()) {
                // Just append the tag line
                val line = allLines[i]
                appendLine(result, line)
                i++
            } else {
                // Align this block
                appendAlignedBlock(result, blockLines)
                i = end
            }
        }
        
        return result.toString()
    }

    data class TokenData(val text: String, val type: com.intellij.psi.tree.IElementType)
    data class Line(val tokens: List<TokenData>)
    
    private fun isTagLine(line: Line): Boolean {
        // If line contains < or </
        return line.tokens.any { it.type == ETokenTypes.TAG_START || it.type == ETokenTypes.TAG_CLOSE_START }
    }
    
    private fun appendLine(sb: StringBuilder, line: Line) {
        // Just join tokens with space? Or keep original spacing?
        // Lexer skipped whitespace tokens!
        // So we lost original spacing.
        // Reconstruct: Tag lines usually don't need internal alignment.
        // Just space separated?
        line.tokens.forEachIndexed { index, token ->
            sb.append(token.text)
            if (index < line.tokens.size - 1) sb.append(" ")
        }
        sb.append("\n")
    }
    
    private fun appendAlignedBlock(sb: StringBuilder, lines: List<Line>) {
        EPluginLogger.marker("=== appendAlignedBlock START ===")
        EPluginLogger.info("Total lines to align: ${lines.size}")

        // 1. Calculate max widths per column (all rows participate)
        val maxWidths = mutableMapOf<Int, Int>()

        for (line in lines) {
            for (c in line.tokens.indices) {
                val w = getStringWidth(line.tokens[c].text)
                val currentMax = maxWidths.getOrDefault(c, 0)
                if (w > currentMax) {
                    maxWidths[c] = w
                }
            }
        }

        EPluginLogger.info("Max widths per column: $maxWidths")

        // 2. Append lines with padding
        for ((lineIndex, line) in lines.withIndex()) {
            EPluginLogger.debug("Line [$lineIndex]: ${line.tokens.map { it.text }}")

            for (c in line.tokens.indices) {
                val token = line.tokens[c]
                val text = token.text
                val width = getStringWidth(text)
                val targetWidth = maxWidths.getOrDefault(c, 0)

                sb.append(text)

                // Padding - If not last token
                if (c < line.tokens.size - 1) {
                    val pad = targetWidth - width + 2  // +2 spacing
                    repeat(pad) { sb.append(" ") }
                }
            }
            sb.append("\n")
        }

        EPluginLogger.marker("=== appendAlignedBlock END ===")
    }

    private fun isCommentLine(line: Line): Boolean {
        // 检查是否是注释行（以 MARKER_COMMENT 开头）
        return line.tokens.isNotEmpty() && line.tokens[0].type == ETokenTypes.MARKER_COMMENT
    }

    private fun isCommentOnlyLine(line: Line): Boolean {
        // 旧版：检查行是否只包含 COMMENT token（整行注释）
        // 新版：// 被解析为 MARKER_COMMENT + IDENTIFIER，注释行作为数据行参与列对齐
        // 这里只检测纯粹的空注释行（只有 MARKER_COMMENT 没有内容）
        return line.tokens.size == 1 && line.tokens[0].type == ETokenTypes.MARKER_COMMENT
    }

    private fun isMarkerToken(type: com.intellij.psi.tree.IElementType): Boolean {
        return type == ETokenTypes.MARKER_AT ||
               type == ETokenTypes.MARKER_AT_AT ||
               type == ETokenTypes.MARKER_HASH ||
               type == ETokenTypes.MARKER_COMMENT
    }
    
    private fun getStringWidth(s: String): Int {
        var len = 0
        for (char in s) {
            if (char.code > 255) len += 2 else len += 1
        }
        return len
    }

}

