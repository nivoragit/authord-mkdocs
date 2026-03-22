package com.authord.mkdocs.ports

import com.authord.mkdocs.ports.topic.AddTopicNodeCommand
import com.authord.mkdocs.ports.topic.MoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RemoveTopicNodeCommand
import com.authord.mkdocs.ports.topic.RenameTopicNodeCommand
import com.authord.mkdocs.ports.topic.ReorderTopicNodesCommand
import com.authord.mkdocs.ports.topic.ReparentTopicNodeCommand
import com.authord.mkdocs.ports.topic.TopicTreeCommandResult
import com.authord.mkdocs.ports.topic.TopicTreeCommandStatus
import com.authord.mkdocs.ports.topic.TopicTreeCommandType
import com.authord.mkdocs.ports.topic.TopicTreeViolation
import com.authord.mkdocs.ports.topic.ValidateTopicTreeCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopicTreeCommandDtosCoverageTest {
    @Test
    fun `command dtos expose expected fields and command types`() {
        val add = AddTopicNodeCommand("c1", "t1", "root", "n1", "Title", 0)
        val move = MoveTopicNodeCommand("c2", "t1", "n1", "root", 1)
        val remove = RemoveTopicNodeCommand("c3", "t1", "n1")
        val rename = RenameTopicNodeCommand("c4", "t1", "n1", "Renamed")
        val reparent = ReparentTopicNodeCommand("c5", "t1", "n1", "root")
        val reorder = ReorderTopicNodesCommand("c6", "t1", "root", listOf("n1", "n2"))
        val validate = ValidateTopicTreeCommand("c7", "t1", strict = false)

        assertEquals(TopicTreeCommandType.ADD, add.commandType)
        assertEquals("c1", add.commandId)
        assertEquals("t1", add.treeId)
        assertEquals("root", add.parentNodeId)
        assertEquals("n1", add.nodeId)
        assertEquals("Title", add.title)
        assertEquals(0, add.orderIndex)

        assertEquals(TopicTreeCommandType.MOVE, move.commandType)
        assertEquals("c2", move.commandId)
        assertEquals("t1", move.treeId)
        assertEquals("n1", move.nodeId)
        assertEquals("root", move.newParentNodeId)
        assertEquals(1, move.newOrderIndex)

        assertEquals(TopicTreeCommandType.REMOVE, remove.commandType)
        assertEquals("c3", remove.commandId)
        assertEquals("t1", remove.treeId)
        assertEquals("n1", remove.nodeId)

        assertEquals(TopicTreeCommandType.RENAME, rename.commandType)
        assertEquals("c4", rename.commandId)
        assertEquals("t1", rename.treeId)
        assertEquals("n1", rename.nodeId)
        assertEquals("Renamed", rename.newTitle)

        assertEquals(TopicTreeCommandType.REPARENT, reparent.commandType)
        assertEquals("c5", reparent.commandId)
        assertEquals("t1", reparent.treeId)
        assertEquals("n1", reparent.nodeId)
        assertEquals("root", reparent.newParentNodeId)

        assertEquals(TopicTreeCommandType.REORDER, reorder.commandType)
        assertEquals("c6", reorder.commandId)
        assertEquals("t1", reorder.treeId)
        assertEquals("root", reorder.parentNodeId)
        assertEquals(listOf("n1", "n2"), reorder.orderedNodeIds)

        assertEquals(TopicTreeCommandType.VALIDATE, validate.commandType)
        assertEquals("c7", validate.commandId)
        assertEquals("t1", validate.treeId)
        assertEquals(false, validate.strict)
    }

    @Test
    fun `result and violation dtos expose fields`() {
        val violation = TopicTreeViolation(code = "INVALID", detail = "detail")
        val result = TopicTreeCommandResult(
            commandId = "cmd-1",
            status = TopicTreeCommandStatus.REJECTED,
            treeVersion = null,
            violations = listOf(violation),
            message = "Rejected",
        )

        assertEquals("INVALID", violation.code)
        assertEquals("detail", violation.detail)
        assertEquals("cmd-1", result.commandId)
        assertEquals(TopicTreeCommandStatus.REJECTED, result.status)
        assertTrue(result.treeVersion == null)
        assertEquals(1, result.violations.size)
        assertEquals("Rejected", result.message)
    }
}
