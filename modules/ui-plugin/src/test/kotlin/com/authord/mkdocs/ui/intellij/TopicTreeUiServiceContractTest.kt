package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ports.TOPIC_TREE_UI_SERVICE_API_VERSION
import kotlin.test.Test
import kotlin.test.assertTrue

class TopicTreeUiServiceContractTest {
    @Test
    fun `ui service exposes dispatch refresh and instance selection methods`() {
        val methods = TopicTreeUiService::class.java.methods.map { it.name }.toSet()
        assertTrue(methods.contains("dispatch"))
        assertTrue(methods.contains("refreshActiveTree"))
        assertTrue(methods.contains("selectInstance"))
    }

    @Test
    fun `ui service api version follows semantic format`() {
        assertTrue(TOPIC_TREE_UI_SERVICE_API_VERSION.matches(Regex("^\\d+\\.\\d+\\.\\d+$")))
    }
}
