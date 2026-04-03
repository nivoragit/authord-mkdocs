package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.MkDocsYamlGateway
import com.authord.mkdocs.runtime.UvExecutableProvider
import com.authord.mkdocs.runtime.UvExecutableResult
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class RecordingCommandRunner(
    private val onRun: (command: List<String>, workingDir: String) -> CommandResult,
) : CommandRunner {
    val commands = mutableListOf<List<String>>()
    val workingDirs = mutableListOf<String>()

    override fun run(command: List<String>, workingDir: String): CommandResult {
        commands += command
        workingDirs += workingDir
        return onRun(command, workingDir)
    }
}

private class StaticUvProvider(
    private val result: UvExecutableResult,
) : UvExecutableProvider {
    override fun resolve(projectPath: String): UvExecutableResult = result
}

class MkDocsProjectCreatorTest {
    @Test
    fun `createProject resolves uv writes authord scaffold and nav in project root`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-project-creator-")
        try {
            val runner = RecordingCommandRunner { _, _ -> CommandResult(exitCode = 0) }
            val creator = MkDocsProjectCreator(
                commandRunner = runner,
                uvExecutableProvider = StaticUvProvider(UvExecutableResult(success = true, executablePath = "/tmp/uv")),
            )

            val result = creator.createProject(projectRoot.toString(), "demo-site")

            assertTrue(result.success)
            val runtimePath = projectRoot.resolve(".mkdocs-plugin-venv").toString()
            assertEquals(
                listOf(
                    listOf("/tmp/uv", "venv", runtimePath),
                    listOf("/tmp/uv", "pip", "install", "--python", runtimePath, "mkdocs<2", "mkdocs-material==9.*"),
                ),
                runner.commands,
            )
            assertEquals(listOf(projectRoot.toString(), projectRoot.toString()), runner.workingDirs)
            val config = Files.readString(projectRoot.resolve("mkdocs.yml"))
            assertTrue(config.contains("site_name: 'demo-site'"))
            assertTrue(config.contains("docs_dir: docs"))
            assertTrue(config.contains("nav:"))
            assertTrue(config.contains("Welcome to Authord"))
            assertTrue(config.contains("  - 'Welcome to Authord':"))
            assertTrue(config.contains("      - 'Overview': index.md"))

            val configGateway = MkDocsYamlGateway()
            val loaded = configGateway.loadConfig(
                TopicInstanceRef(
                    instanceId = "default",
                    configPath = projectRoot.resolve("mkdocs.yml").toString(),
                    docsDirPath = projectRoot.resolve("docs").toString(),
                ),
            )
            val document = when (loaded) {
                is TopicGatewayResult.Success -> loaded.value
                is TopicGatewayResult.Failure -> error("Expected config to parse, but failed: ${loaded.error.detail}")
            }
            assertEquals("demo-site", document.siteName)
            assertEquals(1, document.nav.size)
            assertEquals("Welcome to Authord", document.nav[0].title)
            assertEquals(1, document.nav[0].children.size)
            assertEquals("Overview", document.nav[0].children[0].title)
            assertEquals("index.md", document.nav[0].children[0].path)

            val index = Files.readString(projectRoot.resolve("docs").resolve("index.md"))
            assertTrue(index.contains("Welcome to Authord"))
            assertFalse(index.contains("Welcome to MkDocs"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `createProject fails when project name is blank`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-project-creator-blank-name-")
        try {
            val runner = RecordingCommandRunner { _, workingDir ->
                Files.writeString(
                    java.nio.file.Path.of(workingDir).resolve("mkdocs.yml"),
                    "site_name: placeholder\n",
                )
                CommandResult(exitCode = 0)
            }
            val creator = MkDocsProjectCreator(
                commandRunner = runner,
                uvExecutableProvider = StaticUvProvider(UvExecutableResult(success = true, executablePath = "/tmp/uv")),
            )

            val result = creator.createProject(projectRoot.toString(), "   ")

            assertFalse(result.success)
            assertTrue(result.message.contains("Project name is required"))
            assertTrue(runner.commands.isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `createProject fails when uv resolution fails`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-project-creator-uv-fail-")
        try {
            val runner = RecordingCommandRunner { _, _ -> CommandResult(exitCode = 0) }
            val creator = MkDocsProjectCreator(
                commandRunner = runner,
                uvExecutableProvider = StaticUvProvider(
                    UvExecutableResult(success = false, errorMessage = "uv not found"),
                ),
            )

            val result = creator.createProject(projectRoot.toString(), "demo-site")

            assertFalse(result.success)
            assertTrue(result.message.contains("uv not found"))
            assertTrue(result.message.contains("Install uv"))
            assertTrue(runner.commands.isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
