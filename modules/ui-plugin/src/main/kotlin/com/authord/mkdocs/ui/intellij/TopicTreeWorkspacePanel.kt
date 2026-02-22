package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.authord.mkdocs.ports.topic.TopicSyncErrorCode
import com.authord.mkdocs.ui.PluginCompositionRoot
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JBColor
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.RenderingHints
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.JToolTip
import javax.swing.JTree
import javax.swing.ToolTipManager
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val ROOT_NODE_ID: String = "root"
private const val TOC_HEADER_FONT_FAMILY: String = "Inter"
private const val TOC_HEADER_FONT_SIZE: Int = 13
private const val TOC_HEADER_LINE_HEIGHT: Int = 17
private const val TOC_HEADER_TOOLBAR_PLACE: String = "WRS.TocContents.Toolbar"
private val TOC_HEADER_TOOLBAR_BACKGROUND: Color = JBColor.namedColor("Panel.background", Color(0x191A1C))
private val TOC_HEADER_TOOLBAR_FOREGROUND: Color = JBColor.namedColor("Panel.foreground", Color(0xD1D3D9))
private val TOC_TOOLTIP_BACKGROUND: Color = Color(0x343840)
private val TOC_TOOLTIP_BORDER: Color = Color(0x0E1014)
private val TOC_TOOLTIP_FOREGROUND: Color = Color(0xDFE1E5)
private const val TOC_TOOLTIP_ARC: Int = 12
private val TOC_TREE_BACKGROUND: Color = JBColor.namedColor("ToolWindow.background", Color(0x191A1C))
private val TOC_TREE_SELECTION_BACKGROUND_ACTIVE: Color = JBColor.namedColor(
    "Tree.selectionBackground",
    JBColor(Color(0xD7E8FF), Color(0x2E436E)),
)
private val TOC_TREE_SELECTION_FOREGROUND_ACTIVE: Color = JBColor.namedColor(
    "Tree.selectionForeground",
    JBColor(Color(0x1E1F22), Color(0xDFE1E5)),
)
private val TOC_TREE_SELECTION_BACKGROUND_INACTIVE: Color = JBColor.namedColor(
    "Tree.selectionInactiveBackground",
    JBColor(Color(0xE7EEF7), Color(0x393B40)),
)
private val TOC_TREE_SELECTION_FOREGROUND_INACTIVE: Color = JBColor.namedColor(
    "Tree.selectionInactiveForeground",
    JBColor(Color(0x1E1F22), Color(0xDFE1E5)),
)

private fun uiMessage(key: String, vararg params: Any): String = AuthordUiBundle.message(key, *params)

internal enum class TopicTreeNodeKind {
    ROOT,
    NAV,
    INFO,
}

internal data class TopicTreeNodeView(
    val nodeId: String,
    val title: String,
    val parentNodeId: String?,
    val path: String? = null,
    val externalUrl: String? = null,
    val kind: TopicTreeNodeKind = TopicTreeNodeKind.NAV,
) {
    val isNav: Boolean
        get() = kind == TopicTreeNodeKind.NAV || kind == TopicTreeNodeKind.ROOT

    val isMutable: Boolean
        get() = kind == TopicTreeNodeKind.NAV

    override fun toString(): String = title

    companion object {
        fun root(): TopicTreeNodeView = TopicTreeNodeView(
            nodeId = ROOT_NODE_ID,
            title = uiMessage("topicTree.root.title"),
            parentNodeId = null,
            kind = TopicTreeNodeKind.ROOT,
        )
    }
}

private class TopicTreeNodeTransferable(
    private val nodeId: String,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.stringFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        check(isDataFlavorSupported(flavor)) { "Unsupported transfer flavor: $flavor" }
        return nodeId
    }
}

