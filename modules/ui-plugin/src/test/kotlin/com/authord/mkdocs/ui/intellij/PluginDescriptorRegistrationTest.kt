package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginDescriptorRegistrationTest {
    @Test
    fun `plugin descriptor registers platform dependency tool window and start action`() {
        val root = findRepoRoot()
        val pluginXmlPath = root.resolve("modules/ui-plugin/src/main/resources/META-INF/plugin.xml")
        val content = Files.readString(pluginXmlPath)

        assertTrue(content.contains("<depends>com.intellij.modules.platform</depends>"))
        assertTrue(content.contains("factoryClass=\"com.authord.mkdocs.ui.intellij.MkdocsToolWindowFactory\""))
        assertTrue(content.contains("class=\"com.authord.mkdocs.ui.intellij.StartMkdocsAction\""))
    }

    private fun findRepoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Repository root not found")
    }
}
