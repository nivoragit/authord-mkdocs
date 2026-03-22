package com.authord.mkdocs.ports.topic

import kotlin.test.Test
import kotlin.test.assertTrue

class InstanceRegistryPortContractTest {
    @Test
    fun `defines default discovery registration listing selection and active instance lookup`() {
        val methods = InstanceRegistryPort::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("discoverDefaultInstance"))
        assertTrue(methods.contains("registerInstance"))
        assertTrue(methods.contains("listInstances"))
        assertTrue(methods.contains("selectActiveInstance"))
        assertTrue(methods.contains("activeInstance"))
    }

    @Test
    fun `instance reference includes id config and docs paths`() {
        val instance = TopicInstanceRef(
            instanceId = "root",
            configPath = "/repo/mkdocs.yml",
            docsDirPath = "/repo/docs",
        )

        assertTrue(instance.instanceId.isNotBlank())
        assertTrue(instance.configPath.endsWith("mkdocs.yml"))
        assertTrue(instance.docsDirPath.endsWith("docs"))
    }
}
