package com.authord.mkdocs.ui.intellij

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.geom.Path2D
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants

/**
 * Native JetBrains Swing setup surface shown when no MkDocs configuration exists.
 */
internal class SetupPanel(
    private val onProjectCreate: (String) -> Unit,
    private val suggestedProjectName: String = AuthordUiBundle.message("activation.default.siteName"),
    private val requestProjectName: (String) -> String? = ::requestProjectNameFromUser,
    private val onGettingStarted: () -> Unit = {
        BrowserUtil.browse(AuthordUiBundle.message("setup.gettingStarted.url"))
    },
) : JBPanel<SetupPanel>(BorderLayout()) {

    private val mutedTextColor = JBColor(0x6F7683, 0x767F8D)
    private val accentColor = JBColor(0x5E95FF, 0x6EA1FF)
    private val helpIconColor = JBColor(0x778090, 0x76808D)

    private val addDocumentationButton = JButton(AuthordUiBundle.message("setup.button.add")).apply {
        icon = ChevronDownIcon(accentColor)
        iconTextGap = JBUI.scale(6)
        horizontalAlignment = SwingConstants.CENTER
        horizontalTextPosition = SwingConstants.LEFT
        border = JBUI.Borders.empty()
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        isFocusPainted = false
        foreground = accentColor
        font = JBFont.label().deriveFont(16f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        name = "setup-add-documentation-button"
    }

    private val gettingStartedButton = JButton(AuthordUiBundle.message("setup.link.gettingStarted")).apply {
        border = JBUI.Borders.empty()
        isBorderPainted = false
        isContentAreaFilled = false
        isOpaque = false
        isFocusPainted = false
        foreground = accentColor
        font = JBFont.label().deriveFont(16f)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        name = "setup-getting-started-link"
    }
    private val validationMessage = JBLabel("").apply {
        alignmentX = CENTER_ALIGNMENT
        horizontalAlignment = SwingConstants.CENTER
        foreground = JBColor.RED
        font = JBFont.small()
    }

    init {
        buildUi()
        wireActions()
    }

    /**
     * Transitions the panel into a loading state and guards duplicate setup requests.
     */
    fun setLoading(loading: Boolean) {
        addDocumentationButton.isEnabled = !loading
        addDocumentationButton.text = if (loading) {
            AuthordUiBundle.message("setup.button.add.loading")
        } else {
            AuthordUiBundle.message("setup.button.add")
        }
    }

    private fun buildUi() {
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val title = JBLabel(AuthordUiBundle.message("setup.emptyState.title")).apply {
            alignmentX = CENTER_ALIGNMENT
            horizontalAlignment = SwingConstants.CENTER
            foreground = mutedTextColor
            font = JBFont.label().deriveFont(16f)
        }

        val gettingStartedRow = JPanel(FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0)).apply {
            isOpaque = false
            alignmentX = CENTER_ALIGNMENT
            add(JBLabel(QuestionCircleIcon(helpIconColor)))
            add(gettingStartedButton)
        }

        content.add(title)
        content.add(Box.createRigidArea(Dimension(0, JBUI.scale(14))))
        content.add(addDocumentationButton)
        content.add(Box.createRigidArea(Dimension(0, JBUI.scale(8))))
        content.add(validationMessage)
        content.add(Box.createRigidArea(Dimension(0, JBUI.scale(42))))
        content.add(gettingStartedRow)

        val wrapper = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(content, GridBagConstraints())
        }
        add(wrapper, BorderLayout.CENTER)
    }

    private fun wireActions() {
        addDocumentationButton.addActionListener { submitForm() }
        gettingStartedButton.addActionListener { onGettingStarted() }

        val submitAction = object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) {
                submitForm()
            }
        }
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit")
        actionMap.put("submit", submitAction)
    }

    private fun submitForm() {
        if (!addDocumentationButton.isEnabled) {
            return
        }
        val requestedName = requestProjectName(resolveProjectName())?.trim()
        if (requestedName.isNullOrBlank()) {
            validationMessage.text = AuthordUiBundle.message("setup.error.projectNameRequired")
            return
        }
        validationMessage.text = ""
        onProjectCreate(requestedName)
    }

    private fun resolveProjectName(): String {
        val normalized = suggestedProjectName.trim()
        return normalized.ifBlank { AuthordUiBundle.message("activation.default.siteName") }
    }

    private companion object {
        fun requestProjectNameFromUser(initial: String): String? {
            val app = ApplicationManager.getApplication()
            if (GraphicsEnvironment.isHeadless() || app == null || !app.isDispatchThread) {
                return initial
            }
            return Messages.showInputDialog(
                AuthordUiBundle.message("setup.prompt.projectName.message"),
                AuthordUiBundle.message("setup.prompt.projectName.title"),
                Messages.getQuestionIcon(),
                initial,
                null,
            )
        }
    }
}

private class ChevronDownIcon(
    private val color: JBColor,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(9)

    override fun getIconHeight(): Int = JBUI.scale(6)

    override fun paintIcon(component: java.awt.Component?, graphics: Graphics, x: Int, y: Int) {
        val g2 = graphics.create() as Graphics2D
        g2.color = color
        g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val path = Path2D.Float().apply {
            moveTo((x + 1).toFloat(), (y + 1).toFloat())
            lineTo((x + (iconWidth / 2f)).toFloat(), (y + iconHeight - 1f).toFloat())
            lineTo((x + iconWidth - 1).toFloat(), (y + 1).toFloat())
        }
        g2.draw(path)
        g2.dispose()
    }
}

private class QuestionCircleIcon(
    private val color: JBColor,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(24)

    override fun getIconHeight(): Int = JBUI.scale(24)

    override fun paintIcon(component: java.awt.Component?, graphics: Graphics, x: Int, y: Int) {
        val g2 = graphics.create() as Graphics2D
        g2.color = color
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g2.drawOval(x + 1, y + 1, iconWidth - 3, iconHeight - 3)
        g2.font = JBFont.label().asBold().deriveFont(13f)
        val marker = "?"
        val metrics = g2.fontMetrics
        val markerX = x + ((iconWidth - metrics.stringWidth(marker)) / 2)
        val markerY = y + ((iconHeight - metrics.height) / 2) + metrics.ascent - 1
        g2.drawString(marker, markerX, markerY)
        g2.dispose()
    }
}
