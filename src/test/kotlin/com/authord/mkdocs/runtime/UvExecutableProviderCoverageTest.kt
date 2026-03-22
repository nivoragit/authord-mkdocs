package com.authord.mkdocs.runtime

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UvExecutableProviderCoverageTest {
    @Test
    fun `static provider returns configured executable`() {
        val provider = StaticUvExecutableProvider("/opt/tools/uv")
        val resolved = provider.resolve("/tmp/project")

        assertTrue(resolved.success)
        assertEquals("/opt/tools/uv", resolved.executablePath)
    }

    @Test
    fun `http archive downloader covers success http error and connection failure`() {
        val root = createTempDirectory(prefix = "uv-http-downloader-")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { exchange ->
            val body = "uv-binary".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/fail") { exchange ->
            val body = "nope".toByteArray()
            exchange.sendResponseHeaders(500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val downloader = HttpUvArchiveDownloader()
            val okDestination = root.resolve("nested/uv.zip")
            val ok = downloader.download(
                "http://127.0.0.1:${server.address.port}/ok",
                okDestination,
            )
            assertTrue(ok.success)
            assertTrue(okDestination.exists())
            assertEquals("uv-binary", okDestination.readText())

            val failedByStatus = downloader.download(
                "http://127.0.0.1:${server.address.port}/fail",
                root.resolve("failed-status.zip"),
            )
            assertFalse(failedByStatus.success)
            assertTrue(failedByStatus.errorMessage.contains("status 500"))

            val closedPort = ServerSocket(0).use { it.localPort }
            val failedByIo = downloader.download(
                "http://127.0.0.1:$closedPort/unreachable",
                root.resolve("failed-io.zip"),
            )
            assertFalse(failedByIo.success)
            assertTrue(failedByIo.errorMessage.isNotBlank())
        } finally {
            server.stop(0)
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `default archive extractor covers zip and tar branches plus failures`() {
        val root = createTempDirectory(prefix = "uv-extractor-")
        try {
            val extractor = DefaultUvArchiveExtractor()
            val executableName = "uv"
            val output = root.resolve(executableName)

            val unsupportedArchive = root.resolve("uv.bin")
            unsupportedArchive.writeText("bin")
            val unsupported = extractor.extract(unsupportedArchive, output)
            assertFalse(unsupported.success)
            assertTrue(unsupported.errorMessage.contains("Unsupported uv archive format"))

            val zipWithExecutable = root.resolve("uv-ok.zip")
            createZip(zipWithExecutable, mapOf("nested/$executableName" to "zip-uv"))
            val zipOk = extractor.extract(zipWithExecutable, output)
            assertTrue(zipOk.success)
            assertEquals(output.toString(), zipOk.executablePath)
            assertEquals("zip-uv", output.readText())

            val zipTraversalEntry = root.resolve("uv-zip-slip.zip")
            createZip(zipTraversalEntry, mapOf("../../$executableName" to "zip-slip"))
            val zipTraversal = extractor.extract(zipTraversalEntry, output)
            assertFalse(zipTraversal.success)
            assertTrue(zipTraversal.errorMessage.contains("Failed to extract uv archive"))
            assertEquals("zip-uv", output.readText())

            val zipWithoutExecutable = root.resolve("uv-missing.zip")
            createZip(zipWithoutExecutable, mapOf("nested/not-uv" to "other"))
            val zipMissing = extractor.extract(zipWithoutExecutable, output)
            assertFalse(zipMissing.success)
            assertTrue(zipMissing.errorMessage.contains("did not contain executable"))

            val zipCorrupt = root.resolve("uv-corrupt.zip")
            zipCorrupt.writeText("not-a-zip")
            val zipCorruptResult = extractor.extract(zipCorrupt, output)
            assertFalse(zipCorruptResult.success)
            assertTrue(
                zipCorruptResult.errorMessage.contains("Failed to extract uv archive") ||
                    zipCorruptResult.errorMessage.contains("did not contain executable"),
            )

            val zipMissingFile = extractor.extract(root.resolve("not-found.zip"), output)
            assertFalse(zipMissingFile.success)
            assertTrue(zipMissingFile.errorMessage.contains("Failed to extract uv archive"))

            val tarWithExecutable = root.resolve("uv-ok.tar.gz")
            createTarGz(tarWithExecutable, mapOf(executableName to "tar-uv"))
            val tarOk = extractor.extract(tarWithExecutable, output)
            assertTrue(tarOk.success, tarOk.errorMessage)
            assertEquals("tar-uv", output.readText())

            val tarWithoutExecutable = root.resolve("uv-missing.tar.gz")
            createTarGz(tarWithoutExecutable, mapOf("not-uv" to "other"))
            val tarMissing = extractor.extract(tarWithoutExecutable, output)
            assertFalse(tarMissing.success)
            assertTrue(tarMissing.errorMessage.contains("did not contain executable"))

            val tarCorrupt = root.resolve("uv-corrupt.tar.gz")
            tarCorrupt.writeText("not-a-tar")
            val tarCorruptResult = extractor.extract(tarCorrupt, output)
            assertFalse(tarCorruptResult.success)
            assertTrue(
                tarCorruptResult.errorMessage.contains("Failed to extract uv archive") ||
                    tarCorruptResult.errorMessage.contains("did not contain executable"),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `provider covers path candidate and common fallback discovery`() {
        val root = createTempDirectory(prefix = "uv-provider-path-")
        val pathDir = createTempDirectory(prefix = "uv-provider-bin-")
        try {
            withSystemProperties("os.name" to "Linux", "os.arch" to "amd64") {
                val uv = pathDir.resolve("uv")
                uv.writeText("uv-path")
                makeExecutable(uv)

                val provider = ProjectManagedUvExecutableProvider(
                    env = mapOf(
                        "AUTHORD_MKDOCS_UV_PATH" to "\u0000invalid",
                        "PATH" to "${pathDir}${File.pathSeparator} ${File.pathSeparator}",
                        "HOME" to root.resolve("home").toString(),
                    ),
                    includeSystemFallbackCandidates = true,
                )

                val resolved = provider.resolve(root.toString())
                assertTrue(resolved.success)
                assertEquals(uv.toAbsolutePath().normalize().toString(), resolved.executablePath)
            }
        } finally {
            root.toFile().deleteRecursively()
            pathDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `provider covers managed windows executable and download failure branches`() {
        val root = createTempDirectory(prefix = "uv-provider-win-")
        try {
            withSystemProperties("os.name" to "Windows 11", "os.arch" to "amd64") {
                val managed = root
                    .resolve(".mkdocs-plugin-runtime")
                    .resolve("tools")
                    .resolve("uv")
                    .resolve("uv.exe")
                managed.parent.createDirectories()
                managed.writeText("managed-uv")

                val managedProvider = ProjectManagedUvExecutableProvider(
                    env = mapOf("PATH" to "", "HOME" to root.resolve("home").toString()),
                    includeSystemFallbackCandidates = false,
                )
                val managedResolved = managedProvider.resolve(root.toString())
                assertTrue(managedResolved.success)
                assertEquals(managed.toString(), managedResolved.executablePath)

                managed.toFile().delete()

                val extractionFailureProvider = ProjectManagedUvExecutableProvider(
                    env = mapOf("PATH" to "", "HOME" to root.resolve("home").toString()),
                    archiveDownloader = UvArchiveDownloader { _, destination ->
                        destination.parent.createDirectories()
                        destination.writeText("archive")
                        UvArchiveDownloadResult(success = true)
                    },
                    archiveExtractor = UvArchiveExtractor { _, _ ->
                        UvExecutableResult(success = false, errorMessage = "extract failed")
                    },
                    includeSystemFallbackCandidates = false,
                )
                val extractionFailure = extractionFailureProvider.resolve(root.toString())
                assertFalse(extractionFailure.success)
                assertTrue(extractionFailure.errorMessage.contains("extract failed"))
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `provider resolves windows common candidate from USERPROFILE`() {
        val root = createTempDirectory(prefix = "uv-provider-userprofile-")
        try {
            withSystemProperties("os.name" to "Windows 11", "os.arch" to "amd64") {
                val userProfile = root.resolve("user-profile")
                val uv = userProfile.resolve(".cargo").resolve("bin").resolve("uv.exe")
                uv.parent.createDirectories()
                uv.writeText("userprofile-uv")
                runCatching {
                    val permissions = Files.getPosixFilePermissions(uv).toMutableSet()
                    permissions += PosixFilePermission.OWNER_EXECUTE
                    permissions += PosixFilePermission.GROUP_EXECUTE
                    permissions += PosixFilePermission.OTHERS_EXECUTE
                    Files.setPosixFilePermissions(uv, permissions)
                }

                val provider = ProjectManagedUvExecutableProvider(
                    env = mapOf(
                        "PATH" to "",
                        "USERPROFILE" to userProfile.toString(),
                    ),
                    includeSystemFallbackCandidates = true,
                )
                val resolved = provider.resolve(root.toString())
                assertTrue(resolved.success)
                assertEquals(uv.toAbsolutePath().normalize().toString(), resolved.executablePath)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `provider covers unsupported os and architecture targets`() {
        val root = createTempDirectory(prefix = "uv-provider-unsupported-")
        try {
            withSystemProperties("os.name" to "Plan9", "os.arch" to "amd64") {
                val unsupportedOs = ProjectManagedUvExecutableProvider(
                    env = mapOf("PATH" to "", "HOME" to root.resolve("home").toString()),
                    includeSystemFallbackCandidates = false,
                ).resolve(root.toString())

                assertFalse(unsupportedOs.success)
                assertTrue(unsupportedOs.errorMessage.contains("Unsupported OS/architecture"))
            }

            withSystemProperties("os.name" to "Linux", "os.arch" to "mips64") {
                val unsupportedArch = ProjectManagedUvExecutableProvider(
                    env = mapOf("PATH" to "", "HOME" to root.resolve("home").toString()),
                    includeSystemFallbackCandidates = false,
                ).resolve(root.toString())

                assertFalse(unsupportedArch.success)
                assertTrue(unsupportedArch.errorMessage.contains("Unsupported OS/architecture"))
            }

            withSystemProperties("os.name" to "Linux", "os.arch" to "arm64") {
                val linuxArm = ProjectManagedUvExecutableProvider(
                    env = mapOf("PATH" to "", "HOME" to root.resolve("home").toString()),
                    archiveDownloader = UvArchiveDownloader { _, _ ->
                        UvArchiveDownloadResult(success = false, errorMessage = "download disabled")
                    },
                    includeSystemFallbackCandidates = false,
                ).resolve(root.toString())

                assertFalse(linuxArm.success)
                assertTrue(linuxArm.errorMessage.contains("download disabled"))
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun createZip(path: Path, entries: Map<String, String>) {
        Files.newOutputStream(path).use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }
    }

    private fun createTarGz(path: Path, entries: Map<String, String>) {
        val staging = createTempDirectory(prefix = "uv-tar-staging-")
        try {
            entries.forEach { (name, content) ->
                val target = staging.resolve(name)
                target.parent?.let { Files.createDirectories(it) }
                Files.writeString(target, content)
            }

            val process = ProcessBuilder(
                "tar",
                "-czf",
                path.toString(),
                "-C",
                staging.toString(),
                ".",
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            assertEquals(0, exitCode, output)
        } finally {
            staging.toFile().deleteRecursively()
        }
    }

    private fun makeExecutable(path: Path) {
        if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
            return
        }
        val permissions = Files.getPosixFilePermissions(path).toMutableSet()
        permissions += PosixFilePermission.OWNER_EXECUTE
        permissions += PosixFilePermission.GROUP_EXECUTE
        permissions += PosixFilePermission.OTHERS_EXECUTE
        Files.setPosixFilePermissions(path, permissions)
    }

    private fun <T> withSystemProperties(
        vararg entries: Pair<String, String>,
        block: () -> T,
    ): T {
        val keys = entries.map { it.first }.distinct()
        val previous = keys.associateWith { System.getProperty(it) }
        try {
            entries.forEach { (key, value) -> System.setProperty(key, value) }
            return block()
        } finally {
            keys.forEach { key ->
                val old = previous[key]
                if (old == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, old)
                }
            }
        }
    }
}
