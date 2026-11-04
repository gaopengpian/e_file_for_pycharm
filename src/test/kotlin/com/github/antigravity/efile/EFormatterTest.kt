package com.github.antigravity.efile

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test
import com.intellij.psi.codeStyle.CodeStyleManager

class EFormatterTest : LightPlatformCodeInsightFixture4TestCase() {

    fun testAlignment() {
        // Use GBK as verified previously
        val text = """
<basevalue>
@    name       value
#    p_base   100.000000
#    u_scale    1.000000
"""
        myFixture.configureByText("alignment_test.e", text.trim())
        
        val project = myFixture.project
        val psiFile = myFixture.file
        
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
             CodeStyleManager.getInstance(project).reformat(psiFile)
        }
        
        val after = psiFile.text
        println("Alignment Result:")
        println(after)
        
        val lines = after.split("\n")
        val p_base_idx = lines.find { it.contains("p_base") }?.indexOf("100.000000") ?: -1
        val u_scale_idx = lines.find { it.contains("u_scale") }?.indexOf("1.000000") ?: -1
        
        // Assert they are aligned (same index)
        assertEquals("Values should be aligned", p_base_idx, u_scale_idx)
    }
    @Test
    fun testAstStructure() {
        val fileContent = """
            <basevalue::MeasurePoint>
            @    name       value        unit
            #    p_base     100.000000   MW
            </basevalue::MeasurePoint>
        """.trimIndent()
        
        val psiFile = myFixture.configureByText("test.e", fileContent)
        
        fun printAst(node: com.intellij.lang.ASTNode, indent: String) {
            println(indent + node.elementType.toString() + " (" + node.text.replace("\n", "\\n") + ")")
            var c = node.firstChildNode
            while (c != null) {
                printAst(c, indent + "  ")
                c = c.treeNext
            }
        }
        
        println("=== AST DUMP ===")
        printAst(psiFile.node, "")
        println("================")
    }
}
