package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JBColor
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GraphicsEnvironment
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
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val ROOT_NODE_ID: String = "root"

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

    override fun toString(): String {
        return when {
            kind == TopicTreeNodeKind.ROOT -> title
            path != null -> "$title ($path)"
            externalUrl != null -> "$title ($externalUrl)"
            else -> title
        }
    }

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
) {
    private val root = DefaultMutableTreeNode(TopicTreeNodeView.root())
    private val model = DefaultTreeModel(root)
    private val tree = JTree(model)
    private val treeSelectionBandColor = JBColor(Color(0xD7E8FF), Color(0x304D80))
    private val status = JBLabel("")
    private lateinit var collapseAllButton: JButton
    private lateinit var addRootTopicButton: JButton
    private lateinit var tocDeleteButton: JButton
    private lateinit var tocChildButton: JButton
    private lateinit var tocRowActionsPanel: JPanel
    private lateinit var tocNewChildMenuItem: JMenuItem
    private lateinit var tocEditTitleMenuItem: JMenuItem
    private lateinit var tocRemoveMenuItem: JMenuItem
    private val tocContextMenu = JPopupMenu()
    private var currentState: StartupTreeState? = null

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        preferredSize = java.awt.Dimension(380, 0)
        border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 0, 1)
        add(buildHeader(), BorderLayout.NORTH)
        add(buildCenter(), BorderLayout.CENTER)
        add(buildStatusBar(), BorderLayout.SOUTH)
    }

    init {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.toggleClickCount = 0
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
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
                icon = null
                openIcon = null
                closedIcon = null
                leafIcon = null
                backgroundSelectionColor = treeSelectionBandColor
                border = if (selected) {
                    JBUI.Borders.customLine(treeSelectionBandColor, 0, 4, 0, 0)
                } else {
                    JBUI.Borders.emptyLeft(4)
                }
                return component
            }
        }
        tree.addTreeSelectionListener {
            refreshActionEnablement()

            // Open file in editor on selection
            val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = selectedNode?.userObject
            if (project != null && userObject is TopicTreeNodeView && userObject.isNav) {
                val absolutePath = resolveFilePathInternal(userObject, selectedNode)
                if (!absolutePath.isNullOrBlank()) {
                    openFileInEditor(absolutePath)
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

    private fun buildHeader(): JComponent {
        val title = JBLabel(uiMessage("topicTree.header.brand")).apply {
            font = JBFont.label().deriveFont(JBFont.label().size + 2f)
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 10, 4, 10)
            add(title, BorderLayout.CENTER)
        }
    }

    private fun buildCenter(): JComponent {
        collapseAllButton = iconActionButton(icon = AllIcons.Actions.Collapseall, tooltip = uiMessage("topicTree.tooltip.collapseAll")) { collapseAllTopics() }
        addRootTopicButton = iconActionButton(icon = AllIcons.General.Add, tooltip = uiMessage("topicTree.tooltip.root")) { addRootTopic() }
        val tocHeaderActions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            add(collapseAllButton)
            add(addRootTopicButton)
        }

        tocChildButton = iconActionButton(icon = AllIcons.General.Add, tooltip = uiMessage("topicTree.tooltip.child")) { addChildTopic() }
        tocDeleteButton = iconActionButton(icon = AllIcons.General.Remove, tooltip = uiMessage("topicTree.tooltip.delete")) { removeTopic() }
        tocRowActionsPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 2)).apply {
            add(tocChildButton)
            add(tocDeleteButton)
            isVisible = false
            border = JBUI.Borders.empty(2, 0, 0, 0)
        }

        val tocSectionBody = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(JBScrollPane(tree), BorderLayout.CENTER)
            add(tocRowActionsPanel, BorderLayout.SOUTH)
        }
        val tocSection = buildCollapsibleSection(
            title = uiMessage("topicTree.section.tableOfContents"),
            headerActions = tocHeaderActions,
            body = tocSectionBody,
            initiallyCollapsed = false,
        )

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(tocSection, BorderLayout.NORTH)
        }
    }

    private fun buildCollapsibleSection(
        title: String,
        headerActions: JComponent,
        body: JComponent,
        initiallyCollapsed: Boolean,
    ): JComponent {
        val bodyContainer = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(body, BorderLayout.CENTER)
            isVisible = !initiallyCollapsed
        }

        val leftHeader = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(JBLabel(title).apply { font = JBFont.label().deriveFont(JBFont.label().size + 1f) })
        }

        val header = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 8, 4, 8)
            add(leftHeader, BorderLayout.WEST)
            add(headerActions, BorderLayout.EAST)
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(bodyContainer, BorderLayout.CENTER)
        }
    }

    private fun buildStatusBar(): JComponent {
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(6, 10)
            add(status, BorderLayout.CENTER)
        }
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

    // todo 
    private fun buildTocContextMenu() {
        tocContextMenu.removeAll()
        // tocContextMenu.add(JMenuItem(uiMessage("topicTree.menu.newTopic")).apply { addActionListener { addRootTopic() } })
        tocNewChildMenuItem = JMenuItem(uiMessage("topicTree.menu.newChildTopic")).apply { addActionListener { addChildTopic() } }
        tocContextMenu.add(tocNewChildMenuItem)
        tocEditTitleMenuItem = JMenuItem(uiMessage("topicTree.menu.editTitle")).apply { addActionListener { renameTopic() } }
        tocContextMenu.add(tocEditTitleMenuItem)
        tocRemoveMenuItem = JMenuItem(uiMessage("topicTree.menu.removeTocElement")).apply { addActionListener { removeTopic() } }
        tocContextMenu.add(tocRemoveMenuItem)
        // tocContextMenu.add(JMenuItem(uiMessage("topicTree.menu.setAsHomePage")).apply { addActionListener { setAsHomePage() } })
    }

    private fun maybeShowTocContextMenu(event: MouseEvent) {
        if (!event.isPopupTrigger) {
            return
        }
        val row = tree.getRowForLocation(event.x, event.y)
        if (row >= 0) {
            tree.setSelectionRow(row)
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
            orderIndex = targetNode.childCount,
            sourcePath = sourcePath,
            childNodeId = nodeId,
        )
        handleDispatchResult(result, uiMessage("topicTree.status.addedChildTopic", title)) {
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
        handleDispatchResult(result, uiMessage("topicTree.status.renamedTopic", newTitle)) {
            reconcileAfterMutation(
                preferredNodeId = selected.nodeId,
                preferredPath = selected.path.orEmpty(),
                preferredParentNodeId = selected.parentNodeId,
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
            val parent = selectedNode.parent as? DefaultMutableTreeNode ?: return@handleDispatchResult
            model.removeNodeFromParent(selectedNode)
            tree.selectionPath = TreePath(parent.path)
        }
    }

    private fun setAsHomePage() {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus(uiMessage("topicTree.status.selectMutableTopic"))
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val parentNode = selectedNode.parent as? DefaultMutableTreeNode ?: return
        val parent = parentNode.userObject as? TopicTreeNodeView ?: return
        if (parent.nodeId != ROOT_NODE_ID) {
            publishStatus(uiMessage("topicTree.status.homePageRootOnly"))
            return
        }

        val siblings = (0 until parentNode.childCount)
            .mapNotNull { index -> parentNode.getChildAt(index) as? DefaultMutableTreeNode }
            .mapNotNull { node ->
                val view = node.userObject as? TopicTreeNodeView ?: return@mapNotNull null
                if (view.isMutable) view.nodeId to node else null
            }
        if (siblings.isEmpty()) {
            publishStatus(uiMessage("topicTree.status.noReorderableRootTopics"))
            return
        }

        val currentIndex = siblings.indexOfFirst { (nodeId, _) -> nodeId == selected.nodeId }
        if (currentIndex <= 0) {
            publishStatus(uiMessage("topicTree.status.alreadyHomePage"))
            return
        }

        val orderedNodeIds = siblings.map { (nodeId, _) -> nodeId }.toMutableList().apply {
            val moved = removeAt(currentIndex)
            add(0, moved)
        }
        val controllers = controllersOrNull() ?: return
        val result = controllers.dragDropController.reorderTopics(
            treeId = activeTreeId(),
            parentNodeId = ROOT_NODE_ID,
            orderedNodeIds = orderedNodeIds,
        )
        handleDispatchResult(result, uiMessage("topicTree.status.setAsHomePage", selected.title)) {
            val byId = siblings.associateBy({ it.first }, { it.second })
            parentNode.removeAllChildren()
            orderedNodeIds.forEach { nodeId ->
                parentNode.add(byId.getValue(nodeId))
            }
            model.reload(parentNode)
            tree.selectionPath = TreePath(selectedNode.path)
            tree.scrollPathToVisible(TreePath(selectedNode.path))
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
            newOrderIndex = newOrderIndex.coerceAtLeast(0),
        )

        return when (result.result) {
            is TopicGatewayResult.Success -> {
                val outcome = result.result.value
                if (!outcome.applied) {
                    publishStatus(uiMessage("topicTree.status.dragDropFailedSafely"))
                    return false
                }
                moveNodeInTree(draggedNode, targetParent, newOrderIndex)
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

        tocChildButton.isEnabled = hasNavSelection
        tocDeleteButton.isEnabled = hasMutableTocSelection
        tocChildButton.isVisible = hasNavSelection
        tocDeleteButton.isVisible = hasNavSelection
        tocRowActionsPanel.isVisible = hasNavSelection

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
        onSuccess: () -> Unit,
    ) {
        when (dispatch.result) {
            is TopicGatewayResult.Success -> {
                val outcome = dispatch.result.value
                if (!outcome.applied) {
                    val summary = uiMessage("topicTree.summary.operationFailedSafely")
                    publishStatus(summary)
                    return
                }
                onSuccess()
                publishStatus(successMessage)
            }

            is TopicGatewayResult.Failure -> {
                val recovery = dispatch.recovery
                val summary = recovery?.summary ?: uiMessage("topicTree.summary.operationFailed")
                publishStatus(summary)
            }
        }
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
        return runCatching { Path.of(docsDirPath).toAbsolutePath().normalize() }.getOrNull()
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
    ) {
        reconcileFromDisk()
        val preferredNode = findNode(preferredNodeId) ?: findNodeByRelativePath(preferredPath)
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
        openRelativePathInEditor(preferredPath)
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
        tree.selectionPath = path
        tree.scrollPathToVisible(path)
    }

    private fun openNodeFileInEditor(node: DefaultMutableTreeNode) {
        val view = node.userObject as? TopicTreeNodeView ?: return
        val absolutePath = resolveFilePathInternal(view, node) ?: return
        openFileInEditor(absolutePath)
    }

    private fun openRelativePathInEditor(relativePath: String) {
        val normalized = normalizeOptionalPath(relativePath) ?: return
        val docsDir = activeDocsDirectory() ?: return
        openFileInEditor(docsDir.resolve(normalized).normalize().toString())
    }

    private fun openFileInEditor(absolutePath: String) {
        val currentProject = project ?: return
        val fileSystem = LocalFileSystem.getInstance()
        val virtualFile = fileSystem.findFileByPath(absolutePath)
            ?: fileSystem.refreshAndFindFileByPath(absolutePath)
            ?: return
        FileEditorManager.getInstance(currentProject).openFile(virtualFile, true)
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
        return resolveFilePathInternal(node, treeNode)
    }

    private fun resolveFilePathInternal(node: TopicTreeNodeView, treeNode: DefaultMutableTreeNode?): String? {
        val relativePath = resolveRelativePathForOpen(node, treeNode) ?: return null
        val controllers = controllersProvider() ?: return null
        val activeInstanceId = activeTreeId()

        // Try getting active instance first
        val instanceResult = controllers.instanceRegistryPort?.selectActiveInstance(activeInstanceId)
        val instance = when (instanceResult) {
            is TopicGatewayResult.Success -> instanceResult.value
            else -> null
        }

        val docsDir = instance?.docsDirPath ?: return null
        return Path.of(docsDir).resolve(relativePath).normalize().toString()
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
        val treeNode = DefaultMutableTreeNode(
            TopicTreeNodeView(
                nodeId = node.nodeId,
                title = node.title,
                parentNodeId = parentNodeId,
                path = node.path,
                externalUrl = node.externalUrl,
            ),
        )
        node.children.forEach { child ->
            treeNode.add(toTreeNode(child, node.nodeId))
        }
        return treeNode
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
        return tooltips
    }

    internal fun tocContextMenuLabelsForTest(): List<String> {
        return tocContextMenu.components
            .filterIsInstance<JMenuItem>()
            .map { it.text }
    }

    internal fun tocRowActionStatesForTest(): Map<String, Boolean> {
        refreshActionEnablement()
        return mapOf(
            "Child" to tocChildButton.isEnabled,
            "Delete" to tocDeleteButton.isEnabled,
        )
    }

    internal fun headerActionVisibilityForTest(): Map<String, Boolean> {
        return mapOf(
            "Collapse All" to (collapseAllButton.isShowing || collapseAllButton.isVisible),
            "Root" to (addRootTopicButton.isShowing || addRootTopicButton.isVisible),
        )
    }

    internal fun triggerHeaderActionForTest(label: String): Boolean {
        refreshActionEnablement()
        val button = when (label) {
            "Collapse All" -> collapseAllButton
            "Root" -> addRootTopicButton
            else -> return false
        }
        if (!button.isEnabled) {
            return false
        }
        button.doClick()
        return true
    }

    internal fun selectTreeNodeForTest(nodeId: String): Boolean {
        val targetNode = findNode(nodeId) ?: return false
        focusNode(targetNode)
        return true
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
