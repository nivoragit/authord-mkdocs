package com.authord.mkdocs.ui.intellij

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Native JetBrains Swing panel for the empty-state "Create Documentation" form.
 *
 * Displayed when no configuration file is found in the project root.
 * Follows IntelliJ UI standards and automatically adapts to the IDE theme.
 *
 * @param onProjectCreate callback invoked with the entered project name when the user clicks Create or presses Enter.
 */
internal class SetupPanel(
    private val onProjectCreate: (String) -> Unit,
) : JBPanel<SetupPanel>(BorderLayout()) {

    private val nameField = JBTextField().apply {
        toolTipText = AuthordUiBundle.message("setup.tooltip.projectName")
        columns = 24
    }

    private val createButton = JButton(AuthordUiBundle.message("setup.button.create")).apply {
        icon = AllIcons.Actions.Execute
    }

    private val statusLabel = JBLabel("").apply {
        foreground = JBColor.GRAY
        font = JBFont.small()
    }
    private val requiredNameMessage = AuthordUiBundle.message("setup.error.projectNameRequired")

    init {
        buildUi()
        wireActions()
    }

    /**
     * Transitions the panel into a loading state, disabling interaction.
     */
    fun setLoading(loading: Boolean) {
        nameField.isEnabled = !loading
        createButton.isEnabled = !loading
        createButton.text = if (loading) AuthordUiBundle.message("setup.button.creating") else AuthordUiBundle.message("setup.button.create")
        statusLabel.foreground = JBColor.GRAY
        statusLabel.text = if (loading) AuthordUiBundle.message("setup.status.creating") else ""
    }

    // ---- private ----

    private fun buildUi() {
        val card = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(24, 32),
            )
            isOpaque = false
        }

        // Icon
        val iconLabel = JBLabel(AllIcons.FileTypes.Any_type).apply {
            alignmentX = CENTER_ALIGNMENT
        }
        card.add(iconLabel)
        card.add(Box.createRigidArea(Dimension(0, 12)))

        // Title
        val title = JBLabel(AuthordUiBundle.message("setup.title")).apply {
            font = JBFont.h2().asBold()
            alignmentX = CENTER_ALIGNMENT
        }
        card.add(title)
        card.add(Box.createRigidArea(Dimension(0, 6)))

        // Subtitle
        val subtitle = JBLabel(AuthordUiBundle.message("setup.subtitle")).apply {
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.CENTER
            alignmentX = CENTER_ALIGNMENT
        }
        card.add(subtitle)
        card.add(Box.createRigidArea(Dimension(0, 20)))

        // Field label
        val fieldLabel = JBLabel(AuthordUiBundle.message("setup.label.projectName")).apply {
            font = JBFont.small().asBold()
            foreground = JBColor.GRAY
            alignmentX = CENTER_ALIGNMENT
        }
        card.add(fieldLabel)
        card.add(Box.createRigidArea(Dimension(0, 6)))

        // Text field (centered via wrapper)
        val fieldWrapper = JPanel(GridBagLayout()).apply {
            isOpaque = false
            val gbc = GridBagConstraints()
            nameField.maximumSize = Dimension(300, nameField.preferredSize.height)
            nameField.preferredSize = Dimension(300, nameField.preferredSize.height)
            add(nameField, gbc)
        }
        card.add(fieldWrapper)
        card.add(Box.createRigidArea(Dimension(0, 14)))

        // Button (centered via wrapper)
        val buttonWrapper = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(createButton, GridBagConstraints())
        }
        card.add(buttonWrapper)
        card.add(Box.createRigidArea(Dimension(0, 10)))

        // Status label
        statusLabel.alignmentX = CENTER_ALIGNMENT
        card.add(statusLabel)

        // Hint
        val hint = JBLabel(AuthordUiBundle.message("setup.hint")).apply {
            font = JBFont.small()
            foreground = JBColor.GRAY
            alignmentX = CENTER_ALIGNMENT
        }
        card.add(Box.createRigidArea(Dimension(0, 8)))
        card.add(hint)

        // Center the card in the panel
        val wrapper = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(card, GridBagConstraints())
        }
        add(wrapper, BorderLayout.CENTER)
    }

    private fun wireActions() {
        createButton.addActionListener { submitForm() }

        nameField.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submit")
        nameField.actionMap.put(
            "submit",
            object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    submitForm()
                }
            },
        )
        nameField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = clearValidation()

                override fun removeUpdate(e: DocumentEvent?) = clearValidation()

                override fun changedUpdate(e: DocumentEvent?) = clearValidation()
            },
        )
    }

    private fun submitForm() {
        val name = nameField.text.trim()
        if (name.isBlank()) {
            statusLabel.foreground = JBColor.RED
            statusLabel.text = requiredNameMessage
            return
        }
        clearValidation()
        onProjectCreate(name)
    }

    private fun clearValidation() {
        if (statusLabel.text == requiredNameMessage) {
            statusLabel.foreground = JBColor.GRAY
            statusLabel.text = ""
        }
    }
}
