package com.authord.mkdocs.runtime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectManagedUvExecutableProviderTest {
    @Test
    fun `reuses existing project managed uv executable`() {
        val projectRoot = createTempDirectory(prefix = "project-managed-uv-existing-")
        try {
            val managedUvPath = managedUvPath(projectRoot)
            managedUvPath.parent.createDirectories()
            managedUvPath.writeText("#!/bin/sh\necho uv\n")
            ensureExecutable(managedUvPath)

            val provider = ProjectManagedUvExecutableProvider(
                env = emptyMap(),
                includeSystemFallbackCandidates = false,
            )

            val resolved = provider.resolve(projectRoot.toString())

            assertTrue(resolved.success)
            assertEquals(managedUvPath.toString(), resolved.executablePath)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uses explicit configured uv path directly without copying`() {
        val workspaceRoot = createTempDirectory(prefix = "project-managed-uv-configured-")
        val uvSourceRoot = createTempDirectory(prefix = "project-managed-uv-source-")
        try {
            val sourceUv = uvSourceRoot.resolve(executableName())
            sourceUv.writeText("uv-binary")
            ensureExecutable(sourceUv)

            val provider = ProjectManagedUvExecutableProvider(
                env = mapOf(
                    "AUTHORD_MKDOCS_UV_PATH" to sourceUv.toString(),
                    "PATH" to "",
                ),
                includeSystemFallbackCandidates = false,
            )

            val resolved = provider.resolve(workspaceRoot.toString())

            assertTrue(resolved.success)
            assertEquals(sourceUv.toString(), resolved.executablePath)
            assertFalse(managedUvPath(workspaceRoot).exists())
        } finally {
            workspaceRoot.toFile().deleteRecursively()
            uvSourceRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `downloads uv into project managed tools path when discovery fails`() {
        val projectRoot = createTempDirectory(prefix = "project-managed-uv-download-")
        try {
            var capturedDownloadUrl = ""
            val provider = ProjectManagedUvExecutableProvider(
                env = mapOf(
                    "PATH" to "",
                    "HOME" to projectRoot.resolve("missing-home").toString(),
                ),
                archiveDownloader = UvArchiveDownloader { url, destination ->
                    capturedDownloadUrl = url
                    destination.parent.createDirectories()
                    destination.writeText("stub-archive")
                    UvArchiveDownloadResult(success = true)
                },
                archiveExtractor = UvArchiveExtractor { _, executablePath ->
                    executablePath.parent.createDirectories()
                    executablePath.writeText("downloaded-uv")
                    ensureExecutable(executablePath)
                    UvExecutableResult(success = true, executablePath = executablePath.toString())
                },
                includeSystemFallbackCandidates = false,
            )

            val resolved = provider.resolve(projectRoot.toString())
            val managed = managedUvPath(projectRoot)

            assertTrue(resolved.success)
            assertEquals(managed.toString(), resolved.executablePath)
            assertTrue(managed.exists())
            assertEquals("downloaded-uv", managed.readText())
            assertTrue(capturedDownloadUrl.contains("github.com/astral-sh/uv/releases/latest/download/"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `returns actionable failure when uv cannot be discovered or downloaded`() {
        val projectRoot = createTempDirectory(prefix = "project-managed-uv-missing-")
        try {
            val provider = ProjectManagedUvExecutableProvider(
                env = mapOf(
                    "PATH" to "",
                    "HOME" to projectRoot.resolve("missing-home").toString(),
                ),
                archiveDownloader = UvArchiveDownloader { _, _ ->
                    UvArchiveDownloadResult(success = false, errorMessage = "network unavailable")
                },
                includeSystemFallbackCandidates = false,
            )

            val resolved = provider.resolve(projectRoot.toString())

            assertFalse(resolved.success)
            assertTrue(resolved.errorMessage.contains("AUTHORD_MKDOCS_UV_PATH"))
            assertTrue(resolved.errorMessage.contains("network unavailable"))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    private fun managedUvPath(projectRoot: Path): Path {
        return projectRoot
            .resolve(".mkdocs-plugin-runtime")
            .resolve("tools")
            .resolve("uv")
            .resolve(executableName())
    }

    private fun executableName(): String {
        return if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            "uv.exe"
        } else {
            "uv"
        }
    }

    private fun ensureExecutable(path: Path) {
        if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            return
        }

        val permissions = Files.getPosixFilePermissions(path).toMutableSet()
        permissions += PosixFilePermission.OWNER_EXECUTE
        Files.setPosixFilePermissions(path, permissions)
    }
}
