package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.topic.TopicGatewayResult
import com.authord.mkdocs.ports.topic.TopicInstanceRef
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InstanceSelectionPersistenceTest {
    @Test
    fun `last selected instance is restored when reopening same project`() {
        val projectRoot = createTempDirectory("instance-selection-persistence")
        projectRoot.resolve("mkdocs.yml").writeText("site_name: Root\n")
        projectRoot.resolve("docs").createDirectories()

        val extraRoot = projectRoot.resolve("extra-site")
        extraRoot.createDirectories()
        val extraConfig = extraRoot.resolve("mkdocs.yml")
        extraConfig.writeText("docs_dir: docs-extra\n")
        val extraDocs = extraRoot.resolve("docs-extra")
        extraDocs.createDirectories()

        val firstSession = InstanceRegistryService()
        firstSession.discoverDefaultInstance(projectRoot.toString())
        firstSession.registerInstance(
            TopicInstanceRef(
                instanceId = "extra",
                configPath = extraConfig.toString(),
                docsDirPath = extraDocs.toString(),
            ),
        )
        val selected = firstSession.selectActiveInstance("extra")
        require(selected is TopicGatewayResult.Success)
        assertEquals("extra", selected.value.instanceId)

        val secondSession = InstanceRegistryService()
        secondSession.discoverDefaultInstance(projectRoot.toString())
        val restored = secondSession.activeInstance()
        require(restored is TopicGatewayResult.Success)
        val restoredInstance = assertNotNull(restored.value)
        assertEquals("extra", restoredInstance.instanceId)
    }
}
