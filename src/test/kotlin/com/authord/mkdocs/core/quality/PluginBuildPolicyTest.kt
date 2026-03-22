package com.authord.mkdocs.core.quality

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginBuildPolicyTest {
    @Test
    fun `build configuration enables intellij platform plugin and runIde readiness`() {
        val root = findRepoRoot()
        val rootBuild = Files.readString(root.resolve("build.gradle.kts"))
        val gradleProperties = Files.readString(root.resolve("gradle.properties"))
        val quickstart = Files.readString(root.resolve("specs/002-intellij-mkdocs-mvp/quickstart.md"))

        assertTrue(rootBuild.contains("id(\"org.jetbrains.intellij\")"), "Root build must declare IntelliJ plugin dependency")
        assertTrue(rootBuild.contains("intellij {"), "Root build must configure IntelliJ plugin target")
        assertTrue(rootBuild.contains("patchPluginXml"), "Root build must configure plugin.xml compatibility range")
        assertTrue(gradleProperties.contains("platformVersion="), "Gradle properties must define IntelliJ platform version")
        assertTrue(quickstart.contains("runIde"), "Quickstart must document runIde workflow")
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
