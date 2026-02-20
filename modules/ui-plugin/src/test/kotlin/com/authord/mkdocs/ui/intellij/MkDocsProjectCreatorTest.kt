package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
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
    fun `createProject resolves uv and runs mkdocs new in project root`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-project-creator-")
        try {
            val runner = RecordingCommandRunner { _, workingDir ->
                Files.writeString(
                    java.nio.file.Path.of(workingDir).resolve("mkdocs.yml"),
                    "site_name: 'My Docs'\n",
                )
                CommandResult(exitCode = 0)
            }
            val creator = MkDocsProjectCreator(
                commandRunner = runner,
                uvExecutableProvider = StaticUvProvider(UvExecutableResult(success = true, executablePath = "/tmp/uv")),
            )

            val result = creator.createProject(projectRoot.toString(), "demo-site")

            assertTrue(result.success)
            assertEquals(1, runner.commands.size)
            assertEquals(listOf("/tmp/uv", "run", "mkdocs", "new", "."), runner.commands.single())
            assertEquals(projectRoot.toString(), runner.workingDirs.single())
            val config = Files.readString(projectRoot.resolve("mkdocs.yml"))
            assertTrue(config.contains("site_name: 'demo-site'"))
            assertTrue(config.contains("docs_dir: docs"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `createProject falls back to sensible site name when request is blank`() {
        val projectRoot = createTempDirectory(prefix = "mkdocs-project-creator-fallback-name-")
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

            assertTrue(result.success)
            val config = Files.readString(projectRoot.resolve("mkdocs.yml"))
            val expectedFallbackSiteName = projectRoot.fileName.toString()
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
            assertTrue(config.contains("site_name: '${expectedFallbackSiteName}'"))
            assertTrue(config.contains("docs_dir: docs"))
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
            assertTrue(runner.commands.isEmpty())
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
