package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.runtime.CommandResult
import com.authord.mkdocs.runtime.CommandRunner
import com.authord.mkdocs.runtime.MkDocsYamlGateway
import com.authord.mkdocs.runtime.ProjectManagedUvExecutableProvider
import com.authord.mkdocs.runtime.UvExecutableProvider
import java.nio.file.Files
import java.nio.file.Path

data class MkDocsProjectCreationResult(
    val success: Boolean,
    val message: String = "",
)

/**
 * Creates a new documentation starter project in the selected IntelliJ project root.
 */
class MkDocsProjectCreator(
    private val commandRunner: CommandRunner = ProcessBuilderCommandRunner(),
    private val uvExecutableProvider: UvExecutableProvider = ProjectManagedUvExecutableProvider(),
    private val configGateway: MkDocsYamlGateway = MkDocsYamlGateway(),
) {
    private val runtimeVenvDirName: String = ".authord_venv"
    private val uvInstallUrl: String = "https://docs.astral.sh/uv/getting-started/installation/"
    private val defaultDocsDirName: String = "docs"
    private val defaultIndexRelativePath: String = "index.md"
    private val defaultWelcomeTitle: String = "Welcome to Authord"
    private val defaultOverviewTitle: String = "Overview"

    fun createProject(projectRootPath: String, requestedProjectName: String): MkDocsProjectCreationResult {
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
        val projectName = requestedProjectName.trim()
        if (projectName.isBlank()) {
            return MkDocsProjectCreationResult(
                success = false,
                message = AuthordUiBundle.message("setup.error.projectNameRequired"),
            )
        }

        val uvResolution = uvExecutableProvider.resolve(projectRoot.toString())
        if (!uvResolution.success) {
            return MkDocsProjectCreationResult(
                success = false,
                message = uvMissingMessage(uvResolution.errorMessage),
            )
        }
        val uvExecutable = uvResolution.executablePath
        val runtimePath = projectRoot.resolve(runtimeVenvDirName).toString()

        val venvCommand = listOf(uvExecutable, "venv", runtimePath)
        val venvResult = commandRunner.run(venvCommand, projectRoot.toString())
        if (venvResult.exitCode != 0 && !isExistingRuntimeError(venvResult)) {
            val details = venvResult.stderr.ifBlank { venvResult.stdout }
            return MkDocsProjectCreationResult(
                success = false,
                message = if (looksLikeUvMissing(details)) {
                    uvMissingMessage(details)
                } else {
                    details.ifBlank { "Failed to create project runtime." }
                },
            )
        }

        val installMkdocsCommand = listOf(
            uvExecutable,
            "pip",
            "install",
            "--python",
            runtimePath,
            "mkdocs<2",
            "mkdocs-material==9.*",
        )
        val installMkdocsResult = commandRunner.run(installMkdocsCommand, projectRoot.toString())
        if (installMkdocsResult.exitCode != 0) {
            val details = installMkdocsResult.stderr.ifBlank { installMkdocsResult.stdout }
            return MkDocsProjectCreationResult(
                success = false,
                message = if (looksLikeUvMissing(details)) {
                    uvMissingMessage(details)
                } else {
                    details.ifBlank { "Failed to install mkdocs runtime packages into project runtime." }
                },
            )
        }

        val welcomeWriteResult = writeWelcomeIndex(projectRoot)
        if (!welcomeWriteResult) {
            return MkDocsProjectCreationResult(
                success = false,
                message = "Project was created but docs index could not be updated.",
            )
        }
        val configPath = resolveConfigPath(projectRoot) ?: projectRoot.resolve("mkdocs.yml")
        val configWriteResult = writeBaseConfig(configPath, projectName)
        if (!configWriteResult) {
            return MkDocsProjectCreationResult(
                success = false,
                message = "Project was created but configuration file could not be updated.",
            )
        }

        return MkDocsProjectCreationResult(
            success = true,
            message = "Project created.",
        )
    }

    private fun resolveConfigPath(projectRoot: Path): Path? {
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

    private fun writeBaseConfig(configPath: Path, siteName: String): Boolean {
        val configContent = buildString {
            append("site_name: '${escapeSingleQuotedYaml(siteName)}'\n")
            append("docs_dir: $defaultDocsDirName\n")
            append("nav:\n")
            append("  - '$defaultWelcomeTitle':\n")
            append("      - '$defaultOverviewTitle': $defaultIndexRelativePath\n")
        }
        return runCatching {
            configPath.parent?.let(Files::createDirectories)
            Files.writeString(configPath, configContent)
            hasExpectedBootstrapNavHierarchy(configPath)
        }.getOrDefault(false)
    }

    private fun hasExpectedBootstrapNavHierarchy(configPath: Path): Boolean {
        val configResult = configGateway.loadConfig(
            TopicInstanceRef(
                instanceId = "default",
                configPath = configPath.toAbsolutePath().normalize().toString(),
                docsDirPath = "",
            ),
        )
        val loadedConfig = when (configResult) {
            is TopicGatewayResult.Success -> configResult.value
            is TopicGatewayResult.Failure -> return false
        }
        val navRoot = loadedConfig.nav.singleOrNull() ?: return false
        val navLeaf = navRoot.children.singleOrNull() ?: return false
        return navRoot.title == defaultWelcomeTitle &&
            navLeaf.title == defaultOverviewTitle &&
            navLeaf.path == defaultIndexRelativePath
    }

    private fun writeWelcomeIndex(projectRoot: Path): Boolean {
        val docsDirectory = projectRoot.resolve(defaultDocsDirName)
        val indexPath = docsDirectory.resolve(defaultIndexRelativePath)
        val content = buildString {
            append("# ")
            append(defaultWelcomeTitle)
            append("\n\n")
            append("Start writing your documentation here.\n")
        }
        return runCatching {
            Files.createDirectories(docsDirectory)
            Files.writeString(indexPath, content)
        }.isSuccess
    }

    private fun escapeSingleQuotedYaml(value: String): String {
        return value.replace("'", "''")
    }

    private fun isExistingRuntimeError(result: CommandResult): Boolean {
        val normalized = buildString {
            append(result.stderr)
            append('\n')
            append(result.stdout)
        }.lowercase()
        return "virtual environment already exists" in normalized || "already exists at" in normalized
    }

    private fun looksLikeUvMissing(details: String): Boolean {
        val normalized = details.lowercase()
        return (("uv" in normalized) && ("not found" in normalized || "no such file or directory" in normalized)) ||
            "failed to spawn: `uv`" in normalized
    }

    private fun uvMissingMessage(details: String): String {
        val resolvedDetails = details.trim().ifBlank { "Unable to resolve uv executable." }
        return buildString {
            append("uv is required to create an Authord project. Install uv and retry: ")
            append(uvInstallUrl)
            append(". Details: ")
            append(resolvedDetails)
        }
    }
}
