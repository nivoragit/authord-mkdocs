package com.authord.mkdocs.runtime

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import kotlin.io.path.exists
import kotlin.io.path.isExecutable

/**
 * Result envelope for resolving a runnable `uv` executable path.
 */
data class UvExecutableResult(
    val success: Boolean,
    val executablePath: String = "",
    val errorMessage: String = "",
)

/**
 * Resolves an executable `uv` binary for runtime bootstrap and command execution.
 */
fun interface UvExecutableProvider {
    /**
     * Returns a runnable absolute `uv` executable path for the provided project root.
     */
    fun resolve(projectPath: String): UvExecutableResult
}

/**
 * Static `uv` executable provider used by unit tests and in-memory adapters.
 */
class StaticUvExecutableProvider(
    private val executable: String = "uv",
) : UvExecutableProvider {
    override fun resolve(projectPath: String): UvExecutableResult {
        return UvExecutableResult(success = true, executablePath = executable)
    }
}

data class UvArchiveDownloadResult(
    val success: Boolean,
    val errorMessage: String = "",
)

fun interface UvArchiveDownloader {
    fun download(url: String, destination: Path): UvArchiveDownloadResult
}

class HttpUvArchiveDownloader : UvArchiveDownloader {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    override fun download(url: String, destination: Path): UvArchiveDownloadResult {
        return runCatching {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() >= 400) {
                return UvArchiveDownloadResult(
                    success = false,
                    errorMessage = "Download failed with status ${response.statusCode()} from $url",
                )
            }

            Files.createDirectories(destination.parent)
            response.body().use { body ->
                Files.copy(body, destination, StandardCopyOption.REPLACE_EXISTING)
            }
            UvArchiveDownloadResult(success = true)
        }.getOrElse { exception ->
            UvArchiveDownloadResult(
                success = false,
                errorMessage = exception.message ?: "Download failed",
            )
        }
    }
}

fun interface UvArchiveExtractor {
    fun extract(archivePath: Path, executablePath: Path): UvExecutableResult
}

class DefaultUvArchiveExtractor : UvArchiveExtractor {
    override fun extract(archivePath: Path, executablePath: Path): UvExecutableResult {
        return when {
            archivePath.toString().endsWith(".zip") -> extractZip(archivePath, executablePath)
            archivePath.toString().endsWith(".tar.gz") -> extractTarGz(archivePath, executablePath)
            else -> UvExecutableResult(
                success = false,
                errorMessage = "Unsupported uv archive format: ${archivePath.fileName}",
            )
        }
    }

    private fun extractZip(archivePath: Path, executablePath: Path): UvExecutableResult {
        val executableName = executablePath.fileName.toString()
        val extractionRoot = executablePath.parent.toAbsolutePath().normalize()
        val found = runCatching {
            ZipInputStream(Files.newInputStream(archivePath)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.isDirectory && matchesExecutableEntry(entry.name, executableName)) {
                        ensureSafeArchiveEntry(entry.name, extractionRoot)
                        Files.copy(input, executablePath, StandardCopyOption.REPLACE_EXISTING)
                        return@use true
                    }
                }
                false
            }
        }.getOrElse { exception ->
            return UvExecutableResult(
                success = false,
                errorMessage = "Failed to extract uv archive: ${exception.message}",
            )
        }

        if (!found) {
            return UvExecutableResult(
                success = false,
                errorMessage = "Downloaded uv archive did not contain executable '$executableName'.",
            )
        }

        return UvExecutableResult(success = true, executablePath = executablePath.toString())
    }

    private fun extractTarGz(archivePath: Path, executablePath: Path): UvExecutableResult {
        val executableName = executablePath.fileName.toString()
        val extractionRoot = executablePath.parent.toAbsolutePath().normalize()
        val found = runCatching {
            Files.newInputStream(archivePath).use { fileInput ->
                GzipCompressorInputStream(fileInput).use { gzipInput ->
                    TarArchiveInputStream(gzipInput).use { tarInput ->
                        while (true) {
                            val entry = tarInput.nextTarEntry ?: break
                            if (!entry.isDirectory && matchesExecutableEntry(entry.name, executableName)) {
                                ensureSafeArchiveEntry(entry.name, extractionRoot)
                                Files.copy(tarInput, executablePath, StandardCopyOption.REPLACE_EXISTING)
                                return@use true
                            }
                        }
                        false
                    }
                }
            }
        }.getOrElse { exception ->
            return UvExecutableResult(
                success = false,
                errorMessage = "Failed to extract uv archive: ${exception.message}",
            )
        }

        if (!found) {
            return UvExecutableResult(
                success = false,
                errorMessage = "Downloaded uv archive did not contain executable '$executableName'.",
            )
        }

        return UvExecutableResult(success = true, executablePath = executablePath.toString())
    }

    private fun matchesExecutableEntry(entryName: String, executableName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        return normalized == executableName || normalized.endsWith("/$executableName")
    }

    private fun ensureSafeArchiveEntry(entryName: String, extractionRoot: Path) {
        val archivePath = runCatching { Path.of(entryName.replace('\\', '/')) }
            .getOrElse { throw SecurityException("Invalid archive entry path: $entryName") }
        if (archivePath.isAbsolute) {
            throw SecurityException("Archive entry must be relative: $entryName")
        }
        val resolved = extractionRoot.resolve(archivePath).normalize()
        if (!resolved.startsWith(extractionRoot)) {
            throw SecurityException("Archive entry escaped extraction root: $entryName")
        }
    }
}

