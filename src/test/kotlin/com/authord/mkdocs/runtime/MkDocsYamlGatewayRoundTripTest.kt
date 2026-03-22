package com.authord.mkdocs.runtime

import com.authord.mkdocs.ports.topic.MkDocsConfigDocument
import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import com.authord.mkdocs.ports.topic.TopicNavNode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class MkDocsYamlGatewayRoundTripTest {
    @Test
    fun `load and write preserve python-tagged scalars`() {
        val projectRoot = Files.createTempDirectory("mkdocs-python-tagged-scalars")
        val configPath = projectRoot.resolve("mkdocs.yml")
        Files.writeString(
            configPath,
            """
            site_name: Demo Site
            docs_dir: docs
            nav:
              - Home: index.md
            markdown_extensions:
              - pymdownx.emoji:
                  emoji_index: !!python/name:materialx.emoji.twemoji
                  emoji_generator: !!python/name:materialx.emoji.to_svg
            extra:
              module_ref: !!python/module:materialx.emoji
            """.trimIndent() + "\n",
        )

        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = configPath.toString(),
            docsDirPath = projectRoot.resolve("docs").toString(),
        )
        val gateway = MkDocsYamlGateway()
        val loaded = requireSuccess(gateway.loadConfig(instance))

        val updated = loaded.copy(
            nav = loaded.nav + TopicNavNode(nodeId = "n1", title = "Guide", path = "guide.md"),
        )
        requireSuccess(gateway.writeConfig(instance, updated))

        val written = Files.readString(configPath)
        assertTrue(written.contains("python/name:materialx.emoji.twemoji"))
        assertTrue(written.contains("python/name:materialx.emoji.to_svg"))
        assertTrue(written.contains("python/module:materialx.emoji"))
        assertTrue(written.contains("- Guide: guide.md"))
    }

    @Test
    fun `roundtrip parse and deterministic serialization keeps site_name docs_dir nav and not_in_nav`() {
        val projectRoot = Files.createTempDirectory("mkdocs-roundtrip")
        val configPath = projectRoot.resolve("mkdocs.yml")
        Files.writeString(
            configPath,
            """
            site_name: Demo Site
            docs_dir: docs
            nav:
              - Home: index.md
              - Guide:
                  - Intro: guide/intro.md
                  - Ext: https://example.com/docs
            not_in_nav:
              - drafts/new.md
              - scratch.md
            """.trimIndent() + "\n",
        )

        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = configPath.toString(),
            docsDirPath = projectRoot.resolve("docs").toString(),
        )
        val gateway = MkDocsYamlGateway()

        val loaded = requireSuccess(gateway.loadConfig(instance))
        assertEquals("Demo Site", loaded.siteName)
        assertEquals("docs", loaded.docsDir)
        assertEquals(2, loaded.nav.size)
        assertEquals("index.md", loaded.nav.first().path)
        assertEquals(2, loaded.notInNav.size)
        assertTrue(loaded.navPresent)

        val first = requireSuccess(gateway.serializeDeterministically(loaded))
        val second = requireSuccess(gateway.serializeDeterministically(loaded))
        assertEquals(first, second)
        assertTrue(first.contains("site_name: Demo Site"))
        assertTrue(first.contains("docs_dir: docs"))
        assertTrue(first.contains("nav:"))
        assertTrue(first.contains("not_in_nav:"))
    }

    @Test
    fun `load and serialize without nav preserves no-nav mode`() {
        val projectRoot = Files.createTempDirectory("mkdocs-no-nav")
        val configPath = projectRoot.resolve("mkdocs.yml")
        Files.writeString(
            configPath,
            """
            docs_dir: docs
            """.trimIndent() + "\n",
        )

        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = configPath.toString(),
            docsDirPath = projectRoot.resolve("docs").toString(),
        )
        val gateway = MkDocsYamlGateway()

        val loaded = requireSuccess(gateway.loadConfig(instance))
        assertEquals("docs", loaded.docsDir)
        assertTrue(loaded.nav.isEmpty())
        assertTrue(!loaded.navPresent)

        val serialized = requireSuccess(gateway.serializeDeterministically(loaded))
        assertTrue(serialized.contains("docs_dir: docs"))
        assertTrue(!serialized.contains("\nnav:"))
    }

    @Test
    fun `writeConfig preserves deterministic output for unchanged logical structure`() {
        val projectRoot = Files.createTempDirectory("mkdocs-write")
        val configPath = projectRoot.resolve("mkdocs.yml")
        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = configPath.toString(),
            docsDirPath = projectRoot.resolve("docs").toString(),
        )
        val gateway = MkDocsYamlGateway()
        val document = MkDocsConfigDocument(
            docsDir = "docs",
            nav = listOf(
                TopicNavNode(nodeId = "n0", title = "Home", path = "index.md"),
                TopicNavNode(
                    nodeId = "n1",
                    title = "Guide",
                    children = listOf(TopicNavNode(nodeId = "n1-0", title = "Intro", path = "guide/intro.md")),
                ),
            ),
            notInNav = listOf("scratch.md", "drafts/new.md"),
            siteName = "DemoSite",
        )

        requireSuccess(gateway.writeConfig(instance, document))
        val firstWrite = Files.readString(configPath)

        requireSuccess(gateway.writeConfig(instance, document))
        val secondWrite = Files.readString(configPath)

        assertEquals(firstWrite, secondWrite)
        assertTrue(firstWrite.contains("site_name: DemoSite"))
        assertTrue(firstWrite.contains("docs_dir: docs"))
        assertTrue(firstWrite.contains("- Home: index.md"))
        assertTrue(firstWrite.contains("not_in_nav:"))
    }

    @Test
    fun `writeConfig preserves unknown keys and custom docs_dir when nav is present`() {
        val projectRoot = Files.createTempDirectory("mkdocs-preserve-unknown")
        val configPath = projectRoot.resolve("mkdocs.yml")
        Files.writeString(
            configPath,
            """
            site_name: Demo Site
            docs_dir: content
            theme:
              name: material
            plugins:
              - search
            extra_css:
              - styles/custom.css
            nav:
              - Home: index.md
            """.trimIndent() + "\n",
        )

        val instance = TopicInstanceRef(
            instanceId = "default",
            configPath = configPath.toString(),
            docsDirPath = projectRoot.resolve("content").toString(),
        )
        val gateway = MkDocsYamlGateway()
        val loaded = requireSuccess(gateway.loadConfig(instance))

        val updated = loaded.copy(
            nav = loaded.nav + TopicNavNode(nodeId = "n2", title = "Guide", path = "guide/intro.md"),
        )
        requireSuccess(gateway.writeConfig(instance, updated))

        val written = Files.readString(configPath)
        assertTrue(written.contains("docs_dir: content"))
        assertTrue(written.contains("theme:"))
        assertTrue(written.contains("plugins:"))
        assertTrue(written.contains("extra_css:"))
        assertTrue(written.contains("- Guide: guide/intro.md"))
    }

    @Test
    fun `serializeDeterministically uses document docs_dir when no raw yaml is present`() {
        val gateway = MkDocsYamlGateway()
        val serialized = requireSuccess(
            gateway.serializeDeterministically(
                MkDocsConfigDocument(
                    docsDir = "documentation",
                    nav = listOf(TopicNavNode(nodeId = "n1", title = "Home", path = "index.md")),
                ),
            ),
        )

        assertTrue(serialized.contains("docs_dir: documentation"))
    }

    private fun <T> requireSuccess(result: TopicGatewayResult<T>): T {
        return when (result) {
            is TopicGatewayResult.Success -> result.value
            is TopicGatewayResult.Failure -> fail("Unexpected failure: ${result.error.code} ${result.error.detail}")
        }
    }
}
