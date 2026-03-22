package com.authord.mkdocs.core.topic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TopicDomainModelTest {
    @Test
    fun `topic id requires non-blank values`() {
        val topicId = TopicId("topic-1")

        assertEquals("topic-1", topicId.value)
        assertFailsWith<IllegalArgumentException> { TopicId(" ") }
    }

    @Test
    fun `topic order requires non-negative values`() {
        val order = TopicOrder(2)

        assertEquals(2, order.value)
        assertFailsWith<IllegalArgumentException> { TopicOrder(-1) }
    }

    @Test
    fun `topic value objects expose boxed getters`() {
        val boxedTopicId: Any = TopicId("boxed-topic")
        val boxedOrder: Any = TopicOrder(3)

        val topicIdValue = boxedTopicId::class.java.getMethod("getValue").invoke(boxedTopicId)
        val orderValue = boxedOrder::class.java.getMethod("getValue").invoke(boxedOrder)

        assertEquals("boxed-topic", topicIdValue)
        assertEquals(3, orderValue)
    }

    @Test
    fun `topic link and metadata expose fields`() {
        val from = TopicId("from")
        val to = TopicId("to")
        val link = TopicLink(from = from, to = to)
        val metadata = TopicMetadata(attributes = mapOf("owner" to "docs"))

        assertEquals("from", link.from.value)
        assertEquals("to", link.to.value)
        assertEquals("child", link.relation)
        assertEquals("docs", metadata.attributes["owner"])
    }

    @Test
    fun `topic metadata defaults to empty attributes`() {
        val metadata = TopicMetadata()

        assertTrue(metadata.attributes.isEmpty())
    }

    @Test
    fun `topic tree alias points to aggregate behavior`() {
        val tree: TopicTree = TopicTreeAggregate("tree-alias")

        assertEquals("tree-alias", tree.treeId)
    }
}
