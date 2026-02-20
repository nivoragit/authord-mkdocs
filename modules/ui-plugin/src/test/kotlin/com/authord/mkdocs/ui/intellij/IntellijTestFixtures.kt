package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPopupMenu
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusConnection
import java.awt.Component
import java.awt.event.InputEvent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

object IntellijTestFixtures {
    private val actionManagerFixture: ActionManager = object : ActionManager() {
        override fun createActionPopupMenu(place: String, group: ActionGroup): ActionPopupMenu {
            error("Not used in tests")
        }

        override fun createActionToolbar(place: String, group: ActionGroup, horizontal: Boolean): ActionToolbar {
            error("Not used in tests")
        }

        override fun getAction(id: String): AnAction? = null

        override fun getId(action: AnAction): String = "fixture-action"

        override fun registerAction(actionId: String, action: AnAction) = Unit

        override fun registerAction(actionId: String, action: AnAction, pluginId: PluginId?) = Unit

        override fun unregisterAction(actionId: String) = Unit

        override fun replaceAction(actionId: String, newAction: AnAction) = Unit

        override fun getActionIds(idPrefix: String): Array<String> = emptyArray()

        override fun getActionIdList(idPrefix: String): List<String> = emptyList()

        override fun isGroup(actionId: String): Boolean = false

        override fun getActionOrStub(id: String): AnAction? = null

        override fun addTimerListener(listener: TimerListener) = Unit

        override fun removeTimerListener(listener: TimerListener) = Unit

        override fun tryToExecute(
            action: AnAction,
            inputEvent: InputEvent?,
            contextComponent: Component?,
            place: String?,
            now: Boolean,
        ): ActionCallback = ActionCallback.DONE

        override fun addAnActionListener(listener: AnActionListener) = Unit

        override fun getKeyboardShortcut(actionId: String): KeyboardShortcut? = null
    }

