package com.authord.mkdocs.runtime

import java.io.File

data class CommandResult(
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
)

fun interface CommandRunner {
    fun run(command: List<String>, workingDir: String): CommandResult
}

data class BootstrapResult(
    val success: Boolean,
    val runtimePath: String,
    val executedCommands: List<List<String>>,
    val skipped: Boolean,
    val errorMessage: String = "",
)

class UvBootstrapService(
    private val commandRunner: CommandRunner,
) {
    private val bootstrappedProjects = mutableSetOf<String>()

    fun bootstrap(projectPath: String): BootstrapResult {
        val runtimePath = File(projectPath, ".mkdocs-plugin-venv").path

        if (bootstrappedProjects.contains(projectPath)) {
            return BootstrapResult(
                success = true,
                runtimePath = runtimePath,
                executedCommands = emptyList(),
                skipped = true,
            )
        }

        val setupCommand = listOf("uv", "venv", runtimePath)
        val installCommand = listOf("uv", "pip", "install", "mkdocs")
        val executed = mutableListOf<List<String>>()

        val setupResult = commandRunner.run(setupCommand, projectPath)
        executed += setupCommand
        if (setupResult.exitCode != 0) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                executedCommands = executed,
                skipped = false,
                errorMessage = setupResult.stderr.ifBlank { "Failed to create runtime" },
            )
        }

        val installResult = commandRunner.run(installCommand, projectPath)
        executed += installCommand
        if (installResult.exitCode != 0) {
            return BootstrapResult(
                success = false,
                runtimePath = runtimePath,
                executedCommands = executed,
                skipped = false,
                errorMessage = installResult.stderr.ifBlank { "Failed to install mkdocs" },
            )
        }

        bootstrappedProjects += projectPath
        return BootstrapResult(
            success = true,
            runtimePath = runtimePath,
            executedCommands = executed,
            skipped = false,
        )
    }
}
