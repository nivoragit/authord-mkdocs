package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicDeleteMode
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class RuntimeAdapterCoverageEdgeCasesTest {
    @Test
    fun `mkdocs yaml gateway covers parse and write failure branches`() {
        val root = Files.createTempDirectory("mkdocs-gateway-failure")
        try {
            val gateway = MkDocsYamlGateway()
            val missing = TopicInstanceRef(
                instanceId = "default",
                configPath = root.resolve("missing.yml").toString(),
                docsDirPath = root.resolve("docs").toString(),
            )
            val missingResult = gateway.loadConfig(missing)
            assertFailure(missingResult, "CONFIG_PARSE")

            val malformedPath = root.resolve("mkdocs.yml")
            Files.writeString(malformedPath, "nav: [unterminated\n")
            val malformed = TopicInstanceRef(
                instanceId = "default",
                configPath = malformedPath.toString(),
                docsDirPath = root.resolve("docs").toString(),
            )
            val malformedResult = gateway.loadConfig(malformed)
            assertFailure(malformedResult, "CONFIG_PARSE")

            Files.writeString(
                malformedPath,
                """
                docs_dir: ""
                nav:
                  - Intro: 42
                  - getting-started.md
                  - ''
                  - 123
                """.trimIndent() + "\n",
            )
            val parsed = requireSuccess(gateway.loadConfig(malformed))
            assertTrue(parsed.docsDir.endsWith("/docs") || parsed.docsDir.endsWith("\\docs"))
            assertEquals(3, parsed.nav.size)
            assertEquals("Intro", parsed.nav[0].title)
            assertTrue(parsed.nav[0].children.isEmpty())
            assertEquals("getting started", parsed.nav[1].title)
            assertEquals("getting-started.md", parsed.nav[1].path)
            assertEquals("Untitled", parsed.nav[2].title)

            val directoryAsConfig = TopicInstanceRef(
                instanceId = "default",
                configPath = root.toString(),
                docsDirPath = root.resolve("docs").toString(),
            )
            val writeFailure = gateway.writeConfig(
                directoryAsConfig,
                MkDocsConfigDocument(
                    docsDir = "docs",
                    nav = listOf(TopicNavNode(nodeId = "n1", title = "Home", path = "index.md")),
                ),
            )
            assertFailure(writeFailure, "CONFIG_WRITE")

            val cycleChildren = mutableListOf<TopicNavNode>()
            val cycleNode = TopicNavNode(nodeId = "loop", title = "Loop", children = cycleChildren)
            cycleChildren += cycleNode
            val cyclicDocument = MkDocsConfigDocument(
                docsDir = "docs",
                nav = listOf(cycleNode),
            )
            val serializeFailure = gateway.serializeDeterministically(cyclicDocument)
            assertFailure(serializeFailure, "CONFIG_WRITE")

            val propagateFailure = gateway.writeConfig(malformed, cyclicDocument)
            assertFailure(propagateFailure, "CONFIG_WRITE")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `docs file gateway adapter covers validation and io failure branches`() {
        val root = Files.createTempDirectory("docs-gateway-failure")
        val docs = Files.createDirectories(root.resolve("docs"))
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = root.resolve("mkdocs.yml").toString(),
            docsDirPath = docs.toString(),
        )
        val gateway = DocsFileGatewayAdapter(trashMover = { false })
        try {
            assertFailure(gateway.createMarkdownFile(instance, "invalid.txt", "x"), "VALIDATION")
            assertFailure(gateway.createMarkdownFile(instance, "../escape.md", "x"), "INSTANCE_SCOPE")

            Files.writeString(docs.resolve("conflict"), "blocker")
            assertFailure(gateway.createMarkdownFile(instance, "conflict/new.md", "x"), "FILE_IO")

            assertFailure(gateway.deleteMarkdownFile(instance, "invalid.txt", TopicDeleteMode.RECOVERABLE), "VALIDATION")
            assertFailure(gateway.deleteMarkdownFile(instance, "missing.md", TopicDeleteMode.RECOVERABLE), "FILE_IO")

            Files.writeString(docs.resolve(".recovery"), "blocker")
            Files.writeString(docs.resolve("to-delete.md"), "content")
            assertFailure(gateway.deleteMarkdownFile(instance, "to-delete.md", TopicDeleteMode.RECOVERABLE), "FILE_IO")

            assertFailure(gateway.renameMarkdownFile(instance, "from.txt", "to.md"), "VALIDATION")
            assertFailure(gateway.renameMarkdownFile(instance, "from.md", "to.txt"), "VALIDATION")

            Files.createDirectories(docs.resolve("src"))
            Files.writeString(docs.resolve("src/from.md"), "content")
            Files.writeString(docs.resolve("blocked"), "file")
            assertFailure(gateway.moveMarkdownFile(instance, "src/from.md", "blocked/to.md"), "FILE_IO")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `markdown link rewriter covers instance scope io and non-target markdown branches`() {
        val root = Files.createTempDirectory("rewriter-failure")
        val docs = Files.createDirectories(root.resolve("docs"))
        try {
            val rewriter = MarkdownLinkRewriter()
            val instance = TopicInstanceRef(
                instanceId = "default",
                configPath = root.resolve("mkdocs.yml").toString(),
                docsDirPath = docs.toString(),
            )

            assertFailure(rewriter.rewrite(instance, "../outside.md", "inside.md"), "INSTANCE_SCOPE")

            val missingDocsInstance = TopicInstanceRef(
                instanceId = "default",
                configPath = root.resolve("mkdocs.yml").toString(),
                docsDirPath = root.resolve("missing-docs").toString(),
            )
            assertFailure(rewriter.rewrite(missingDocsInstance, "a.md", "b.md"), "FILE_IO")

            Files.createDirectories(docs.resolve("guide"))
            Files.writeString(
                docs.resolve("index.md"),
                "[Other](guide/other.md)\n",
            )
            Files.writeString(docs.resolve("guide/other.md"), "# other\n")
            Files.writeString(docs.resolve("guide/source.md"), "# source\n")

            val unchanged = requireSuccess(rewriter.rewrite(instance, "guide/source.md", "guide/renamed.md"))
            assertEquals(0, unchanged)
            assertTrue(Files.readString(docs.resolve("index.md")).contains("[Other](guide/other.md)"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uv bootstrap service covers two-phase install with get-deps`() {
        val root = Files.createTempDirectory("uv-bootstrap-branch")
        try {
            val configPath = root.resolve("mkdocs.yml")
            Files.writeString(
                configPath,
                """
                site_name: Demo
                theme: material
                plugins:
                  - search
                  - glightbox: {}
                """.trimIndent() + "\n",
            )

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                // Simulate mkdocs get-deps returning dependency list
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 0, stdout = "mkdocs-material\nmkdocs-glightbox\n")
                } else {
                    CommandResult(exitCode = 0)
                }
            }
            val result = service.bootstrap(root.toString())
            val runtimePath = root.resolve(".authord_venv").toString()
            assertTrue(result.success)
            // Verify base install
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs<2", "mkdocs-material==9.*"),
                commands[1],
            )
            // Verify get-deps was called
            assertTrue(commands[2].contains("get-deps"))
            // Verify discovered deps were installed
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs-material", "mkdocs-glightbox"),
                commands[3],
            )

            // When get-deps fails, bootstrap still succeeds with base mkdocs
            Files.writeString(
                configPath,
                """
                site_name: Demo
                plugins: 42
                """.trimIndent() + "\n",
            )
            val secondCommands = mutableListOf<List<String>>()
            val secondService = UvBootstrapService { command, _ ->
                secondCommands += command
                if (command.contains("get-deps")) {
                    CommandResult(exitCode = 1, stderr = "bad config")
                } else {
                    CommandResult(exitCode = 0)
                }
            }
            val second = secondService.bootstrap(root.toString())
            assertTrue(second.success)
            // Only 3 commands: venv + base install + get-deps (failed)
            assertEquals(3, secondCommands.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uv bootstrap detects mkdocs yaml file variant`() {
        val root = Files.createTempDirectory("uv-bootstrap-yaml-variant")
        try {
            // Use .yaml extension instead of .yml
            Files.writeString(root.resolve("mkdocs.yaml"), "site_name: Demo\n")
            Files.createDirectories(root.resolve(".authord_venv"))

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val result = service.bootstrap(root.toString())
            assertTrue(result.success)
            assertFalse(result.skipped)

            // Second call should skip (cached)
            val second = service.bootstrap(root.toString())
            assertTrue(second.success)
            assertTrue(second.skipped)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uv bootstrap uses windows python path when unix path does not exist`() {
        val root = Files.createTempDirectory("uv-bootstrap-win-python")
        try {
            val venv = root.resolve(".authord_venv")
            Files.createDirectories(venv)
            // Create a Windows-style python path instead of Unix-style
            val scriptsDir = venv.resolve("Scripts")
            Files.createDirectories(scriptsDir)
            Files.writeString(scriptsDir.resolve("python.exe"), "fake")
            Files.writeString(root.resolve("mkdocs.yml"), "site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val result = service.bootstrap(root.toString())
            assertTrue(result.success)
            // get-deps command should use Windows python path
            val getDepsCmd = commands.find { it.contains("get-deps") }
            assertTrue(getDepsCmd != null)
            assertTrue(getDepsCmd!!.first().contains("Scripts"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uv bootstrap deduplicates discovered dependencies`() {
        val root = Files.createTempDirectory("uv-bootstrap-dedup")
        try {
            Files.writeString(root.resolve("mkdocs.yml"), "site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                if (command.contains("get-deps")) {
                    // Simulate duplicate entries from get-deps
                    CommandResult(exitCode = 0, stdout = "mkdocs-material\nmkdocs-material\npymdown-extensions\n")
                } else {
                    CommandResult(exitCode = 0)
                }
            }
            val result = service.bootstrap(root.toString())
            assertTrue(result.success)
            // Verify deps are deduplicated
            val depsCmd = commands.last()
            val runtimePath = root.resolve(".authord_venv").toString()
            assertEquals(
                listOf("uv", "pip", "install", "--python", runtimePath, "mkdocs-material", "pymdown-extensions"),
                depsCmd,
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uv bootstrap handles unreadable config file gracefully`() {
        val root = Files.createTempDirectory("uv-bootstrap-unreadable-config")
        try {
            // Create mkdocs.yml as a directory (causes readAllBytes to throw)
            Files.createDirectories(root.resolve("mkdocs.yml"))
            // Also create requirements.txt as a directory
            Files.createDirectories(root.resolve("requirements.txt"))
            Files.createDirectories(root.resolve(".authord_venv"))

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val result = service.bootstrap(root.toString())
            assertTrue(result.success)
            assertFalse(result.skipped)

            // Second call should re-run because dependency discovery confidence is low
            // when config inspection cannot reliably parse plugin declarations.
            val second = service.bootstrap(root.toString())
            assertTrue(second.success)
            assertFalse(second.skipped)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `uv bootstrap uses fallback python path when venv has no python binary`() {
        val root = Files.createTempDirectory("uv-bootstrap-no-python")
        try {
            // Create venv directory but WITHOUT any python binary inside
            val venv = root.resolve(".authord_venv")
            Files.createDirectories(venv)
            Files.writeString(root.resolve("mkdocs.yml"), "site_name: Demo\n")

            val commands = mutableListOf<List<String>>()
            val service = UvBootstrapService { command, _ ->
                commands += command
                CommandResult(exitCode = 0)
            }
            val result = service.bootstrap(root.toString())
            assertTrue(result.success)

            // get-deps command should use the fallback unix-style path
            val getDepsCmd = commands.find { it.contains("get-deps") }
            assertTrue(getDepsCmd != null)
            val pythonPath = getDepsCmd!!.first()
            assertTrue(pythonPath.contains("bin/python"), "Expected fallback unix python path, got: $pythonPath")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun <T> requireSuccess(result: TopicGatewayResult<T>): T {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }

    private fun <T> assertFailure(result: TopicGatewayResult<T>, expectedCode: String) {
        when (result) {
            is TopicGatewayResult.Success -> fail("Expected failure with $expectedCode")
            is TopicGatewayResult.Failure -> assertEquals(expectedCode, result.error.code.name)
        }
    }
}