    fun actionEvent(action: AnAction, project: Project?, virtualFilePath: String? = null): AnActionEvent {
        val virtualFile = virtualFilePath?.let(::virtualFile)
        val context = DataContext { dataId ->
            when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> virtualFile
                else -> null
            }
        }
        return AnActionEvent(
            null,
            context,
            ActionPlaces.UNKNOWN,
            action.templatePresentation.clone(),
            actionManagerFixture,
            0,
        )
    }

    private fun virtualFile(path: String): VirtualFile {
        val normalizedPath = path.replace('\\', '/')
        val name = normalizedPath.substringAfterLast('/')
        return object : LightVirtualFile(name) {
            override fun getPath(): String = normalizedPath
        }
    }

    fun project(
        basePath: String? = "/tmp/project",
        locationHash: String = "project-hash",
        services: Map<Class<*>, Any> = emptyMap(),
    ): Project {
        ensureSyntheticProjectRoot(basePath)
        val userData = mutableMapOf<Key<*>, Any?>()
        val messageBusConnection = dynamicProxy(MessageBusConnection::class.java) { _, method, _ ->
            when (method.name) {
                "subscribe", "setDefaultHandler", "deliverImmediately", "disconnect", "dispose" -> null
                else -> defaultValue(method.returnType)
            }
        }
        val messageBus = dynamicProxy(MessageBus::class.java) { _, method, _ ->
            when (method.name) {
                "connect" -> messageBusConnection
                "dispose" -> null
                else -> defaultValue(method.returnType)
            }
        }

        return dynamicProxy(Project::class.java) { proxy, method, args ->
            when (method.name) {
                "getBasePath" -> basePath
                "getLocationHash" -> locationHash
                "isDisposed" -> false
                "getMessageBus" -> messageBus
                "getService" -> services[args?.get(0)]
                "getUserData" -> userData[args?.get(0)]
                "putUserData" -> {
                    val key = args?.get(0) as Key<Any?>
                    userData[key] = args[1]
                    null
                }
                "toString" -> "ProjectFixture($locationHash)"
                "hashCode" -> locationHash.hashCode()
                "equals" -> args?.get(0) === proxy
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun ensureSyntheticProjectRoot(basePath: String?) {
        if (basePath != "/tmp/project") {
            return
        }
        runCatching {
            val root = Path.of(basePath)
            Files.createDirectories(root)
            val configPath = root.resolve("mkdocs.yml")
            if (!Files.exists(configPath)) {
                Files.writeString(
                    configPath,
                    """
                        site_name: Fixture Docs
                        docs_dir: docs
                    """.trimIndent() + "\n",
                )
            }
        }
    }

    fun toolWindowFixture(toolWindowId: String? = null): ToolWindowFixture {
        val removedAllCalls = mutableListOf<Boolean>()
        val addedComponents = mutableListOf<JComponent>()

        val contentFactory = dynamicProxy(ContentFactory::class.java) { _, method, args ->
            when (method.name) {
                "createContent" -> {
                    val component = args?.get(0) as JComponent
                    content(component)
                }
                else -> defaultValue(method.returnType)
            }
        }

        val contentManager = dynamicProxy(ContentManager::class.java) { _, method, args ->
            when (method.name) {
                "getFactory" -> contentFactory
                "removeAllContents" -> {
                    removedAllCalls += (args?.get(0) as Boolean)
                    null
                }
                "addContent" -> {
                    val content = args?.get(0) as Content
                    addedComponents += content.component
                    null
                }
                else -> defaultValue(method.returnType)
            }
        }

        val toolWindow = dynamicProxy(ToolWindow::class.java) { _, method, _ ->
            when (method.name) {
                "getContentManager" -> contentManager
                "getId" -> toolWindowId
                else -> defaultValue(method.returnType)
            }
        }

        return ToolWindowFixture(
            toolWindow = toolWindow,
            removedAllCalls = removedAllCalls,
            addedComponents = addedComponents,
        )
    }

    private fun content(component: JComponent): Content {
        return dynamicProxy(Content::class.java) { _, method, _ ->
            when (method.name) {
                "getComponent" -> component
                "isValid" -> true
                "getActionsContextComponent" -> component
                "getUserDataString" -> null
                "putUserDataString" -> null
                "setPreferredFocusedComponent" -> null
                "setHelpId" -> null
                "setExecutionId" -> null
                "setPinned" -> null
                "setDisposer" -> null
                "setDisplayName" -> null
                "getDisplayName" -> ""
                "setTabName" -> null
                "setToolwindowTitle" -> null
                "setIcon" -> null
                "setSearchComponent" -> null
                "setDescription" -> null
                "setCloseable" -> null
                "setShouldDisposeContent" -> null
                "setAlertIcon" -> null
                "setBusyObject" -> null
                "setPreferredFocusableComponent" -> null
                "release" -> null
                "getExecutionId" -> 0L
                "isPinned" -> false
                "isPinnable" -> true
                "isCloseable" -> true
                "isAlertIcon" -> false
                "isDisposed" -> false
                "isSelected" -> false
                "getAlertIcon" -> false
                "getBusyObject" -> null
                "setExecutionEnvironment" -> null
                "isValidForQuickAction" -> true
                "getPreferredFocusableComponent" -> component
                "toString" -> "ContentFixture"
                "hashCode" -> component.hashCode()
                "equals" -> false
                else -> defaultValue(method.returnType)
            }
        }
    }

    private fun <T> dynamicProxy(type: Class<T>, handler: InvocationHandler): T {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T
    }

    private fun defaultValue(returnType: Class<*>): Any? {
        return when {
            !returnType.isPrimitive -> null
            returnType == Boolean::class.javaPrimitiveType -> false
            returnType == Char::class.javaPrimitiveType -> '\u0000'
            returnType == Byte::class.javaPrimitiveType -> 0.toByte()
            returnType == Short::class.javaPrimitiveType -> 0.toShort()
            returnType == Int::class.javaPrimitiveType -> 0
            returnType == Long::class.javaPrimitiveType -> 0L
            returnType == Float::class.javaPrimitiveType -> 0f
            returnType == Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }
}

data class ToolWindowFixture(
    val toolWindow: ToolWindow,
    val removedAllCalls: MutableList<Boolean>,
    val addedComponents: MutableList<JComponent>,
)
