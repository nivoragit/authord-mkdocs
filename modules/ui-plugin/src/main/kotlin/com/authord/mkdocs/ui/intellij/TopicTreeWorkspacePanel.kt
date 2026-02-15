package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
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
import java.nio.file.Path
import java.util.UUID
import javax.swing.DefaultListModel
import javax.swing.DropMode
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTabbedPane
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

private const val ROOT_NODE_ID: String = "root"

private enum class TopicTreeNodeKind {
    ROOT,
    NAV,
    INFO,
}

private data class TopicTreeNodeView(
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
            title = "Topic Tree",
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
) {
    private val root = DefaultMutableTreeNode(TopicTreeNodeView.root())
    private val model = DefaultTreeModel(root)
    private val tree = JTree(model)
    private val treeSelectionBandColor = JBColor(Color(0xD7E8FF), Color(0x304D80))
    private val instanceSelectionBandColor = JBColor(Color(0xD7E8FF), Color(0x304D80))
    private val instanceModel = DefaultListModel<TopicInstanceRef>()
    private val instanceList = JBList(instanceModel)
    private var suppressInstanceSelectionEvents: Boolean = false
    private val status = JBLabel("Topic tree not loaded")
    private val details = JBTextArea()
    private val unlinkedModel = DefaultListModel<String>()
    private val validationModel = DefaultListModel<String>()
    private val unlinkedList = JBList(unlinkedModel)
    private val validationList = JBList(validationModel)
    private lateinit var reloadConfigurationButton: JButton
    private lateinit var newInstanceButton: JButton
    private lateinit var instancesOverflowButton: JButton
    private lateinit var collapseAllButton: JButton
    private lateinit var addRootTopicButton: JButton
    private lateinit var instanceDeleteButton: JButton
    private lateinit var instanceRenameButton: JButton
    private lateinit var instanceRowActionsPanel: JPanel
    private lateinit var tocDeleteButton: JButton
    private lateinit var tocChildButton: JButton
    private lateinit var tocRowActionsPanel: JPanel
    private lateinit var tocSetHomePageMenuItem: JMenuItem
    private lateinit var tocNewChildMenuItem: JMenuItem
    private lateinit var tocEditTitleMenuItem: JMenuItem
    private lateinit var tocRemoveMenuItem: JMenuItem
    private val instancesOverflowMenu = JPopupMenu()
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
            renderSelectionDetails()
            refreshActionEnablement()
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

        details.isEditable = false
        details.lineWrap = true
        details.wrapStyleWord = true
        details.border = JBUI.Borders.empty(8)
        details.background = tree.background
        details.text = "Select a topic to inspect details."

        instanceList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        instanceList.cellRenderer = object : javax.swing.ListCellRenderer<TopicInstanceRef> {
            private val delegate = DefaultTreeCellRenderer()
            override fun getListCellRendererComponent(
                list: javax.swing.JList<out TopicInstanceRef>,
                value: TopicInstanceRef?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val text = if (value == null) {
                    ""
                } else {
                    if (value.instanceId == currentState?.instanceId) {
                        "${value.instanceId} (active)"
                    } else {
                        value.instanceId
                    }
                }
                val label = delegate.getTreeCellRendererComponent(tree, text, isSelected, false, true, index, cellHasFocus) as javax.swing.JLabel
                label.border = if (isSelected) {
                    JBUI.Borders.customLine(instanceSelectionBandColor, 0, 4, 0, 0)
                } else {
                    JBUI.Borders.emptyLeft(4)
                }
                label.background = if (isSelected) instanceSelectionBandColor else list.background
                label.isOpaque = true
                return label
            }
        }
        instanceList.addListSelectionListener { event ->
            if (event.valueIsAdjusting || suppressInstanceSelectionEvents) {
                return@addListSelectionListener
            }
            onInstanceSelectionChanged()
        }

        buildInstancesOverflowMenu()
        buildTocContextMenu()
    }

    fun render(state: StartupTreeState) {
        currentState = state
        root.removeAllChildren()

        if (state.nodes.isEmpty()) {
            root.add(
                DefaultMutableTreeNode(
                    TopicTreeNodeView(
                        nodeId = "info-empty",
                        title = "No topics found",
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

        unlinkedModel.clear()
        state.unlinkedPaths.forEach(unlinkedModel::addElement)

        validationModel.clear()
        state.validationIssues
            .map { "${it.type}: ${it.reference}" }
            .forEach(validationModel::addElement)

        status.text = "Source: ${state.source.name.lowercase()} | Topics: ${state.navOrderedPaths.size} | Unlinked: ${state.unlinkedPaths.size} | Issues: ${state.validationIssues.size}"
        model.reload()
        if (root.childCount > 0) {
            tree.expandPath(TreePath(root.path))
        }
        refreshInstances(state.instanceId)
        renderSelectionDetails()
        refreshActionEnablement()
    }

    private fun buildHeader(): JComponent {
        val title = JBLabel("Authord").apply {
            font = JBFont.label().deriveFont(JBFont.label().size + 2f)
        }
        val subtitle = JBLabel("instances and table of contents").apply {
            foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            border = JBUI.Borders.emptyTop(2)
        }

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 10, 4, 10)
            add(title, BorderLayout.NORTH)
            add(subtitle, BorderLayout.SOUTH)
        }
    }

    private fun buildCenter(): JComponent {
        reloadConfigurationButton = iconActionButton(
            icon = AllIcons.Actions.Refresh,
            tooltip = "Reload Configuration",
        ) { reconcileFromDisk() }
        newInstanceButton = iconActionButton(icon = AllIcons.General.Add, tooltip = "New") { createNewInstance() }
        instancesOverflowButton = overflowActionButton { showInstancesOverflowMenu() }
        val instancesHeaderActions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            add(reloadConfigurationButton)
            add(newInstanceButton)
            add(instancesOverflowButton)
        }

        instanceRenameButton = iconActionButton(icon = AllIcons.Actions.Edit, tooltip = "Rename") { renameInstance() }
        instanceDeleteButton = iconActionButton(icon = AllIcons.General.Remove, tooltip = "Delete") { deleteInstance() }
        instanceRowActionsPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 2)).apply {
            add(instanceRenameButton)
            add(instanceDeleteButton)
            isVisible = false
            border = JBUI.Borders.empty(2, 0, 0, 0)
        }
        val instancesSectionBody = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(JBScrollPane(instanceList), BorderLayout.CENTER)
            add(instanceRowActionsPanel, BorderLayout.SOUTH)
        }
        val instancesSection = buildCollapsibleSection(
            title = "instances",
            headerActions = instancesHeaderActions,
            body = instancesSectionBody,
            initiallyCollapsed = false,
        )

        collapseAllButton = iconActionButton(icon = AllIcons.Actions.Collapseall, tooltip = "Collapse All") { collapseAllTopics() }
        addRootTopicButton = iconActionButton(icon = AllIcons.General.Add, tooltip = "Root") { addRootTopic() }
        val tocHeaderActions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            add(collapseAllButton)
            add(addRootTopicButton)
        }

        tocChildButton = iconActionButton(icon = AllIcons.General.Add, tooltip = "Child") { addChildTopic() }
        tocDeleteButton = iconActionButton(icon = AllIcons.General.Remove, tooltip = "Delete") { removeTopic() }
        tocRowActionsPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 2, 2)).apply {
            add(tocChildButton)
            add(tocDeleteButton)
            isVisible = false
            border = JBUI.Borders.empty(2, 0, 0, 0)
        }

        val diagnosticsTabs = JTabbedPane().apply {
            addTab("Details", JBScrollPane(details))
            addTab("Unlinked", JBScrollPane(unlinkedList))
            addTab("Validation", JBScrollPane(validationList))
        }

        val split = JBSplitter(true, 0.70f).apply {
            firstComponent = JBScrollPane(tree)
            secondComponent = diagnosticsTabs
        }

        val tocSectionBody = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(0, 8, 8, 8)
            add(split, BorderLayout.CENTER)
            add(tocRowActionsPanel, BorderLayout.SOUTH)
        }
        val tocSection = buildCollapsibleSection(
            title = "table of contents",
            headerActions = tocHeaderActions,
            body = tocSectionBody,
            initiallyCollapsed = false,
        )

        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(instancesSection, BorderLayout.NORTH)
            add(tocSection, BorderLayout.CENTER)
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
        val toggleButton = JButton(if (initiallyCollapsed) "+" else "-").apply {
            margin = JBUI.insets(0, 6)
            isFocusable = false
            addActionListener {
                bodyContainer.isVisible = !bodyContainer.isVisible
                text = if (bodyContainer.isVisible) "-" else "+"
                component.revalidate()
                component.repaint()
            }
        }

        val leftHeader = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
            add(toggleButton)
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

    private fun overflowActionButton(action: () -> Unit): JButton {
        return JButton("...").apply {
            toolTipText = "More"
            margin = JBUI.insets(2, 6)
            isFocusable = false
            addActionListener { action() }
        }
    }

    private fun buildInstancesOverflowMenu() {
        instancesOverflowMenu.removeAll()
        instancesOverflowMenu.add(
            JMenuItem("New Instance").apply {
                addActionListener { createNewInstance() }
            },
        )
        instancesOverflowMenu.add(
            JMenuItem("Rename").apply {
                isEnabled = false
                addActionListener { renameInstance() }
            },
        )
        instancesOverflowMenu.add(
            JMenuItem("Delete").apply {
                isEnabled = false
                addActionListener { deleteInstance() }
            },
        )
    }

    private fun buildTocContextMenu() {
        tocContextMenu.removeAll()
        tocContextMenu.add(JMenuItem("New Topic").apply { addActionListener { addRootTopic() } })
        tocNewChildMenuItem = JMenuItem("New Child Topic").apply { addActionListener { addChildTopic() } }
        tocContextMenu.add(tocNewChildMenuItem)
        tocEditTitleMenuItem = JMenuItem("Edit Title").apply { addActionListener { renameTopic() } }
        tocContextMenu.add(tocEditTitleMenuItem)
        tocRemoveMenuItem = JMenuItem("Remove TOC Element").apply { addActionListener { removeTopic() } }
        tocContextMenu.add(tocRemoveMenuItem)
        tocSetHomePageMenuItem = JMenuItem("Set as Home Page").apply { addActionListener { setAsHomePage() } }
        tocContextMenu.add(tocSetHomePageMenuItem)
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

    private fun showInstancesOverflowMenu() {
        refreshActionEnablement()
        instancesOverflowMenu.show(instancesOverflowButton, 0, instancesOverflowButton.height)
    }

    private fun refreshInstances(activeInstanceId: String) {
        val listedInstances = when (val result = controllersProvider()?.instanceRegistryPort?.listInstances()) {
            is TopicGatewayResult.Success -> result.value
            else -> emptyList()
        }
        val instances = if (listedInstances.isNotEmpty()) {
            listedInstances
        } else {
            listOf(
                TopicInstanceRef(
                    instanceId = activeInstanceId,
                    configPath = "",
                    docsDirPath = "",
                ),
            )
        }
        suppressInstanceSelectionEvents = true
        instanceModel.clear()
        instances.sortedBy { it.instanceId }.forEach(instanceModel::addElement)
        val activeIndex = (0 until instanceModel.size())
            .firstOrNull { index -> instanceModel.get(index).instanceId == activeInstanceId }
            ?: 0
        if (instanceModel.size() > 0) {
            instanceList.selectedIndex = activeIndex
        }
        suppressInstanceSelectionEvents = false
    }

    private fun onInstanceSelectionChanged() {
        val selectedInstance = instanceList.selectedValue ?: return
        if (selectedInstance.instanceId == currentState?.instanceId) {
            refreshActionEnablement()
            return
        }
        val controllers = controllersOrNull() ?: return
        val switchResult = controllers.instanceSwitchCoordinator.switchActiveInstance(selectedInstance.instanceId)
        when (switchResult) {
            is TopicGatewayResult.Success -> {
                publishStatus("Switched active instance to '${switchResult.value.selectedInstance.instanceId}'")
                reconcileFromDisk()
            }

            is TopicGatewayResult.Failure -> {
                val recovery = controllers.failureRecoveryPresenter.present("Switch instance", switchResult.error)
                publishStatus(recovery.summary)
                details.text = "${recovery.summary}\n${recovery.guidance}"
            }
        }
        refreshActionEnablement()
    }

    private fun createNewInstance() {
        val controllers = controllersOrNull() ?: return
        val instanceRegistry = controllers.instanceRegistryPort ?: run {
            publishStatus("Instance registration is unavailable")
            refreshActionEnablement()
            return
        }
        val instanceId = prompt("New Instance", "Instance ID") ?: return
        val configInput = prompt("New Instance", "Config path (mkdocs.yml or mkdocs.yaml)", defaultMkdocsConfigPath()) ?: return
        val configPath = resolveConfigPathForRegistration(configInput)
        val defaultDocsDir = runCatching {
            Path.of(configPath).toAbsolutePath().normalize().parent.resolve("docs").toString()
        }.getOrDefault("docs")
        val docsDirPath = promptOptional("New Instance", "Docs directory path (optional)") ?: defaultDocsDir
        val registration = instanceRegistry.registerInstance(
            TopicInstanceRef(
                instanceId = instanceId,
                configPath = configPath,
                docsDirPath = docsDirPath,
            ),
        )
        when (registration) {
            is TopicGatewayResult.Success -> {
                refreshInstances(activeInstanceId = instanceId)
                onInstanceSelectionChanged()
                publishStatus("Registered instance '$instanceId'")
            }

            is TopicGatewayResult.Failure -> {
                val recovery = controllers.failureRecoveryPresenter.present("Register instance", registration.error)
                publishStatus(recovery.summary)
                details.text = "${recovery.summary}\n${recovery.guidance}"
            }
        }
        refreshActionEnablement()
    }

    private fun resolveConfigPathForRegistration(configInput: String): String {
        val trimmed = configInput.trim()
        val baseRoot = defaultConfigBaseDir()
        val expanded = if (trimmed.startsWith("~/")) {
            Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString() + trimmed.removePrefix("~")
        } else {
            trimmed
        }
        val rawPath = runCatching { Path.of(expanded) }.getOrNull() ?: return expanded
        if (rawPath.isAbsolute) {
            return rawPath.normalize().toString()
        }
        return baseRoot.resolve(rawPath).normalize().toString()
    }

    private fun defaultMkdocsConfigPath(): String {
        return defaultConfigBaseDir()
            .resolve("mkdocs.yml")
            .toString()
    }

    private fun defaultConfigBaseDir(): Path {
        val projectBase = projectRootPath
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
        return projectBase ?: Path.of(System.getProperty("user.home")).toAbsolutePath().normalize()
    }

    private fun renameInstance() {
        publishStatus("Instance rename is not available in current contracts")
    }

    private fun deleteInstance() {
        publishStatus("Instance delete is not available in current contracts")
    }

    private fun collapseAllTopics() {
        for (row in tree.rowCount - 1 downTo 1) {
            tree.collapseRow(row)
        }
        publishStatus("Collapsed all topics")
    }

    private fun addRootTopic() {
        addTopic(parentOverride = root)
    }

    fun reconcileFromDisk() {
        val state = reconcileStateProvider()
        if (state == null) {
            publishStatus("Reconciliation unavailable (missing project/base config)")
            refreshActionEnablement()
            return
        }
        render(state)
        startupStateListener(state)
        details.text = "Reconciliation completed from mkdocs config + docs_dir scan."
        refreshActionEnablement()
    }

    private fun switchInstance() {
        val targetInstanceId = prompt("Switch Instance", "Instance ID", activeTreeId()) ?: return
        val controllers = controllersOrNull() ?: return
        val switchResult = controllers.instanceSwitchCoordinator.switchActiveInstance(targetInstanceId)
        when (switchResult) {
            is TopicGatewayResult.Success -> {
                publishStatus("Switched active instance to '${switchResult.value.selectedInstance.instanceId}'")
                reconcileFromDisk()
            }

            is TopicGatewayResult.Failure -> {
                val recovery = controllers.failureRecoveryPresenter.present("Switch instance", switchResult.error)
                publishStatus(recovery.summary)
                details.text = "${recovery.summary}\n${recovery.guidance}"
            }
        }
    }

    private fun addTopic(parentOverride: DefaultMutableTreeNode? = null) {
        val parentNode = parentOverride ?: selectedNavNode() ?: root
        val parent = parentNode.userObject as? TopicTreeNodeView ?: TopicTreeNodeView.root()
        val title = prompt("Add Topic", "Topic title") ?: return
        val sourcePath = promptOptional("Add Topic", "Relative markdown path (optional)")
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
        handleDispatchResult(result, "Created topic '$title'") {
            val uiNode = DefaultMutableTreeNode(
                TopicTreeNodeView(
                    nodeId = nodeId,
                    title = title,
                    parentNodeId = parent.nodeId,
                    path = normalizeOptionalPath(sourcePath),
                ),
            )
            model.insertNodeInto(uiNode, parentNode, parentNode.childCount)
            tree.selectionPath = TreePath(uiNode.path)
            tree.scrollPathToVisible(TreePath(uiNode.path))
        }
    }

    private fun addChildTopic() {
        val targetNode = selectedNavNode()
        if (targetNode == null) {
            publishStatus("Select a topic first to add a child")
            return
        }
        val target = targetNode.userObject as? TopicTreeNodeView ?: return
        val title = prompt("Add Child Topic", "Child topic title") ?: return
        val sourcePath = promptOptional("Add Child Topic", "Relative markdown path (optional)")
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
        handleDispatchResult(result, "Added child topic '$title'") {
            val uiNode = DefaultMutableTreeNode(
                TopicTreeNodeView(
                    nodeId = nodeId,
                    title = title,
                    parentNodeId = target.nodeId,
                    path = normalizeOptionalPath(sourcePath),
                ),
            )
            model.insertNodeInto(uiNode, targetNode, targetNode.childCount)
            tree.expandPath(TreePath(targetNode.path))
            tree.selectionPath = TreePath(uiNode.path)
            tree.scrollPathToVisible(TreePath(uiNode.path))
        }
    }

    private fun addExistingFile() {
        val parentNode = selectedNavNode() ?: root
        val parent = parentNode.userObject as? TopicTreeNodeView ?: TopicTreeNodeView.root()
        val title = prompt("Add Existing File", "Display title") ?: return
        val relativePath = prompt("Add Existing File", "Relative markdown path (example: guide/index.md)") ?: return
        val nodeId = "ui-node-${UUID.randomUUID()}"
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.addExistingFile(
            treeId = activeTreeId(),
            parentNodeId = parent.nodeId,
            title = title,
            relativePath = relativePath,
            orderIndex = parentNode.childCount,
            nodeId = nodeId,
        )
        handleDispatchResult(result, "Added existing file '$relativePath'") {
            val uiNode = DefaultMutableTreeNode(
                TopicTreeNodeView(
                    nodeId = nodeId,
                    title = title,
                    parentNodeId = parent.nodeId,
                    path = normalizeOptionalPath(relativePath),
                ),
            )
            model.insertNodeInto(uiNode, parentNode, parentNode.childCount)
            tree.selectionPath = TreePath(uiNode.path)
            tree.scrollPathToVisible(TreePath(uiNode.path))
        }
    }

    private fun addExternalLink() {
        val parentNode = selectedNavNode() ?: root
        val parent = parentNode.userObject as? TopicTreeNodeView ?: TopicTreeNodeView.root()
        val title = prompt("Add External Link", "Display title") ?: return
        val url = prompt("Add External Link", "URL (https://...)") ?: return
        val nodeId = "ui-node-${UUID.randomUUID()}"
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.addExternalLink(
            treeId = activeTreeId(),
            parentNodeId = parent.nodeId,
            title = title,
            externalUrl = url,
            orderIndex = parentNode.childCount,
            nodeId = nodeId,
        )
        handleDispatchResult(result, "Added external link '$title'") {
            val uiNode = DefaultMutableTreeNode(
                TopicTreeNodeView(
                    nodeId = nodeId,
                    title = title,
                    parentNodeId = parent.nodeId,
                    externalUrl = url.trim(),
                ),
            )
            model.insertNodeInto(uiNode, parentNode, parentNode.childCount)
            tree.selectionPath = TreePath(uiNode.path)
            tree.scrollPathToVisible(TreePath(uiNode.path))
        }
    }

    private fun renameTopic() {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus("Select a mutable topic first")
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val newTitle = prompt("Rename Topic", "New title", selected.title) ?: return
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.renameTopic(
            treeId = activeTreeId(),
            nodeId = selected.nodeId,
            newTitle = newTitle,
        )
        handleDispatchResult(result, "Renamed topic to '$newTitle'") {
            selectedNode.userObject = selected.copy(title = newTitle)
            model.nodeChanged(selectedNode)
        }
    }

    private fun removeTopic() {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus("Select a mutable topic first")
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val controllers = controllersOrNull() ?: return
        val result = controllers.actionController.removeTopic(
            treeId = activeTreeId(),
            nodeId = selected.nodeId,
        )
        handleDispatchResult(result, "Removed topic '${selected.title}'") {
            val parent = selectedNode.parent as? DefaultMutableTreeNode ?: return@handleDispatchResult
            model.removeNodeFromParent(selectedNode)
            tree.selectionPath = TreePath(parent.path)
        }
    }

    private fun moveByReorder(direction: Int) {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus("Select a mutable topic to reorder")
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val parentNode = selectedNode.parent as? DefaultMutableTreeNode ?: return
        val parent = parentNode.userObject as? TopicTreeNodeView ?: return

        val siblings = (0 until parentNode.childCount)
            .mapNotNull { index -> parentNode.getChildAt(index) as? DefaultMutableTreeNode }
            .mapNotNull { node ->
                val view = node.userObject as? TopicTreeNodeView ?: return@mapNotNull null
                if (!view.isMutable) {
                    null
                } else {
                    view.nodeId to node
                }
            }

        val currentIndex = siblings.indexOfFirst { (nodeId, _) -> nodeId == selected.nodeId }
        if (currentIndex == -1) {
            return
        }
        val targetIndex = currentIndex + direction
        if (targetIndex !in siblings.indices) {
            return
        }

        val orderedNodeIds = siblings.map { (nodeId, _) -> nodeId }.toMutableList()
        val movingId = orderedNodeIds.removeAt(currentIndex)
        orderedNodeIds.add(targetIndex, movingId)

        val controllers = controllersOrNull() ?: return
        val result = controllers.dragDropController.reorderTopics(
            treeId = activeTreeId(),
            parentNodeId = parent.nodeId,
            orderedNodeIds = orderedNodeIds,
        )
        handleDispatchResult(result, "Reordered topics under '${parent.title}'") {
            val byId = siblings.associateBy({ it.first }, { it.second })
            parentNode.removeAllChildren()
            orderedNodeIds.forEach { nodeId ->
                parentNode.add(byId.getValue(nodeId))
            }
            model.reload(parentNode)
            val movedNode = byId[movingId]
            if (movedNode != null) {
                tree.selectionPath = TreePath(movedNode.path)
                tree.scrollPathToVisible(TreePath(movedNode.path))
            }
        }
    }

    private fun setAsHomePage() {
        val selectedNode = selectedMutableNode() ?: run {
            publishStatus("Select a mutable topic first")
            return
        }
        val selected = selectedNode.userObject as? TopicTreeNodeView ?: return
        val parentNode = selectedNode.parent as? DefaultMutableTreeNode ?: return
        val parent = parentNode.userObject as? TopicTreeNodeView ?: return
        if (parent.nodeId != ROOT_NODE_ID) {
            publishStatus("Home page can only be set for root-level topics")
            return
        }

        val siblings = (0 until parentNode.childCount)
            .mapNotNull { index -> parentNode.getChildAt(index) as? DefaultMutableTreeNode }
            .mapNotNull { node ->
                val view = node.userObject as? TopicTreeNodeView ?: return@mapNotNull null
                if (view.isMutable) view.nodeId to node else null
            }
        if (siblings.isEmpty()) {
            publishStatus("No reorderable root topics available")
            return
        }

        val currentIndex = siblings.indexOfFirst { (nodeId, _) -> nodeId == selected.nodeId }
        if (currentIndex <= 0) {
            publishStatus("Selected topic is already the home page")
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
        handleDispatchResult(result, "Set '${selected.title}' as home page") {
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
                moveNodeInTree(draggedNode, targetParent, newOrderIndex)
                details.text = "Drag-drop applied for node '$draggedNodeId'."
                true
            }

            is TopicGatewayResult.Failure -> {
                val recovery = result.recovery
                details.text = buildString {
                    append(recovery?.summary ?: "Drag-drop failed")
                    append('\n')
                    append(recovery?.guidance ?: "No additional guidance available.")
                }
                publishStatus(recovery?.summary ?: "Drag-drop failed")
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
        val controllers = controllersProvider()
        val hasInstanceSelection = instanceList.selectedValue != null
        val hasNavSelection = selectedNavNode() != null
        val hasMutableTocSelection = selectedMutableNode() != null

        newInstanceButton.isEnabled = controllers?.instanceRegistryPort != null
        instanceRenameButton.isEnabled = false
        instanceDeleteButton.isEnabled = false
        instanceRenameButton.isVisible = hasInstanceSelection
        instanceDeleteButton.isVisible = hasInstanceSelection
        instanceRowActionsPanel.isVisible = hasInstanceSelection

        tocChildButton.isEnabled = hasNavSelection
        tocDeleteButton.isEnabled = hasMutableTocSelection
        tocChildButton.isVisible = hasNavSelection
        tocDeleteButton.isVisible = hasNavSelection
        tocRowActionsPanel.isVisible = hasNavSelection

        tocNewChildMenuItem.isEnabled = hasNavSelection
        tocEditTitleMenuItem.isEnabled = hasMutableTocSelection
        tocRemoveMenuItem.isEnabled = hasMutableTocSelection
        tocSetHomePageMenuItem.isEnabled = canSetAsHomePage()

        instancesOverflowMenu.components
            .filterIsInstance<JMenuItem>()
            .forEachIndexed { index, menuItem ->
                menuItem.isEnabled = when (index) {
                    0 -> controllers?.instanceRegistryPort != null
                    else -> false
                }
            }
    }

    private fun canSetAsHomePage(): Boolean {
        val selectedNode = selectedMutableNode() ?: return false
        val parentNode = selectedNode.parent as? DefaultMutableTreeNode ?: return false
        val parent = parentNode.userObject as? TopicTreeNodeView ?: return false
        if (parent.nodeId != ROOT_NODE_ID) {
            return false
        }
        val mutableSiblingIds = (0 until parentNode.childCount)
            .mapNotNull { index -> parentNode.getChildAt(index) as? DefaultMutableTreeNode }
            .mapNotNull { node ->
                val view = node.userObject as? TopicTreeNodeView ?: return@mapNotNull null
                if (view.isMutable) view.nodeId else null
            }
        if (mutableSiblingIds.size <= 1) {
            return false
        }
        val selectedId = (selectedNode.userObject as? TopicTreeNodeView)?.nodeId ?: return false
        return mutableSiblingIds.firstOrNull() != selectedId
    }

    private fun controllersOrNull(): TopicTreeControllers? {
        val controllers = controllersProvider()
        if (controllers == null) {
            publishStatus("Topic-tree controllers are not available")
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
                onSuccess()
                publishStatus(successMessage)
                details.text = "Success: $successMessage"
            }

            is TopicGatewayResult.Failure -> {
                val recovery = dispatch.recovery
                val summary = recovery?.summary ?: "Operation failed"
                publishStatus(summary)
                details.text = buildString {
                    append(summary)
                    append('\n')
                    append(recovery?.guidance ?: "No recovery guidance available.")
                }
            }
        }
    }

    private fun renderSelectionDetails() {
        val selected = selectedNavNode()?.userObject as? TopicTreeNodeView
        if (selected == null) {
            details.text = "Select a topic to inspect details."
            return
        }
        details.text = buildString {
            append("Title: ${selected.title}\n")
            append("Node ID: ${selected.nodeId}\n")
            append("Parent ID: ${selected.parentNodeId ?: "-"}\n")
            append("Path: ${selected.path ?: "-"}\n")
            append("External URL: ${selected.externalUrl ?: "-"}")
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

    private fun promptOptional(title: String, message: String): String? {
        val value = promptInputProvider(title, message, null)
            ?.trim()
            ?: return null
        return value.takeIf { it.isNotEmpty() }
    }

    private fun activeTreeId(): String = currentState?.instanceId ?: "default"

    private fun normalizeOptionalPath(path: String?): String? {
        return path
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('\\', '/')
            ?.trimStart('/')
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

    internal fun instancesOverflowMenuLabelsForTest(): List<String> {
        return instancesOverflowMenu.components
            .filterIsInstance<JMenuItem>()
            .map { it.text }
    }

    internal fun instancesOverflowMenuStatesForTest(): List<Pair<String, Boolean>> {
        refreshActionEnablement()
        return instancesOverflowMenu.components
            .filterIsInstance<JMenuItem>()
            .map { it.text to it.isEnabled }
    }

    internal fun tocContextMenuLabelsForTest(): List<String> {
        return tocContextMenu.components
            .filterIsInstance<JMenuItem>()
            .map { it.text }
    }

    internal fun instanceActionStatesForTest(): Map<String, Boolean> {
        refreshActionEnablement()
        return mapOf(
            "Rename" to instanceRenameButton.isEnabled,
            "Delete" to instanceDeleteButton.isEnabled,
        )
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
            "Reload Configuration" to (reloadConfigurationButton.isShowing || reloadConfigurationButton.isVisible),
            "New" to (newInstanceButton.isShowing || newInstanceButton.isVisible),
            "Collapse All" to (collapseAllButton.isShowing || collapseAllButton.isVisible),
            "Root" to (addRootTopicButton.isShowing || addRootTopicButton.isVisible),
        )
    }

    internal fun selectTreeNodeForTest(nodeId: String): Boolean {
        val targetNode = findNode(nodeId) ?: return false
        tree.selectionPath = TreePath(targetNode.path)
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