/**
 * Project-managed `uv` provider.
 *
 * Behavior:
 * - Reuses an existing project-managed binary when available.
 * - Otherwise discovers `uv` from explicit env var, PATH, and common user install paths.
 * - Uses discovered `uv` directly when available.
 * - Downloads `uv` into project runtime tools directory only when discovery fails.
 */
class ProjectManagedUvExecutableProvider(
    private val env: Map<String, String> = System.getenv(),
    private val archiveDownloader: UvArchiveDownloader = HttpUvArchiveDownloader(),
    private val archiveExtractor: UvArchiveExtractor = DefaultUvArchiveExtractor(),
    private val includeSystemFallbackCandidates: Boolean = true,
) : UvExecutableProvider {
    override fun resolve(projectPath: String): UvExecutableResult {
        val managedUvPath = managedUvPath(projectPath)
        if (managedUvPath.exists()) {
            ensureExecutable(managedUvPath)
            return UvExecutableResult(success = true, executablePath = managedUvPath.toString())
        }

        val sourceUv = locateSourceUv()
        if (sourceUv != null) {
            return UvExecutableResult(success = true, executablePath = sourceUv.toString())
        }

        val downloadedUv = downloadUvToManagedPath(managedUvPath)
        if (downloadedUv.success) {
            return downloadedUv
        }

        return UvExecutableResult(
            success = false,
            errorMessage = buildString {
                append("Unable to provision project-managed 'uv'. ")
                append("Set AUTHORD_UV_PATH to an absolute uv binary path. ")
                append("Details: ${downloadedUv.errorMessage}")
            },
        )
    }

    private fun downloadUvToManagedPath(managedUvPath: Path): UvExecutableResult {
        val target = distributionTarget()
            ?: return UvExecutableResult(
                success = false,
                errorMessage = "Unsupported OS/architecture for uv download: ${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
            )

        val archiveExtension = if (isWindows()) ".zip" else ".tar.gz"
        val archiveFileName = "uv-$target$archiveExtension"
        val archivePath = managedUvPath.parent.resolve(archiveFileName)
        val downloadUrl = "https://github.com/astral-sh/uv/releases/latest/download/$archiveFileName"

        val downloadResult = archiveDownloader.download(downloadUrl, archivePath)
        if (!downloadResult.success) {
            return UvExecutableResult(
                success = false,
                errorMessage = downloadResult.errorMessage,
            )
        }

        val extractionResult = archiveExtractor.extract(archivePath, managedUvPath)
        runCatching { Files.deleteIfExists(archivePath) }
        if (!extractionResult.success) {
            return extractionResult
        }

        ensureExecutable(managedUvPath)
        return UvExecutableResult(success = true, executablePath = managedUvPath.toString())
    }

    private fun managedUvPath(projectPath: String): Path {
        val executableName = if (isWindows()) "uv.exe" else "uv"
        return Path.of(projectPath)
            .resolve(".mkdocs-plugin-runtime")
            .resolve("tools")
            .resolve("uv")
            .resolve(executableName)
    }

    private fun locateSourceUv(): Path? {
        val candidates = buildList {
            explicitConfiguredUvPath()?.let { add(it) }
            addAll(pathCandidates())
            if (includeSystemFallbackCandidates) {
                addAll(commonInstallCandidates())
            }
        }

        return candidates
            .asSequence()
            .mapNotNull { candidate -> candidate.normalizeOrNull() }
            .firstOrNull { candidate -> candidate.exists() && candidate.isExecutable() }
    }

    private fun explicitConfiguredUvPath(): Path? {
        val configured = env["AUTHORD_UV_PATH"]?.trim().orEmpty()
            .ifBlank { env["AUTHORD_MKDOCS_UV_PATH"]?.trim().orEmpty() }
        if (configured.isBlank()) {
            return null
        }
        return runCatching { Path.of(configured) }.getOrNull()
    }

    private fun pathCandidates(): List<Path> {
        val pathValue = env["PATH"].orEmpty()
        if (pathValue.isBlank()) {
            return emptyList()
        }

        val executableName = if (isWindows()) "uv.exe" else "uv"
        return pathValue
            .split(File.pathSeparator)
            .mapNotNull { rawEntry ->
                val entry = rawEntry.trim()
                if (entry.isBlank()) return@mapNotNull null
                runCatching { Path.of(entry).resolve(executableName) }.getOrNull()
            }
    }

    private fun commonInstallCandidates(): List<Path> {
        val home = userHomePath()
        val candidates = mutableListOf<Path>()
        val executableName = if (isWindows()) "uv.exe" else "uv"

        if (home != null) {
            candidates.add(Path.of(home, ".local", "bin", executableName))
            candidates.add(Path.of(home, ".cargo", "bin", executableName))
        }

        if (!isWindows()) {
            candidates.add(Path.of("/opt", "homebrew", "bin", executableName))
            candidates.add(Path.of("/usr", "local", "bin", executableName))
            candidates.add(Path.of("/usr", "bin", executableName))
        }

        return candidates
    }

    private fun userHomePath(): String? {
        val unixHome = env["HOME"]?.takeIf { it.isNotBlank() }
        if (unixHome != null) {
            return unixHome
        }

        val userProfile = env["USERPROFILE"]?.takeIf { it.isNotBlank() }
        if (userProfile != null) {
            return userProfile
        }

        val homeDrive = env["HOMEDRIVE"]?.takeIf { it.isNotBlank() }
        val homePath = env["HOMEPATH"]?.takeIf { it.isNotBlank() }
        if (homeDrive != null && homePath != null) {
            return homeDrive + homePath
        }
        return null
    }

    private fun Path.normalizeOrNull(): Path? = runCatching { toAbsolutePath().normalize() }.getOrNull()

    private fun distributionTarget(): String? {
        val os = System.getProperty("os.name").lowercase()
        val arch = normalizeArch(System.getProperty("os.arch").lowercase()) ?: return null

        return when {
            os.contains("mac") || os.contains("darwin") -> "$arch-apple-darwin"
            os.contains("linux") -> "$arch-unknown-linux-gnu"
            os.contains("windows") -> "$arch-pc-windows-msvc"
            else -> null
        }
    }

    private fun normalizeArch(rawArch: String): String? {
        return when (rawArch) {
            "x86_64", "amd64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> null
        }
    }

    private fun ensureExecutable(path: Path) {
        if (isWindows()) {
            return
        }

        runCatching {
            val permissions = Files.getPosixFilePermissions(path).toMutableSet()
            permissions += PosixFilePermission.OWNER_EXECUTE
            permissions += PosixFilePermission.GROUP_EXECUTE
            permissions += PosixFilePermission.OTHERS_EXECUTE
            Files.setPosixFilePermissions(path, permissions)
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").contains("win", ignoreCase = true)
}
