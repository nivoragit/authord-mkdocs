package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.ProjectManagedUvExecutableProvider
import com.authord.mkdocs.runtime.UvExecutableProvider
import java.nio.file.Files
import java.nio.file.Path

data class MkDocsProjectCreationResult(
    val success: Boolean,
    val message: String = "",
)

/**
 * Creates a new MkDocs starter project in the selected IntelliJ project root.
 */
class MkDocsProjectCreator(
    private val commandRunner: CommandRunner = ProcessBuilderCommandRunner(),
    private val uvExecutableProvider: UvExecutableProvider = ProjectManagedUvExecutableProvider(),
) {
    fun createProject(projectRootPath: String, @Suppress("UNUSED_PARAMETER") requestedProjectName: String): MkDocsProjectCreationResult {
        val projectRoot = runCatching { Path.of(projectRootPath).toAbsolutePath().normalize() }
            .getOrElse {
                return MkDocsProjectCreationResult(
                    success = false,
                    message = "Invalid project path: $projectRootPath",
                )
            }
        if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
            return MkDocsProjectCreationResult(
                success = false,
                message = "Project root does not exist: $projectRoot",
            )
        }

        val uvResolution = uvExecutableProvider.resolve(projectRoot.toString())
        if (!uvResolution.success) {
            return MkDocsProjectCreationResult(
                success = false,
                message = uvResolution.errorMessage.ifBlank { "Unable to resolve uv executable." },
            )
        }

        val command = listOf(uvResolution.executablePath, "run", "mkdocs", "new", ".")
        val commandResult = commandRunner.run(command, projectRoot.toString())
        if (commandResult.exitCode != 0) {
            val details = commandResult.stderr.ifBlank { commandResult.stdout }
            return MkDocsProjectCreationResult(
                success = false,
                message = details.ifBlank { "Failed to create MkDocs project." },
            )
        }

        val configPath = resolveMkdocsConfigPath(projectRoot)
            ?: return MkDocsProjectCreationResult(
                success = false,
                message = "MkDocs project was created but mkdocs.yml was not found.",
            )
        writeBaseConfig(configPath)

        return MkDocsProjectCreationResult(
            success = true,
            message = "MkDocs project created.",
        )
    }

    private fun resolveMkdocsConfigPath(projectRoot: Path): Path? {
        val yml = projectRoot.resolve("mkdocs.yml")
        if (Files.exists(yml)) {
            return yml
        }
        val yaml = projectRoot.resolve("mkdocs.yaml")
        if (Files.exists(yaml)) {
            return yaml
        }
        return null
    }

    private fun writeBaseConfig(configPath: Path) {
        val configContent = buildString {
            append("site_name: My Docs\n")
            append("docs_dir: docs\n")
        }
        runCatching { Files.writeString(configPath, configContent) }
    }
}