internal class TopicTreeWorkspacePanel(
    private val project: com.intellij.openapi.project.Project?, // Inject Project
    private val controllersProvider: () -> TopicTreeControllers?,
    private val reconcileStateProvider: () -> StartupTreeState?,
    private val startupStateListener: (StartupTreeState) -> Unit = {},
    private val projectRootPath: String? = null,
    private val promptInputProvider: (title: String, message: String, initial: String?) -> String? =
        { title, message, initial ->
            Messages.showInputDialog(
                null,
                message,
                title,
                Messages.getQuestionIcon(),
                initial.orEmpty(),
                null,
            )
        },
    private val duplicatePathPrompt: (requestedPath: String, suggestedPath: String) -> Boolean =
        { requestedPath, suggestedPath ->
            Messages.showOkCancelDialog(
                uiMessage("topicTree.prompt.duplicatePath.message", requestedPath, suggestedPath),
                uiMessage("topicTree.prompt.duplicatePath.title"),
                uiMessage("topicTree.prompt.duplicatePath.ok"),
                uiMessage("topicTree.prompt.duplicatePath.cancel"),
                Messages.getWarningIcon(),
            ) == Messages.OK
        },
    private val runtimeServiceResolver: (com.intellij.openapi.project.Project) -> PluginRuntimeIntegrationService = {
        PluginCompositionRoot().runtimeIntegration(it)
    },
    private val browserServiceResolver: (com.intellij.openapi.project.Project) -> MkDocsPreviewBrowserService? = { currentProject ->
        runCatching { currentProject.getService(MkDocsPreviewBrowserService::class.java) }.getOrNull()
    },
) {
    private data class TocHeaderAction(
        val tooltip: String,
        val icon: Icon,
        val perform: () -> Unit,
    )

    private val root = DefaultMutableTreeNode(TopicTreeNodeView.root())
    private val model = DefaultTreeModel(root)
    private val tree = object : JTree(model) {
        override fun getToolTipText(event: MouseEvent?): String? {
            val mouseEvent = event ?: return null
            val treeNode = getPathForLocation(mouseEvent.x, mouseEvent.y)
                ?.lastPathComponent as? DefaultMutableTreeNode
                ?: return null
            val view = treeNode.userObject as? TopicTreeNodeView ?: return null
            if (!view.isNav) {
                return null
            }
            val relativePath = resolveRelativePathForOpen(view, treeNode) ?: return null
            val normalized = normalizeOptionalPath(relativePath) ?: return null
            return normalized.substringAfterLast('/').takeIf { it.isNotBlank() }
        }

        override fun getToolTipLocation(event: MouseEvent?): Point? {
            val mouseEvent = event ?: return null
            val row = getRowForLocation(mouseEvent.x, mouseEvent.y)
            if (row < 0) {
                return null
            }
            val bounds = getRowBounds(row) ?: return null
            return Point(bounds.x + JBUI.scale(14), bounds.y + bounds.height + JBUI.scale(6))
        }

        override fun createToolTip(): JToolTip {
            return TocTreeFileNameToolTip().also { tooltip ->
                tooltip.component = this
            }
        }
    }
    private val status = JBLabel("")
    private lateinit var tocHeaderActionsPanel: JComponent
    private val tocHeaderActionTooltips = mutableListOf<String>()
    private val tocHeaderActionHandlers = linkedMapOf<String, () -> Unit>()
    private lateinit var tocNewChildMenuItem: JMenuItem
    private lateinit var tocEditTitleMenuItem: JMenuItem
    private lateinit var tocRemoveMenuItem: JMenuItem
    private val tocContextMenu = JPopupMenu()
    private var currentState: StartupTreeState? = null
    private var navPresentFromParsedConfigState: Boolean = false
    private var suppressSelectionFileOpen: Boolean = false

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        preferredSize = java.awt.Dimension(380, 0)
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 0, 1)
        add(buildCenter(), BorderLayout.CENTER)
    }

    init {
        ToolTipManager.sharedInstance().registerComponent(tree)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.toggleClickCount = 0
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.background = TOC_TREE_BACKGROUND
        tree.cellRenderer = object : DefaultTreeCellRenderer() {
            override fun getTreeCellRendererComponent(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean,
            ): Component {
                val component = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
                val isActiveSelection = tree.isFocusOwner
                val selectionBackground = if (isActiveSelection) {
                    TOC_TREE_SELECTION_BACKGROUND_ACTIVE
                } else {
                    TOC_TREE_SELECTION_BACKGROUND_INACTIVE
                }
                val selectionForeground = if (isActiveSelection) {
                    TOC_TREE_SELECTION_FOREGROUND_ACTIVE
                } else {
                    TOC_TREE_SELECTION_FOREGROUND_INACTIVE
                }
                icon = null
                openIcon = null
                closedIcon = null
                leafIcon = null
                backgroundSelectionColor = selectionBackground
                textSelectionColor = selectionForeground
                backgroundNonSelectionColor = TOC_TREE_BACKGROUND
                if (selected) {
                    foreground = selectionForeground
                }
                border = if (selected) {
                    JBUI.Borders.customLine(selectionBackground, 0, 4, 0, 0)
                } else {
                    JBUI.Borders.emptyLeft(4)
                }
                return component
            }
        }
        tree.addTreeSelectionListener {
            refreshActionEnablement()
            if (suppressSelectionFileOpen) {
                return@addTreeSelectionListener
            }

            // Open file in editor on selection
            val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = selectedNode?.userObject
            if (project != null && userObject is TopicTreeNodeView && userObject.isNav) {
                val relativePath = resolveRelativePathForOpen(userObject, selectedNode)
                if (!relativePath.isNullOrBlank()) {
                    openRelativePathInEditor(relativePath)
                }
            }
        }
        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) = maybeShowTocContextMenu(event)

                override fun mouseReleased(event: MouseEvent) = maybeShowTocContextMenu(event)
            },
        )

        if (!GraphicsEnvironment.isHeadless()) {
            tree.dragEnabled = true
        }
        tree.dropMode = DropMode.ON_OR_INSERT
        tree.transferHandler = TopicTreeTransferHandler(
            nodeFromId = ::findNode,
            onMoveRequest = ::applyDragDropMove,
        )
        buildTocContextMenu()
    }

    fun render(state: StartupTreeState) {
        val expandedNodeIds = captureExpandedNodeIds()
        val selectedNodeId = selectedNavNode()
            ?.userObject
            ?.let { it as? TopicTreeNodeView }
            ?.nodeId
        navPresentFromParsedConfigState = state.source == StartupTreeSource.NAV
        currentState = state
        root.removeAllChildren()

        if (state.nodes.isEmpty()) {
            root.add(
                DefaultMutableTreeNode(
                    TopicTreeNodeView(
                        nodeId = "info-empty",
                        title = uiMessage("topicTree.info.empty"),
                        parentNodeId = ROOT_NODE_ID,
                        kind = TopicTreeNodeKind.INFO,
                    ),
                ),
            )
        } else {
            state.nodes.forEach { navNode ->
                root.add(toTreeNode(navNode, ROOT_NODE_ID))
            }
        }

        model.reload()
        if (root.childCount > 0) {
            tree.expandPath(TreePath(root.path))
        }
        restoreExpandedNodeIds(expandedNodeIds)
        selectedNodeId?.let { selectNodeById(it) }
        refreshActionEnablement()
    }

    private fun buildCenter(): JComponent {
        val headerActions = listOf(
            TocHeaderAction(
                tooltip = uiMessage("topicTree.tooltip.root"),
                icon = AllIcons.General.Add,
                perform = ::addRootTopic,
            ),
            TocHeaderAction(
                tooltip = uiMessage("topicTree.tooltip.expandAll"),
                icon = AllIcons.Actions.Expandall,
                perform = ::expandAllTopics,
            ),
            TocHeaderAction(
                tooltip = uiMessage("topicTree.tooltip.collapseAll"),
                icon = AllIcons.Actions.Collapseall,
                perform = ::collapseAllTopics,
            ),
            TocHeaderAction(
                tooltip = uiMessage("topicTree.tooltip.syncTocEditor"),
                icon = AllIcons.General.Locate,
                perform = ::synchronizeTocAndEditor,
            ),
        )
        tocHeaderActionTooltips.clear()
        tocHeaderActionTooltips += headerActions.map { it.tooltip }
        tocHeaderActionHandlers.clear()
        headerActions.forEach { action ->
            tocHeaderActionHandlers[action.tooltip] = action.perform
        }
        tocHeaderActionsPanel = createTocHeaderToolbar(headerActions)

        val tocTreeScrollPane = JBScrollPane(tree).apply {
            border = JBUI.Borders.empty()
            viewportBorder = null
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(buildSectionHeader(title = uiMessage("topicTree.section.tableOfContents"), headerActions = tocHeaderActionsPanel), BorderLayout.NORTH)
            add(tocTreeScrollPane, BorderLayout.CENTER)
        }
    }

    private fun createTocHeaderToolbar(actions: List<TocHeaderAction>): JComponent {
        if (ApplicationManager.getApplication() == null) {
            return JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
                actions.forEach { action ->
                    add(iconActionButton(icon = action.icon, tooltip = action.tooltip, action = action.perform))
                }
                applyToolbarInspectorStyle()
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            actions.forEach { action ->
                add(object : DumbAwareAction(action.tooltip, action.tooltip, action.icon) {
                    override fun actionPerformed(event: AnActionEvent) {
                        action.perform()
                    }

                    override fun update(event: AnActionEvent) {
                        event.presentation.text = action.tooltip
                        event.presentation.description = action.tooltip
                    }
                })
            }
        }
        val toolbarComponent = runCatching {
            val toolbar = ActionManager.getInstance().createActionToolbar(TOC_HEADER_TOOLBAR_PLACE, actionGroup, true)
            toolbar.targetComponent = tree
            toolbar.component
        }.getOrElse {
            JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 0))
        }
        return toolbarComponent.applyToolbarInspectorStyle()
    }

    private fun JComponent.applyToolbarInspectorStyle(): JComponent {
        border = JBUI.Borders.empty(5, 7)
        background = TOC_HEADER_TOOLBAR_BACKGROUND
        foreground = TOC_HEADER_TOOLBAR_FOREGROUND
        font = Font(TOC_HEADER_FONT_FAMILY, Font.PLAIN, TOC_HEADER_FONT_SIZE)
        isOpaque = true
        isDoubleBuffered = true
        isFocusable = true
        preferredSize = Dimension(118, 34)
        minimumSize = Dimension(30, 32)
        putClientProperty("ActionToolbarImpl.suppressTargetComponentWarning", true)
        putClientProperty("ActionToolbarImpl.suppressFastTrack", true)
        putClientProperty("SUPPRESS_BACKGROUND_PREDICATE", true)
        return this
    }

    private fun buildSectionHeader(
        title: String,
        headerActions: JComponent,
    ): JComponent {
        val leftHeader = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(
                JBLabel(title).apply {
                    border = JBUI.Borders.empty(0, 12)
                    iconTextGap = 4
                    font = Font(TOC_HEADER_FONT_FAMILY, Font.PLAIN, TOC_HEADER_FONT_SIZE)
                    foreground = Color(0xD1D3D9)
                    background = Color(0x191A1C)
                    horizontalAlignment = SwingConstants.LEADING
                    verticalAlignment = SwingConstants.CENTER
                    horizontalTextPosition = SwingConstants.TRAILING
                    verticalTextPosition = SwingConstants.CENTER
                    alignmentX = 0.0f
                    isEnabled = false
                    isFocusable = true
                    isOpaque = false
                    isDoubleBuffered = false
                    preferredSize = Dimension(preferredSize.width, TOC_HEADER_LINE_HEIGHT)
                    minimumSize = Dimension(minimumSize.width, TOC_HEADER_LINE_HEIGHT)
                    maximumSize = Dimension(maximumSize.width, TOC_HEADER_LINE_HEIGHT)
                },
                BorderLayout.WEST,
            )
        }

        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 8)
            add(leftHeader, BorderLayout.WEST)
            add(headerActions, BorderLayout.EAST)
        }
        return header
    }

    private fun iconActionButton(icon: Icon?, tooltip: String, action: () -> Unit): JButton {
        return JButton(icon).apply {
            toolTipText = tooltip
            margin = JBUI.insets(2, 4)
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            border = JBUI.Borders.empty(2)
            addActionListener { action() }
        }
    }

    private fun buildTocContextMenu() {
        tocContextMenu.removeAll()
        tocNewChildMenuItem = JMenuItem(uiMessage("topicTree.menu.newChildTopic")).apply { addActionListener { addChildTopic() } }
        tocContextMenu.add(tocNewChildMenuItem)
        tocEditTitleMenuItem = JMenuItem(uiMessage("topicTree.menu.editTitle")).apply { addActionListener { renameTopic() } }
        tocContextMenu.add(tocEditTitleMenuItem)
        tocRemoveMenuItem = JMenuItem(uiMessage("topicTree.menu.removeTocElement")).apply { addActionListener { removeTopic() } }
        tocContextMenu.add(tocRemoveMenuItem)
    }

    private fun maybeShowTocContextMenu(event: MouseEvent) {
        if (!event.isPopupTrigger) {
            return
        }
        val row = tree.getRowForLocation(event.x, event.y)
        if (row >= 0) {
            withSelectionFileOpenSuppressed {
                tree.setSelectionRow(row)
            }
        }
        refreshActionEnablement()
        tocContextMenu.show(event.component, event.x, event.y)
    }

    private fun collapseAllTopics() {
        for (row in tree.rowCount - 1 downTo 1) {
            tree.collapseRow(row)
        }
        publishStatus(uiMessage("topicTree.status.collapsedAll"))
    }

    private fun expandAllTopics() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row += 1
        }
        publishStatus(uiMessage("topicTree.status.expandedAll"))
    }

    private fun synchronizeTocAndEditor() {
        val currentProject = project ?: run {
            publishStatus(uiMessage("topicTree.status.syncUnavailable"))
            return
        }
        val selectedFile = FileEditorManager.getInstance(currentProject).selectedFiles.firstOrNull() ?: run {
            publishStatus(uiMessage("topicTree.status.syncNoOpenEditorFile"))
            return
        }
        val docsDir = activeDocsDirectory() ?: run {
            publishStatus(uiMessage("topicTree.status.syncUnavailable"))
            return
        }
        val selectedPath = runCatching { Path.of(selectedFile.path).normalize() }.getOrNull() ?: run {
            publishStatus(uiMessage("topicTree.status.syncUnavailable"))
            return
        }
        val relativePath = runCatching { docsDir.relativize(selectedPath).toString().replace('\\', '/') }.getOrNull()
        val normalizedRelativePath = normalizeOptionalPath(relativePath)
        if (normalizedRelativePath.isNullOrBlank() || normalizedRelativePath.startsWith("..")) {
            publishStatus(uiMessage("topicTree.status.syncFileOutsideDocs"))
            return
        }

        val node = findNodeByRelativePath(normalizedRelativePath) ?: run {
            publishStatus(uiMessage("topicTree.status.syncNoMatchingTopic", normalizedRelativePath))
            return
        }
        focusNode(node)
        publishStatus(uiMessage("topicTree.status.syncMatched", normalizedRelativePath))
    }

    private fun addRootTopic() {
        addTopic(parentOverride = root)
    }

    fun reconcileFromDisk() {
        val state = reconcileStateProvider()
        if (state == null) {
            publishStatus(uiMessage("topicTree.status.reconcileUnavailable"))
            refreshActionEnablement()
            return
        }
        render(state)
        startupStateListener(state)
        refreshActionEnablement()
    }

    private fun addTopic(parentOverride: DefaultMutableTreeNode? = null) {
        val parentNode = parentOverride ?: selectedNavNode() ?: root
        val parent = parentNode.userObject as? TopicTreeNodeView ?: TopicTreeNodeView.root()
        val title = prompt(
            uiMessage("topicTree.prompt.addTopic.title"),
            uiMessage("topicTree.prompt.addTopic.topicTitle"),
        ) ?: return
        val suggestedPath = suggestedMarkdownPath(title, parentNode)
        val requestedSourcePath = promptOptional(
            title = uiMessage("topicTree.prompt.addTopic.title"),
            message = uiMessage("topicTree.prompt.addTopic.relativePathOptional"),
            initial = suggestedPath,
        )?.let(::normalizeMarkdownPathWithExtension) ?: suggestedPath
        val sourcePath = resolveSourcePathWithDuplicatePrompt(requestedSourcePath) ?: return
        val nodeId = "ui-node-${UUID.randomUUID()}"
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.createTopic(
            treeId = activeTreeId(),
            parentNodeId = parent.nodeId,
            title = title,
            orderIndex = parentNode.childCount,
            sourcePath = sourcePath,
            nodeId = nodeId,
        )
        handleDispatchResult(result, uiMessage("topicTree.status.createdTopic", title)) {
            reconcileAfterMutation(
                preferredNodeId = nodeId,
                preferredPath = sourcePath,
                preferredParentNodeId = parent.nodeId,
            )
        }
    }

    private fun addChildTopic() {
        val targetNode = selectedNavNode()
        if (targetNode == null) {
            publishStatus(uiMessage("topicTree.status.selectTopicForChild"))
            return
        }
        val target = targetNode.userObject as? TopicTreeNodeView ?: return
        val title = prompt(
            uiMessage("topicTree.prompt.addChildTopic.title"),
            uiMessage("topicTree.prompt.addChildTopic.topicTitle"),
        ) ?: return
        val suggestedPath = suggestedMarkdownPath(title, targetNode)
        val requestedSourcePath = promptOptional(
            title = uiMessage("topicTree.prompt.addChildTopic.title"),
            message = uiMessage("topicTree.prompt.addTopic.relativePathOptional"),
            initial = suggestedPath,
        )?.let(::normalizeMarkdownPathWithExtension) ?: suggestedPath
        val sourcePath = resolveSourcePathWithDuplicatePrompt(requestedSourcePath) ?: return
        val nodeId = "ui-node-${UUID.randomUUID()}"
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.addChildTopic(
            treeId = activeTreeId(),
            targetNodeId = target.nodeId,
            title = title,
            orderIndex = toActualChildOrderIndex(target.nodeId, targetNode.childCount),
            sourcePath = sourcePath,
            childNodeId = nodeId,
        )
        handleDispatchResult(
            result,
            uiMessage("topicTree.status.addedChildTopic", title),
            publishSuccessStatus = false,
        ) {
            reconcileAfterMutation(
                preferredNodeId = nodeId,
                preferredPath = sourcePath,
                preferredParentNodeId = target.nodeId,
            )
        }
    }

    private fun renameTopic() {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus(uiMessage("topicTree.status.selectMutableTopic"))
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val newTitle = prompt(
            uiMessage("topicTree.prompt.renameTopic.title"),
            uiMessage("topicTree.prompt.renameTopic.newTitle"),
            selected.title,
        ) ?: return
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.renameTopic(
            treeId = activeTreeId(),
            nodeId = selected.nodeId,
            newTitle = newTitle,
        )
        handleDispatchResult(
            dispatch = result,
            successMessage = uiMessage("topicTree.status.renamedTopic", newTitle),
            publishSuccessStatus = false,
        ) {
            reconcileAfterMutation(
                preferredNodeId = selected.nodeId,
                preferredPath = selected.path.orEmpty(),
                preferredParentNodeId = selected.parentNodeId,
                preferredTitle = newTitle,
                openPreferredPathFallback = false,
            )
        }
    }

    private fun removeTopic() {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus(uiMessage("topicTree.status.selectMutableTopic"))
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.removeTopic(
            treeId = activeTreeId(),
            nodeId = selected.nodeId,
        )
        handleDispatchResult(result, uiMessage("topicTree.status.removedTopic", selected.title)) {
            val parentNode = selectedNode.parent as? DefaultMutableTreeNode
            val parentView = parentNode?.userObject as? TopicTreeNodeView
            val preferredNodeId = parentView?.nodeId ?: ROOT_NODE_ID
            reconcileAfterMutation(
                preferredNodeId = preferredNodeId,
                preferredPath = parentView?.path.orEmpty(),
                preferredParentNodeId = parentView?.parentNodeId,
                openPreferredPathFallback = false,
            )
        }
    }

    private fun applyDragDropMove(
        draggedNodeId: String,
        newParentNodeId: String,
        newOrderIndex: Int,
    ): Boolean {
        val draggedNode = findNode(draggedNodeId) ?: return false
        val targetParent = findNode(newParentNodeId) ?: root
        val controllers = controllersOrNull() ?: return false
        val result = controllers.dragDropController.moveTopic(
            treeId = activeTreeId(),
            nodeId = draggedNodeId,
            newParentNodeId = newParentNodeId,
            newOrderIndex = toActualChildOrderIndex(newParentNodeId, newOrderIndex.coerceAtLeast(0)),
        )

        return when (result.result) {
            is TopicGatewayResult.Success -> {
                val outcome = result.result.value
                if (!outcome.applied) {
                    publishStatus(uiMessage("topicTree.status.dragDropFailedSafely"))
                    return false
                }
                moveNodeInTree(draggedNode, targetParent, newOrderIndex)
                val movedView = draggedNode.userObject as? TopicTreeNodeView
                reconcileAfterMutation(
                    preferredNodeId = draggedNodeId,
                    preferredPath = movedView?.path.orEmpty(),
                    preferredParentNodeId = newParentNodeId,
                    openPreferredPathFallback = false,
                )
                true
            }

            is TopicGatewayResult.Failure -> {
                val recovery = result.recovery
                publishStatus(recovery?.summary ?: uiMessage("topicTree.status.dragDropFailed"))
                false
            }
        }
    }

    private fun moveNodeInTree(
        draggedNode: DefaultMutableTreeNode,
        newParentNode: DefaultMutableTreeNode,
        requestedIndex: Int,
    ) {
        val oldParent = draggedNode.parent as? DefaultMutableTreeNode
        val oldIndex = oldParent?.getIndex(draggedNode) ?: -1
        var index = requestedIndex.coerceIn(0, newParentNode.childCount)
        if (oldParent === newParentNode && oldIndex in 0 until index) {
            index -= 1
        }
        if (oldParent != null) {
            model.removeNodeFromParent(draggedNode)
        }

        val parentView = newParentNode.userObject as? TopicTreeNodeView ?: TopicTreeNodeView.root()
        val draggedView = draggedNode.userObject as? TopicTreeNodeView
        if (draggedView != null) {
            draggedNode.userObject = draggedView.copy(parentNodeId = parentView.nodeId)
        }
        model.insertNodeInto(draggedNode, newParentNode, index.coerceIn(0, newParentNode.childCount))
        tree.selectionPath = TreePath(draggedNode.path)
        tree.scrollPathToVisible(TreePath(draggedNode.path))
    }

    private fun selectedMutableNode(): DefaultMutableTreeNode? {
        val selectedNode = selectedNavNode() ?: return null
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return null
        return if (selected.isMutable) {
            selectedNode
        } else {
            null
        }
    }

    private fun selectedNavNode(): DefaultMutableTreeNode? {
        val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return null
        return if (selected.isNav) selectedNode else null
    }

    private fun refreshActionEnablement() {
        val hasNavSelection = selectedNavNode() != null
        val hasMutableTocSelection = selectedMutableNode() != null

        tocNewChildMenuItem.isEnabled = hasNavSelection
        tocEditTitleMenuItem.isEnabled = hasMutableTocSelection
        tocRemoveMenuItem.isEnabled = hasMutableTocSelection
    }

    private fun controllersOrNull(): TopicTreeControllers? {
        val controllers = controllersProvider()
        if (controllers == null) {
            publishStatus(uiMessage("topicTree.status.controllersUnavailable"))
        }
        return controllers
    }

    private fun handleDispatchResult(
        dispatch: TopicMutationDispatchResult,
        successMessage: String,
        publishSuccessStatus: Boolean = true,
        onSuccess: () -> Unit,
    ) {
        when (dispatch.result) {
            is TopicGatewayResult.Success -> {
                val outcome = dispatch.result.value
                if (!outcome.applied) {
                    val summary = uiMessage("topicTree.summary.operationFailedSafely")
                    publishStatus(summary)
                    publishOperationNotification(summary, NotificationType.WARNING)
                    return
                }
                onSuccess()
                if (publishSuccessStatus) {
                    publishStatus(successMessage)
                }
                publishOperationNotification(successMessage, NotificationType.INFORMATION)
            }

            is TopicGatewayResult.Failure -> {
                val recovery = dispatch.recovery
                val summary = recovery?.summary ?: uiMessage("topicTree.summary.operationFailed")
                publishStatus(summary)
                val failureType = if (dispatch.result.error.code == TopicSyncErrorCode.VALIDATION) {
                    NotificationType.WARNING
                } else {
                    NotificationType.ERROR
                }
                publishOperationNotification(summary, failureType)
            }
        }
    }

    private fun publishOperationNotification(message: String, notificationType: NotificationType) {
        presentAuthordNotification(project, message, notificationType)
    }

    private fun publishStatus(message: String) {
        status.text = message
    }

    private fun prompt(title: String, message: String, initial: String = ""): String? {
        return promptInputProvider(title, message, initial)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun promptOptional(title: String, message: String, initial: String? = null): String? {
        val value = promptInputProvider(title, message, initial)
            ?.trim()
            ?: return null
        return value.takeIf { it.isNotEmpty() }
    }

    private fun resolveSourcePathWithDuplicatePrompt(requestedPath: String): String? {
        var candidate = normalizeMarkdownPathWithExtension(requestedPath)
        while (isRelativePathTaken(candidate)) {
            val suggestion = suggestAlternativePath(candidate)
            if (!duplicatePathPrompt(candidate, suggestion)) {
                return null
            }
            candidate = suggestion
        }
        return candidate
    }

    private fun suggestAlternativePath(requestedPath: String): String {
        val normalized = normalizeMarkdownPathWithExtension(requestedPath)
        val directory = normalized.substringBeforeLast('/', "")
        val fileName = normalized.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "md")
        var suffix = 2
        while (true) {
            val candidate = joinPath(directory, "$stem-$suffix.$extension")
            if (!isRelativePathTaken(candidate)) {
                return candidate
            }
            suffix += 1
        }
    }

    private fun isRelativePathTaken(relativePath: String): Boolean {
        val normalized = normalizeOptionalPath(relativePath) ?: return false
        val target = comparablePath(normalized)
        val takenInTree = collectExistingTreePaths().any { existing ->
            comparablePath(existing) == target
        }
        if (takenInTree) {
            return true
        }

        val docsDir = activeDocsDirectory() ?: return false
        val candidate = docsDir.resolve(normalized).normalize()
        return runCatching { Files.exists(candidate) }.getOrDefault(false)
    }

    private fun collectExistingTreePaths(): Set<String> {
        val paths = linkedSetOf<String>()
        fun visit(node: DefaultMutableTreeNode) {
            val view = node.userObject as? TopicTreeNodeView
            normalizeOptionalPath(view?.path)?.let(paths::add)
            for (index in 0 until node.childCount) {
                val child = node.getChildAt(index) as? DefaultMutableTreeNode ?: continue
                visit(child)
            }
        }
        visit(root)
        return paths
    }

    private fun activeDocsDirectory(): Path? {
        val registry = controllersProvider()?.instanceRegistryPort ?: return null
        val activeTreeId = activeTreeId()
        val activeInstance = when (val active = registry.activeInstance()) {
            is TopicGatewayResult.Success -> {
                val instance = active.value
                if (instance != null && instance.instanceId == activeTreeId) {
                    instance
                } else {
                    when (val selected = registry.selectActiveInstance(activeTreeId)) {
                        is TopicGatewayResult.Success -> selected.value
                        is TopicGatewayResult.Failure -> null
                    }
                }
            }

            is TopicGatewayResult.Failure -> {
                when (val selected = registry.selectActiveInstance(activeTreeId)) {
                    is TopicGatewayResult.Success -> selected.value
                    is TopicGatewayResult.Failure -> null
                }
            }
        }
        val docsDirPath = activeInstance?.docsDirPath?.takeIf { it.isNotBlank() } ?: return null
        return resolvePathAgainstProjectRoot(docsDirPath)
    }

    private fun resolvePathAgainstProjectRoot(path: String): Path? {
        val candidate = runCatching { Path.of(path).normalize() }.getOrNull() ?: return null
        if (candidate.isAbsolute) {
            return candidate
        }
        val root = resolveProjectRootPath() ?: return null
        return runCatching { root.resolve(candidate).normalize() }.getOrNull()
    }

    private fun resolveProjectRootPath(): Path? {
        val rootPath = project?.basePath?.takeIf { it.isNotBlank() }
            ?: projectRootPath?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { Path.of(rootPath).normalize() }.getOrNull()
    }

    private fun comparablePath(path: String): String {
        return if (SystemInfoRt.isFileSystemCaseSensitive) {
            path
        } else {
            path.lowercase()
        }
    }

    private fun reconcileAfterMutation(
        preferredNodeId: String,
        preferredPath: String,
        preferredParentNodeId: String?,
        preferredTitle: String? = null,
        openPreferredPathFallback: Boolean = true,
    ) {
        reconcileFromDisk()
        runtimeServiceOrNull()?.onTopicMutationCommitted(navPresent = navPresentFromParsedConfigState)
        val preferredNode = findNode(preferredNodeId)
            ?: findNodeByRelativePath(preferredPath)
            ?: preferredTitle?.let { findNodeByTitleAndParent(title = it, parentNodeId = preferredParentNodeId) }
        if (preferredNode != null) {
            focusNode(preferredNode)
            openNodeFileInEditor(preferredNode)
            return
        }
        preferredParentNodeId
            ?.let(::findNode)
            ?.let { parentNode ->
                tree.expandPath(TreePath(parentNode.path))
                focusNode(parentNode)
            }
        if (openPreferredPathFallback) {
            openRelativePathInEditor(preferredPath)
        }
    }

    private fun captureExpandedNodeIds(): Set<String> {
        val expanded = linkedSetOf<String>()
        for (row in 0 until tree.rowCount) {
            val path = tree.getPathForRow(row) ?: continue
            if (!tree.isExpanded(path)) {
                continue
            }
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val nodeView = node.userObject as? TopicTreeNodeView ?: continue
            expanded += nodeView.nodeId
        }
        return expanded
    }

    private fun restoreExpandedNodeIds(nodeIds: Set<String>) {
        nodeIds.forEach { nodeId ->
            findNode(nodeId)?.let { node ->
                tree.expandPath(TreePath(node.path))
            }
        }
    }

    private fun selectNodeById(nodeId: String): Boolean {
        val node = findNode(nodeId) ?: return false
        focusNode(node)
        return true
    }

    private fun findNodeByRelativePath(relativePath: String): DefaultMutableTreeNode? {
        val normalized = normalizeOptionalPath(relativePath) ?: return null
        val target = comparablePath(normalized)
        fun visit(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            val view = node.userObject as? TopicTreeNodeView
            val nodePath = normalizeOptionalPath(view?.path)
            if (nodePath != null && comparablePath(nodePath) == target) {
                return node
            }
            for (index in 0 until node.childCount) {
                val child = node.getChildAt(index) as? DefaultMutableTreeNode ?: continue
                val match = visit(child)
                if (match != null) {
                    return match
                }
            }
            return null
        }
        return visit(root)
    }

    private fun focusNode(node: DefaultMutableTreeNode) {
        val path = TreePath(node.path)
        withSelectionFileOpenSuppressed {
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        }
    }

    private fun findNodeByTitleAndParent(title: String, parentNodeId: String?): DefaultMutableTreeNode? {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) {
            return null
        }
        fun visit(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            val view = node.userObject as? TopicTreeNodeView
            if (
                view != null &&
                view.kind == TopicTreeNodeKind.NAV &&
                view.parentNodeId == parentNodeId &&
                view.title.trim().equals(normalizedTitle, ignoreCase = true)
            ) {
                return node
            }
            for (index in 0 until node.childCount) {
                val child = node.getChildAt(index) as? DefaultMutableTreeNode ?: continue
                val match = visit(child)
                if (match != null) {
                    return match
                }
            }
            return null
        }
        return visit(root)
    }

    private fun <T> withSelectionFileOpenSuppressed(action: () -> T): T {
        val previous = suppressSelectionFileOpen
        suppressSelectionFileOpen = true
        return try {
            action()
        } finally {
            suppressSelectionFileOpen = previous
        }
    }

    private fun openNodeFileInEditor(node: DefaultMutableTreeNode) {
        val view = node.userObject as? TopicTreeNodeView ?: return
        val relativePath = resolveRelativePathForOpen(view, node) ?: return
        openRelativePathInEditor(relativePath)
    }

    private fun openRelativePathInEditor(relativePath: String) {
        val normalized = normalizeOptionalPath(relativePath) ?: return
        val docsDir = activeDocsDirectory() ?: return
        openFileInEditor(docsDir.resolve(normalized).normalize().toString())
    }

    private fun openFileInEditor(filePath: String) {
        val currentProject = project ?: return
        val fileSystem = LocalFileSystem.getInstance()
        val virtualFile = fileSystem.findFileByPath(filePath)
            ?: fileSystem.refreshAndFindFileByPath(filePath)
            ?: return
        val fileEditorManager = FileEditorManager.getInstance(currentProject)
        storeAuthordSplitLayout(
            currentProject,
            com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout.SHOW_EDITOR_AND_PREVIEW,
        )
        fileEditorManager.openFile(virtualFile, true)
        runCatching {
            fileEditorManager.setSelectedEditor(virtualFile, AUTHORD_PREVIEW_EDITOR_TYPE_ID)
        }
        requestPreviewForOpenedFile(currentProject, filePath)
    }

    private fun requestPreviewForOpenedFile(
        currentProject: com.intellij.openapi.project.Project,
        filePath: String,
    ) {
        val runtimeService = runtimeServiceOrNull() ?: return
        val browserService = browserServiceResolver(currentProject) ?: return
        dispatchPreviewForOpenedFile(
            currentProject = currentProject,
            filePath = filePath,
            runtimeService = runtimeService,
            browserService = browserService,
        )
    }

    private fun dispatchPreviewForOpenedFile(
        currentProject: com.intellij.openapi.project.Project,
        filePath: String,
        runtimeService: PluginRuntimeIntegrationService,
        browserService: MkDocsPreviewBrowserService,
    ) {
        runtimeService.dispatchPreviewForSelectedFileWithRetry(
            selectedPath = filePath,
            source = PreviewRouteIntentSource.TOPIC_MUTATION,
            forceReload = true,
            loadUrl = { url, forceReload -> browserService.loadUrl(url, forceReload) },
            shouldRetry = {
                isFileSelectedForPreview(currentProject, filePath)
            },
            onRouteUnavailable = { message ->
                presentAuthordNotification(currentProject, message, NotificationType.WARNING)
            },
        )
    }

    private fun isFileSelectedForPreview(
        currentProject: com.intellij.openapi.project.Project,
        filePath: String,
    ): Boolean {
        if (currentProject.isDisposed) {
            return false
        }
        val selectedFiles = runCatching { FileEditorManager.getInstance(currentProject).selectedFiles.toList() }.getOrNull()
            ?: return false
        val expected = comparablePath(filePath.replace('\\', '/'))
        return selectedFiles.any { virtualFile ->
            comparablePath(virtualFile.path.replace('\\', '/')) == expected
        }
    }

    private fun runtimeServiceOrNull(): PluginRuntimeIntegrationService? {
        val currentProject = project ?: return null
        return runCatching { runtimeServiceResolver(currentProject) }.getOrNull()
    }

    private fun activeTreeId(): String = currentState?.instanceId ?: "default"

    private fun normalizeOptionalPath(path: String?): String? {
        return path
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('\\', '/')
            ?.trimStart('/')
    }

    private fun normalizeMarkdownPathWithExtension(path: String): String {
        val normalized = normalizeOptionalPath(path).orEmpty()
        return if (normalized.endsWith(".md", ignoreCase = true)) {
            normalized
        } else {
            "$normalized.md"
        }
    }

    private fun suggestedMarkdownPath(title: String, parentNode: DefaultMutableTreeNode): String {
        val parentDirectory = resolveDirectoryForParent(parentNode)
        return joinPath(parentDirectory, "${slugifyTitle(title)}.md")
    }

    private fun resolveDirectoryForParent(parentNode: DefaultMutableTreeNode): String {
        val parentView = parentNode.userObject as? TopicTreeNodeView ?: return ""
        if (parentView.nodeId == ROOT_NODE_ID) {
            return ""
        }

        val noNavFolderHierarchy = currentState?.source == StartupTreeSource.FALLBACK
        if (noNavFolderHierarchy) {
            sectionDirectoryFromNodeId(parentView.nodeId)?.let { return it }
        }

        val directPath = normalizeOptionalPath(parentView.path)
        if (directPath != null) {
            return if (noNavFolderHierarchy) {
                deriveNoNavDirectoryFromPagePath(directPath)
            } else {
                directPath.substringBeforeLast('/', "")
            }
        }

        val childPath = firstPathInSubtree(parentNode)
        if (childPath != null) {
            return childPath.substringBeforeLast('/', "")
        }

        val ancestorNode = parentNode.parent as? DefaultMutableTreeNode ?: return ""
        return resolveDirectoryForParent(ancestorNode)
    }

    private fun sectionDirectoryFromNodeId(nodeId: String): String? {
        val prefix = "section:"
        if (!nodeId.startsWith(prefix)) {
            return null
        }
        return normalizeOptionalPath(nodeId.removePrefix(prefix))
    }

    private fun deriveNoNavDirectoryFromPagePath(pagePath: String): String {
        val normalized = normalizeOptionalPath(pagePath) ?: return ""
        val directory = normalized.substringBeforeLast('/', "")
        val fileName = normalized.substringAfterLast('/')
        val stem = fileName.substringBeforeLast('.', fileName)
        return if (stem.equals("index", ignoreCase = true)) {
            directory
        } else {
            joinPath(directory, stem)
        }
    }

    internal fun resolveFilePath(node: TopicTreeNodeView): String? {
        val treeNode = findNode(node.nodeId)
        return resolveRelativePathForOpen(node, treeNode)
    }

    private fun resolveRelativePathForOpen(
        node: TopicTreeNodeView,
        treeNode: DefaultMutableTreeNode?,
    ): String? {
        normalizeOptionalPath(node.path)?.let { return it }
        return treeNode?.let(::firstIndexPathInSubtree)
    }

    private fun firstIndexPathInSubtree(node: DefaultMutableTreeNode): String? {
        var fallbackPath: String? = null
        fun visit(current: DefaultMutableTreeNode): String? {
            val view = current.userObject as? TopicTreeNodeView
            val normalizedPath = normalizeOptionalPath(view?.path)
            if (normalizedPath != null) {
                if (isIndexMarkdownPath(normalizedPath)) {
                    return normalizedPath
                }
                if (fallbackPath == null) {
                    fallbackPath = normalizedPath
                }
            }
            for (index in 0 until current.childCount) {
                val child = current.getChildAt(index) as? DefaultMutableTreeNode ?: continue
                val match = visit(child)
                if (match != null) {
                    return match
                }
            }
            return null
        }
        return visit(node) ?: fallbackPath
    }

    private fun isIndexMarkdownPath(path: String): Boolean {
        return path.equals("index.md", ignoreCase = true) || path.endsWith("/index.md", ignoreCase = true)
    }

    private fun firstPathInSubtree(node: DefaultMutableTreeNode): String? {
        for (index in 0 until node.childCount) {
            val childNode = node.getChildAt(index) as? DefaultMutableTreeNode ?: continue
            val childView = childNode.userObject as? TopicTreeNodeView
            val normalizedPath = normalizeOptionalPath(childView?.path)
            if (normalizedPath != null) {
                return normalizedPath
            }
            val descendant = firstPathInSubtree(childNode)
            if (descendant != null) {
                return descendant
            }
        }
        return null
    }

    private fun slugifyTitle(title: String): String {
        return title
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "topic" }
    }

    private fun joinPath(directory: String, fileName: String): String {
        return when {
            directory.isBlank() -> fileName
            else -> "${directory.trimEnd('/')}/$fileName"
        }
    }

    private fun toTreeNode(
        node: TopicNavNode,
        parentNodeId: String,
    ): DefaultMutableTreeNode {
        val directPath = normalizeOptionalPath(node.path)
        val hiddenSectionRepresentativeChildren = hiddenSectionRepresentativeChildren(node)
        val hiddenSectionRepresentativeChildIds = hiddenSectionRepresentativeChildren.map { it.nodeId }.toSet()
        val representativeSectionPath = hiddenSectionRepresentativeChildren
            .asSequence()
            .mapNotNull { child -> normalizeOptionalPath(child.path) }
            .firstOrNull()
        val treeNode = DefaultMutableTreeNode(
            TopicTreeNodeView(
                nodeId = node.nodeId,
                title = node.title,
                parentNodeId = parentNodeId,
                path = directPath ?: representativeSectionPath,
                externalUrl = node.externalUrl,
            ),
        )
        node.children.forEach { child ->
            if (directPath == null && hiddenSectionRepresentativeChildIds.contains(child.nodeId)) {
                return@forEach
            }
            treeNode.add(toTreeNode(child, node.nodeId))
        }
        return treeNode
    }

    private fun hiddenSectionRepresentativeChildren(node: TopicNavNode): List<TopicNavNode> {
        if (node.path != null) {
            return emptyList()
        }
        val indexChildren = node.children.filter { child ->
            val childPath = normalizeOptionalPath(child.path) ?: return@filter false
            isIndexMarkdownPath(childPath)
        }
        if (indexChildren.isNotEmpty()) {
            return indexChildren
        }

        val sameTitleRepresentative = node.children.firstOrNull { child ->
            shouldHideSameTitleRepresentativeChild(node, child)
        }
        if (sameTitleRepresentative != null) {
            return listOf(sameTitleRepresentative)
        }

        val idBasedRepresentative = node.children.firstOrNull { child ->
            shouldHideLegacyRepresentativeChild(node, child)
        }
        return idBasedRepresentative?.let(::listOf) ?: emptyList()
    }

    private fun shouldHideSameTitleRepresentativeChild(parent: TopicNavNode, child: TopicNavNode): Boolean {
        val childPath = normalizeOptionalPath(child.path) ?: return false
        if (child.externalUrl != null || child.children.isNotEmpty()) {
            return false
        }
        if (isIndexMarkdownPath(childPath)) {
            return true
        }
        return child.title.trim().equals(parent.title.trim(), ignoreCase = true)
    }

    private fun shouldHideLegacyRepresentativeChild(parent: TopicNavNode, child: TopicNavNode): Boolean {
        if (!child.nodeId.startsWith("${parent.nodeId}__page")) {
            return false
        }
        if (child.externalUrl != null || child.children.isNotEmpty()) {
            return false
        }
        return normalizeOptionalPath(child.path) != null
    }

    private fun toActualChildOrderIndex(parentNodeId: String, uiChildIndex: Int): Int {
        val parentStateNode = findStateNodeById(parentNodeId) ?: return uiChildIndex
        if (parentStateNode.path != null) {
            return uiChildIndex
        }
        val hiddenRepresentativeChildIds = hiddenSectionRepresentativeChildren(parentStateNode)
            .map { it.nodeId }
            .toSet()
        val hiddenRepresentativeChildrenCount = parentStateNode.children.count { child ->
            hiddenRepresentativeChildIds.contains(child.nodeId)
        }
        return uiChildIndex + hiddenRepresentativeChildrenCount
    }

    private fun findStateNodeById(nodeId: String): TopicNavNode? {
        fun visit(nodes: List<TopicNavNode>): TopicNavNode? {
            nodes.forEach { node ->
                if (node.nodeId == nodeId) {
                    return node
                }
                val nested = visit(node.children)
                if (nested != null) {
                    return nested
                }
            }
            return null
        }

        return visit(currentState?.nodes.orEmpty())
    }

    internal fun tooltipTextsForTest(): List<String> {
        val tooltips = mutableListOf<String>()
        fun visit(component: Component) {
            if (component is JComponent) {
                val tooltip = component.toolTipText
                if (!tooltip.isNullOrBlank()) {
                    tooltips += tooltip
                }
            }
            if (component is java.awt.Container) {
                component.components.forEach(::visit)
            }
        }
        visit(component)
        tooltips += tocHeaderActionTooltips
        return tooltips
    }

    internal fun tocContextMenuLabelsForTest(): List<String> {
        return tocContextMenu.components
            .filterIsInstance<JMenuItem>()
            .map { it.text }
    }

    internal fun tocContextActionStatesForTest(): Map<String, Boolean> {
        refreshActionEnablement()
        return mapOf(
            "Child" to tocNewChildMenuItem.isEnabled,
            "Delete" to tocRemoveMenuItem.isEnabled,
        )
    }

    internal fun headerActionVisibilityForTest(): Map<String, Boolean> {
        return mapOf(
            "Expand All" to (tocHeaderActionsPanel.isShowing || tocHeaderActionsPanel.isVisible),
            "Collapse All" to (tocHeaderActionsPanel.isShowing || tocHeaderActionsPanel.isVisible),
            "New Topic" to (tocHeaderActionsPanel.isShowing || tocHeaderActionsPanel.isVisible),
            "Synchronize TOC and Editor" to (tocHeaderActionsPanel.isShowing || tocHeaderActionsPanel.isVisible),
        )
    }

    internal fun headerActionTooltipsForTest(): List<String> {
        return tocHeaderActionTooltips.toList()
    }

    internal fun statusTextForTest(): String = status.text.orEmpty()

    internal fun triggerHeaderActionForTest(label: String): Boolean {
        refreshActionEnablement()
        val action = tocHeaderActionHandlers[label] ?: return false
        action()
        return true
    }

    internal fun selectTreeNodeForTest(nodeId: String): Boolean {
        val targetNode = findNode(nodeId) ?: return false
        focusNode(targetNode)
        return true
    }

    internal fun selectedTreeNodeIdForTest(): String? {
        val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null
        return (selectedNode.userObject as? TopicTreeNodeView)?.nodeId
    }

    internal fun childNodeIdsForTest(parentNodeId: String): List<String> {
        val parent = findNode(parentNodeId) ?: return emptyList()
        return (0 until parent.childCount)
            .mapNotNull { index -> parent.getChildAt(index) as? DefaultMutableTreeNode }
            .mapNotNull { child -> (child.userObject as? TopicTreeNodeView)?.nodeId }
    }

    internal fun triggerTocContextActionForTest(label: String): Boolean {
        refreshActionEnablement()
        val action = tocContextMenu.components
            .filterIsInstance<JMenuItem>()
            .firstOrNull { it.text == label }
            ?: return false
        if (!action.isEnabled) {
            return false
        }
        action.doClick()
        return true
    }

    private fun findNode(nodeId: String): DefaultMutableTreeNode? {
        fun visit(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            val view = node.userObject as? TopicTreeNodeView
            if (view?.nodeId == nodeId) {
                return node
            }
            for (index in 0 until node.childCount) {
                val child = node.getChildAt(index) as? DefaultMutableTreeNode ?: continue
                val match = visit(child)
                if (match != null) {
                    return match
                }
            }
            return null
        }
        return visit(root)
    }
}

