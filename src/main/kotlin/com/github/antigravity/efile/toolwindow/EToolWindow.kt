package com.github.antigravity.efile.toolwindow

import com.github.antigravity.efile.EFileType
import com.github.antigravity.efile.lang.ETokenTypes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.*
import com.intellij.lang.ASTNode
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class EToolWindow(private val project: Project, private val toolWindow: ToolWindow) {
    val content = JPanel(BorderLayout())
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Root"))
    private val tree = Tree(treeModel)

    init {
        tree.isRootVisible = false
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        
        // Navigation listener: Use OpenFileDescriptor for standard IDE navigation
        tree.addTreeSelectionListener { e ->
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val info = node.userObject as? NodeInfo ?: return@addTreeSelectionListener
            
            if (info.file.isValid) {
                // Navigate with focus, requesting scrolling
                OpenFileDescriptor(project, info.file, info.offset).navigate(true)
                
                // Manual Horizontal Adjustment
                // ScrollType.CENTER doesn't always guarantee horizontal centering relative to viewport.
                // We calculate visible area and shift manually.
                javax.swing.SwingUtilities.invokeLater {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    if (editor != null && editor.virtualFile == info.file) {
                        try {
                            val caretModel = editor.caretModel
                            val scrollingModel = editor.scrollingModel
                            
                            // Get visual position of caret
                            val visualPos = caretModel.visualPosition
                            // Convert to XY in editor content component
                            val p = editor.visualPositionToXY(visualPos)
                            
                            // Get visible area
                            val visibleArea = scrollingModel.visibleArea
                            
                            // Calculate target ScrollX
                            // We want 'p.x' to be in the center of 'visibleArea.width'
                            val targetX = p.x - (visibleArea.width / 2)
                             
                            // Scroll there
                            scrollingModel.scrollHorizontally(targetX)
                            
                            // Also ensure vertical center (though OpenFileDescriptor usually handles it, re-enforcing doesn't hurt)
                            scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                            
                        } catch (ex: Exception) {
                            // Ignore scrolling errors
                        }
                    }
                }
            }
        }

        content.add(JBScrollPane(tree), BorderLayout.CENTER)

        // Listen for file changes (Selection / Open)
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                refreshTree(event.newFile)
            }
            
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                refreshTree(file)
            }
        })
        
        // Listen for Content Changes (Reformat / Edit) to update offsets
        PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
            override fun childrenChanged(event: PsiTreeChangeEvent) {
                // Using event.file or event.child.containingFile
                val file = event.file?.virtualFile ?: event.child?.containingFile?.virtualFile ?: return
                
                // Determine valid active file
                val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                
                if (file == currentFile) {
                     javax.swing.SwingUtilities.invokeLater {
                         refreshTree(file)
                     }
                }
            }
            // Catch other events too like property changes just in case
            override fun propertyChanged(event: PsiTreeChangeEvent) {
                 childrenChanged(event)
            }
        }, project) 
        
        // Initial refresh: Use invokeLater to allow IDE to restore editors first
        javax.swing.SwingUtilities.invokeLater {
            if (project.isDisposed) return@invokeLater
            val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            refreshTree(currentFile)
        }
    }
    
    // Data class to hold navigation info
    data class NodeInfo(val name: String, val file: VirtualFile, val offset: Int) {
        override fun toString(): String = name
    }

    private fun refreshTree(file: VirtualFile?) {
        if (file == null || file.fileType.name != "EFILE") {
            treeModel.setRoot(DefaultMutableTreeNode("Root"))
            return
        }
        
        // Ensure Tool Window is visible
        if (!toolWindow.isVisible) {
            toolWindow.show(null)
        }
        
        // Wrap PSI access in Read Action
        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
            if (project.isDisposed) return@runReadAction
            
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runReadAction
            
            val rootNode = DefaultMutableTreeNode("Root")
            
            // Iterate PSI
            var child = psiFile.firstChild
            while (child != null) {
                if (child.node.elementType == ETokenTypes.MODULE) {
                    try {
                         // 1. Extract Name
                         var moduleName = "Module"
                         val text = child.text
                         // Simple extraction from first line
                         val firstLineEnd = text.indexOf('\n')
                         val headerText = if (firstLineEnd > 0) text.substring(0, firstLineEnd) else text
                         
                         val s = headerText.indexOf('<')
                         val e = headerText.indexOf('>')
                         if (s >= 0 && e > s) {
                             moduleName = headerText.substring(s+1, e).trim()
                         }
                         
                         // 2. Count Data Lines
                         var dataCount = 0
                         
                         var isLineHeader = false
                         var isLineComment = false
                         var hasLineContent = false
                         var firstTokenOnLine = true
                         
                         fun countDataLines(node: ASTNode) {
                             if (node.elementType == ETokenTypes.DATA_SECTION || node.elementType == ETokenTypes.DATA_ROW) {
                                 var c = node.firstChildNode
                                 while (c != null) {
                                     countDataLines(c)
                                     c = c.treeNext
                                 }
                             } else {
                                 val type = node.elementType
                                 if (type == ETokenTypes.WHITE_SPACE && node.text.contains('\n')) {
                                     if (hasLineContent && !isLineHeader) dataCount++
                                     isLineHeader = false
                                     isLineComment = false
                                     hasLineContent = false
                                     firstTokenOnLine = true
                                 } else if (type == ETokenTypes.WHITE_SPACE) {
                                 } else if (type == ETokenTypes.COMMENT) {
                                     if (firstTokenOnLine) isLineComment = true
                                 } else {
                                     if (!isLineComment) {
                                         if (type == ETokenTypes.MARKER_AT || type == ETokenTypes.MARKER_AT_AT) isLineHeader = true
                                         if (type == ETokenTypes.TAG_START || type == ETokenTypes.TAG_CLOSE_START) isLineHeader = true
                                         hasLineContent = true
                                     }
                                     firstTokenOnLine = false
                                 }
                             }
                         }
                         
                         var currentChild = child.node.firstChildNode
                         while (currentChild != null) {
                             countDataLines(currentChild)
                             currentChild = currentChild.treeNext
                         }
                         // Check last line (if no final newline)
                         if (hasLineContent && !isLineHeader) {
                             dataCount++
                         }
                         
                         // Display
                         val displayName = "$moduleName [$dataCount]"
                         val moduleNode = DefaultMutableTreeNode(NodeInfo(displayName, file, child.textOffset))
                         rootNode.add(moduleNode)
                         
                         // 3. Populate Children (Fields)
                         var fieldHeaderFound = false
                         var fieldScanDone = false
                         fun processField(node: ASTNode) {
                             if (fieldScanDone) return
                             if (node.elementType == ETokenTypes.DATA_SECTION || node.elementType == ETokenTypes.DATA_ROW) {
                                 var c = node.firstChildNode
                                 while (c != null) {
                                      processField(c)
                                      c = c.treeNext
                                 }
                             } else {
                                 val type = node.elementType
                                 if (type == ETokenTypes.WHITE_SPACE && node.text.contains('\n')) {
                                     if (fieldHeaderFound) {
                                         fieldScanDone = true
                                         return
                                     }
                                     fieldHeaderFound = false
                                 }
                                 if (type == ETokenTypes.MARKER_AT || type == ETokenTypes.MARKER_AT_AT) {
                                     fieldHeaderFound = true
                                 }
                                 if (fieldHeaderFound && type == ETokenTypes.IDENTIFIER) {
                                      moduleNode.add(DefaultMutableTreeNode(NodeInfo(node.text, file, node.startOffset)))
                                 }
                             }
                         }
                         var fieldChild = child.node.firstChildNode
                         while (fieldChild != null && !fieldScanDone) {
                             processField(fieldChild)
                             fieldChild = fieldChild.treeNext
                         }
                    } catch (ex: Exception) {
                        val errorNode = DefaultMutableTreeNode(NodeInfo("Error: ${ex.message ?: ex.javaClass.simpleName}", file, child.textOffset))
                        rootNode.add(errorNode)
                    }
                }
                child = child.nextSibling
            }
            
            treeModel.setRoot(rootNode)
            treeModel.reload()
            
            // Expand all
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
        }
    }}
