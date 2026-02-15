package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TOPIC_TREE_APP_SERVICE_API_VERSION
import kotlin.test.Test
import kotlin.test.assertTrue

class TopicTreeApplicationServiceContractTest {
    @Test
    fun `application service exposes execute rollback and active instance methods`() {
        val methods = TopicTreeApplicationService::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("execute"))
        assertTrue(methods.contains("rollback"))
        assertTrue(methods.contains("activeInstance"))
    }

    @Test
    fun `application service api version follows semantic format`() {
        assertTrue(TOPIC_TREE_APP_SERVICE_API_VERSION.matches(Regex("^\\d+\\.\\d+\\.\\d+$")))
    }
}
