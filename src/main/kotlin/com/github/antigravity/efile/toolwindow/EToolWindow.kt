package com.github.antigravity.efile.toolwindow

import com.github.antigravity.efile.EFileType
import com.github.antigravity.efile.lang.ETokenTypes
import com.github.antigravity.efile.highlight.EHighlighterColors
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.psi.*
import com.intellij.lang.ASTNode
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
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
    
    private var activeNode: DefaultMutableTreeNode? = null
    private var isSyncing = false

    private fun setActiveNode(node: DefaultMutableTreeNode?) {
        if (activeNode === node) return
        val old = activeNode
        activeNode = node
        if (old != null) treeModel.nodeChanged(old)
        if (node != null) {
            treeModel.nodeChanged(node)
            val path = javax.swing.tree.TreePath(node.path)
            
            isSyncing = true // Prevent feedback loop
            try {
                tree.scrollPathToVisible(path)
                tree.setSelectionPath(path)
            } finally {
                // Return to normal after UI updates settle
                javax.swing.SwingUtilities.invokeLater { isSyncing = false }
            }
        }
    }

    init {
        tree.isRootVisible = false
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        
        tree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: javax.swing.JTree, value: Any?, selected: Boolean,
                expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode ?: return
                val userObject = node.userObject
                val isActive = (node === activeNode)
                
                if (userObject is String) {
                    append(userObject)
                } else if (userObject is NodeInfo) {
                    if (userObject.isModule) {
                        val nameStr = userObject.name
                        val bracketIdx = nameStr.lastIndexOf('[')
                        
                        val scheme = EditorColorsManager.getInstance().globalScheme
                        val tagAttributes = scheme.getAttributes(EHighlighterColors.TAG)
                        val tagColor = tagAttributes?.foregroundColor ?: com.intellij.ui.JBColor.ORANGE
                        
                        if (bracketIdx > 0) {
                            val modName = nameStr.substring(0, bracketIdx).trim()
                            val countStr = nameStr.substring(bracketIdx)
                            if (isActive) {
                                append(modName, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, com.intellij.ui.JBColor.RED))
                            } else {
                                append(modName, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, tagColor))
                            }
                            append(" $countStr", SimpleTextAttributes.GRAY_ATTRIBUTES)
                        } else {
                            if (isActive) {
                                append(nameStr, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, com.intellij.ui.JBColor.RED))
                            } else {
                                append(nameStr, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, tagColor))
                            }
                        }
                    } else {
                        if (isActive) {
                            append(userObject.name, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, com.intellij.ui.JBColor.RED))
                        } else {
                            append(userObject.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        }
                    }
                }
            }
        }
        
        // Navigation listener: Use OpenFileDescriptor for standard IDE navigation
        tree.addTreeSelectionListener { e ->
            if (isSyncing) return@addTreeSelectionListener
            
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            val info = node.userObject as? NodeInfo ?: return@addTreeSelectionListener
            
            if (info.file.isValid) {
                isSyncing = true
                try {
                    // Pre-select the node for coloring immediately
                    setActiveNode(node)
                    
                    // Navigate with focus, requesting scrolling
                    OpenFileDescriptor(project, info.file, info.offset).navigate(true)
                    
                    // Manual Horizontal Adjustment
                    javax.swing.SwingUtilities.invokeLater {
                        val editor = FileEditorManager.getInstance(project).selectedTextEditor
                        if (editor != null && editor.virtualFile == info.file) {
                            try {
                                val caretModel = editor.caretModel
                                val scrollingModel = editor.scrollingModel
                                val visualPos = caretModel.visualPosition
                                val p = editor.visualPositionToXY(visualPos)
                                val visibleArea = scrollingModel.visibleArea
                                val targetX = p.x - (visibleArea.width / 2)
                                scrollingModel.scrollHorizontally(targetX.coerceAtLeast(0))
                                scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                            } catch (ex: Exception) {}
                        }
                    }
                } finally {
                    // Reset syncing flag after navigation settles
                    javax.swing.SwingUtilities.invokeLater { isSyncing = false }
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
        
        // Listen for ToolWindow selection/visibility to ensure we are up to date
        project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                if (toolWindow.isVisible) {
                    val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                    if (currentFile != null) refreshTree(currentFile)
                }
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
        
        // Listen for Caret Changes (Cursor position)
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.project != project) return
                val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
                if (file.fileType.name != "EFILE") return
                
                if (toolWindow.isVisible) {
                     updateActiveNodeFromCaret(editor.caretModel.offset, file)
                }
            }
        }, project)

        // Initial refresh: Use invokeLater to allow IDE to restore editors first
        // And use DumbService to wait for indices to be ready for PSI parsing
        DumbService.getInstance(project).runWhenSmart {
            if (project.isDisposed) return@runWhenSmart
            val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            refreshTree(currentFile)
        }
    }
    
    // Data class to hold navigation info
    data class NodeInfo(val name: String, val isModule: Boolean, val file: VirtualFile, val offset: Int) {
        override fun toString(): String = name
    }

    private fun refreshTree(file: VirtualFile?) {
        if (isSyncing) return // Don't refresh if we are currently navigating
        
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
            var foundModule = false
            while (child != null) {
                if (child.node?.elementType == ETokenTypes.MODULE) {
                    foundModule = true
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
                             moduleName = headerText.substring(s+1, e).trim().split("::")[0]
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
                         val moduleNode = DefaultMutableTreeNode(NodeInfo(displayName, true, file, child.textOffset))
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
                                       moduleNode.add(DefaultMutableTreeNode(NodeInfo(node.text, false, file, node.startOffset)))
                                 }
                             }
                         }
                         var fieldChild = child.node.firstChildNode
                         while (fieldChild != null && !fieldScanDone) {
                             processField(fieldChild)
                             fieldChild = fieldChild.treeNext
                         }
                    } catch (ex: Exception) {
                        val errorNode = DefaultMutableTreeNode(NodeInfo("Error: ${ex.message ?: ex.javaClass.simpleName}", false, file, child.textOffset))
                        rootNode.add(errorNode)
                    }
                }
                child = child.nextSibling
            }
            
            if (!foundModule) {
                 refreshTreeLargeFile(psiFile, rootNode)
            }
            
            treeModel.setRoot(rootNode)
            treeModel.reload()
            
            // Expand all
            for (i in 0 until tree.rowCount) {
                tree.expandRow(i)
            }
        }
    }

    private fun refreshTreeLargeFile(psiFile: PsiFile, rootNode: DefaultMutableTreeNode) {
        val text = psiFile.text
        val file = psiFile.virtualFile
        val lines = text.split('\n')
        
        var currentOffset = 0
        var currentModuleNode: DefaultMutableTreeNode? = null
        var currentDataCount = 0
        var currentModuleStart = -1
        var currentModulePlaceholder = ""

        val foldingRegions = mutableListOf<Triple<Int, Int, String>>()

        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.startsWith("<!")) {
                // Update previous module count
                if (currentModuleNode != null) {
                    val info = currentModuleNode.userObject as NodeInfo
                    currentModuleNode.userObject = info.copy(name = info.name.replace("[0]", "[$currentDataCount]"))
                }
                
                val endIdx = trimmed.indexOf('>')
                if (endIdx > 1) {
                    val modName = trimmed.substring(1, endIdx).split("::")[0].trim()
                    val startOffset = currentOffset + line.indexOf('<')
                    val info = NodeInfo("$modName [0]", true, file, startOffset)
                    currentModuleNode = DefaultMutableTreeNode(info)
                    rootNode.add(currentModuleNode)
                    currentDataCount = 0
                    
                    currentModuleStart = startOffset
                    currentModulePlaceholder = line.substring(line.indexOf('<'), line.indexOf('>', line.indexOf('<')) + 1) + "..."
                }
            } else if (trimmed.startsWith("</") && currentModuleStart != -1) {
                 val endIdx = line.indexOf('>')
                 if (endIdx >= 0) {
                     val endPos = currentOffset + endIdx + 1
                     if (endPos > currentModuleStart + 10) {
                         foldingRegions.add(Triple(currentModuleStart, endPos, currentModulePlaceholder))
                     }
                 }
                 currentModuleStart = -1
            } else if (trimmed.startsWith("@")) {
                if (currentModuleNode != null && currentModuleNode.childCount == 0) {
                    val atIdx = line.indexOf('@')
                    val headerText = line.substring(atIdx + 1)
                    val scanner = java.util.Scanner(headerText)
                    var runningOffset = currentOffset + atIdx + 1
                    while (scanner.hasNext()) {
                        val field = scanner.next()
                        val fieldPos = line.indexOf(field, runningOffset - currentOffset)
                        currentModuleNode.add(DefaultMutableTreeNode(NodeInfo(field, false, file, currentOffset + fieldPos)))
                        runningOffset = currentOffset + fieldPos + field.length
                    }
                }
            } else if (trimmed.startsWith("#")) {
                currentDataCount++
            }
            currentOffset += line.length + 1
        }
        
        // Final module update
        if (currentModuleNode != null) {
            val info = currentModuleNode.userObject as NodeInfo
            currentModuleNode.userObject = info.copy(name = info.name.replace("[0]", "[$currentDataCount]"))
        }

        // Inject Folding for Large Files manually
        if (foldingRegions.isNotEmpty()) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null && FileDocumentManager.getInstance().getFile(editor.document) == file) {
                javax.swing.SwingUtilities.invokeLater {
                    com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                        editor.foldingModel.runBatchFoldingOperation {
                            // For large files, IDE-disabled folding means allFoldRegions is likely empty or minimal.
                            // We just add current scan results.
                            for (region in foldingRegions) {
                                try {
                                    editor.foldingModel.addFoldRegion(region.first, region.second, region.third)
                                } catch (e: Exception) {
                                    // Ignore overlapping or invalid regions
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateActiveNodeFromCaret(offset: Int, file: VirtualFile) {
        if (isSyncing) return
        
        com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
            if (project.isDisposed) return@runReadAction
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runReadAction
            
            var element = psiFile.findElementAt(offset)
            if (element != null && element.node?.elementType == ETokenTypes.WHITE_SPACE && offset > 0) {
                 element = psiFile.findElementAt(offset - 1)
            }
            if (element == null) return@runReadAction
            
            var parentNode: PsiElement? = element.parent
            while (parentNode != null && parentNode !is PsiFile && parentNode.node?.elementType != ETokenTypes.MODULE) {
                parentNode = parentNode.parent
            }
            
            val root = treeModel.root as? DefaultMutableTreeNode ?: return@runReadAction
            var targetModuleNode: DefaultMutableTreeNode? = null

            if (parentNode != null && parentNode.node?.elementType == ETokenTypes.MODULE) {
                val moduleOffset = parentNode.textOffset
                for (j in 0 until root.childCount) {
                     val mNode = root.getChildAt(j) as DefaultMutableTreeNode
                     val info = mNode.userObject as? NodeInfo
                     if (info != null && info.offset == moduleOffset) {
                         targetModuleNode = mNode
                         break
                     }
                }
            } else {
                // Fallback for large files where MODULE nodes don't exist in PSI
                // Find latest Module node that offset is after
                for (j in 0 until root.childCount) {
                    val mNode = root.getChildAt(j) as DefaultMutableTreeNode
                    val info = mNode.userObject as? NodeInfo
                    if (info != null && info.offset <= offset) {
                        targetModuleNode = mNode
                    } else if (info != null && info.offset > offset) {
                        break
                    }
                }
            }
            
            if (targetModuleNode == null) return@runReadAction
            
            val text = psiFile.text
            val lineStart = text.lastIndexOf('\n', offset - 1).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
            val lineText = text.substring(lineStart, lineEnd)
            
            var i = 0
            while (i < lineText.length && Character.isWhitespace(lineText[i])) i++
            
            if (i < lineText.length && (lineText[i] == '#' || lineText[i] == '@')) {
                val relativeOffset = offset - lineStart
                val textBefore = lineText.substring(0, relativeOffset)
                
                // Optimized text-based column counting
                val headerChar = lineText[i]
                val contentBefore = textBefore.substringAfter(headerChar)
                val scanner = java.util.Scanner(contentBefore)
                var count = 0
                while (scanner.hasNext()) {
                    scanner.next()
                    count++
                }
                
                // If cursor is inside a word, the scanner just counted it.
                if (relativeOffset > 0 && !lineText[relativeOffset - 1].isWhitespace() && count > 0) {
                    count--
                }
                
                if (count < targetModuleNode.childCount) {
                    val fieldNode = targetModuleNode.getChildAt(count) as DefaultMutableTreeNode
                    javax.swing.SwingUtilities.invokeLater { setActiveNode(fieldNode) }
                    return@runReadAction
                }
            }
            
            // Default to module node
            javax.swing.SwingUtilities.invokeLater { setActiveNode(targetModuleNode) }
        }
    }
}