private class TocTreeFileNameToolTip : JToolTip() {
    init {
        isOpaque = false
        border = JBUI.Borders.empty(10, 18)
        font = Font(TOC_HEADER_FONT_FAMILY, Font.PLAIN, TOC_HEADER_FONT_SIZE + 1)
        foreground = TOC_TOOLTIP_FOREGROUND
        background = TOC_TOOLTIP_BACKGROUND
    }

    override fun paintComponent(graphics: Graphics) {
        val g2 = graphics.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            g2.fillRoundRect(0, 0, width - 1, height - 1, TOC_TOOLTIP_ARC, TOC_TOOLTIP_ARC)
            g2.color = TOC_TOOLTIP_BORDER
            g2.drawRoundRect(0, 0, width - 1, height - 1, TOC_TOOLTIP_ARC, TOC_TOOLTIP_ARC)
        } finally {
            g2.dispose()
        }
        super.paintComponent(graphics)
    }
}

private class TopicTreeTransferHandler(
    private val nodeFromId: (String) -> DefaultMutableTreeNode?,
    private val onMoveRequest: (draggedNodeId: String, newParentNodeId: String, newOrderIndex: Int) -> Boolean,
) : TransferHandler() {
    override fun getSourceActions(c: JComponent?): Int = MOVE

    override fun createTransferable(c: JComponent?): Transferable? {
        val tree = c as? JTree ?: return null
        val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return null
        val nodeView = node.userObject as? TopicTreeNodeView ?: return null
        if (!nodeView.isMutable) {
            return null
        }
        return TopicTreeNodeTransferable(nodeView.nodeId)
    }

    override fun canImport(support: TransferSupport): Boolean {
        if (!support.isDrop || !support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return false
        }
        val drop = support.dropLocation as? JTree.DropLocation ?: return false
        val path = drop.path ?: return false
        val targetNode = path.lastPathComponent as? DefaultMutableTreeNode ?: return false
        val targetView = targetNode.userObject as? TopicTreeNodeView ?: return false
        return targetView.isNav
    }

    override fun importData(support: TransferSupport): Boolean {
        if (!canImport(support)) {
            return false
        }
        val drop = support.dropLocation as? JTree.DropLocation ?: return false
        val targetNode = drop.path?.lastPathComponent as? DefaultMutableTreeNode ?: return false
        val targetView = targetNode.userObject as? TopicTreeNodeView ?: return false
        val draggedNodeId = runCatching {
            support.transferable.getTransferData(DataFlavor.stringFlavor) as String
        }.getOrNull() ?: return false
        val draggedNode = nodeFromId(draggedNodeId) ?: return false
        if (draggedNode === targetNode) {
            return false
        }

        val childIndex = drop.childIndex
        val newParentId = targetView.nodeId
        val newOrderIndex = if (childIndex >= 0) childIndex else targetNode.childCount
        return onMoveRequest(draggedNodeId, newParentId, newOrderIndex)
    }
}
