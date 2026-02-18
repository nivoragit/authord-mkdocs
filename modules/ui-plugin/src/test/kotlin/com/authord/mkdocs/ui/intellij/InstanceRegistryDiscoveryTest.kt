package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstanceRegistryDiscoveryTest {
    @Test
    fun `discovers root mkdocs yml as default instance`() {
        val projectRoot = createTempDirectory("instance-discovery-root")
        projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
        projectRoot.resolve("docs").createDirectories()

        val registry = InstanceRegistryService()
        val discovered = registry.discoverDefaultInstance(projectRoot.toString())

        require(discovered is TopicGatewayResult.Success)
        val instance = discovered.value
        assertNotNull(instance)
        assertEquals("default", instance.instanceId)
        assertEquals("mkdocs.yml", Path.of(instance.configPath).fileName.toString())
        assertEquals("docs", Path.of(instance.docsDirPath).fileName.toString())
    }

    @Test
    fun `registers additional instances only through explicit add instance flow`() {
        val projectRoot = createTempDirectory("instance-discovery-explicit")
        projectRoot.resolve("mkdocs.yml").writeText("site_name: Demo\n")
        projectRoot.resolve("docs").createDirectories()

        val extraConfigDir = projectRoot.resolve("guide-alt")
        extraConfigDir.createDirectories()
        val extraConfig = extraConfigDir.resolve("mkdocs.yml")
        extraConfig.writeText("docs_dir: docs-alt\n")
        extraConfigDir.resolve("docs-alt").createDirectories()

        val registry = InstanceRegistryService()
        registry.discoverDefaultInstance(projectRoot.toString())
        val before = registry.listInstances()
        require(before is TopicGatewayResult.Success)
        assertEquals(1, before.value.size)

        val register = registry.registerInstance(
            com.authord.mkdocs.ports.topic.TopicInstanceRef(
                instanceId = "extra",
                configPath = extraConfig.toString(),
                docsDirPath = extraConfigDir.resolve("docs-alt").toString(),
            ),
        )
        require(register is TopicGatewayResult.Success)

        val after = registry.listInstances()
        require(after is TopicGatewayResult.Success)
        assertEquals(setOf("default", "extra"), after.value.map { it.instanceId }.toSet())
    }
}
