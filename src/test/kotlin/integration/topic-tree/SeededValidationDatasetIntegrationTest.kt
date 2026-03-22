package integration.topic.tree

import com.authord.mkdocs.core.topic.PathPolicy
import com.authord.mkdocs.core.topic.TopicTreeValidationIssueType
import com.authord.mkdocs.core.topic.TopicTreeValidationService
import com.authord.mkdocs.ports.topic.TopicNavNode
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class SeededValidationDatasetIntegrationTest {
    @Test
    fun `seeded validation dataset declares required case families`() {
        val yaml = Files.readString(datasetPath())

        assertTrue(yaml.contains("id: broken-path-001"))
        assertTrue(yaml.contains("id: duplicate-ref-001"))
        assertTrue(yaml.contains("id: malformed-link-001"))
    }

    @Test
    fun `validator detects each seeded validation family`() {
        val validator = TopicTreeValidationService(PathPolicy(caseSensitiveComparison = true))

        val issues = validator.validate(
            navNodes = listOf(
                TopicNavNode(nodeId = "n1", title = "Missing", path = "missing.md"),
                TopicNavNode(nodeId = "n2", title = "Intro A", path = "intro.md"),
                TopicNavNode(nodeId = "n3", title = "Intro B", path = "intro.md"),
                TopicNavNode(nodeId = "n4", title = "Bad Link", externalUrl = "ht!tp://broken"),
            ),
            docsRelativePaths = listOf("intro.md"),
        )

        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.BROKEN_PATH })
        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.DUPLICATE_REFERENCE })
        assertTrue(issues.any { it.type == TopicTreeValidationIssueType.MALFORMED_LINK })
    }

    private fun datasetPath(): Path {
        val root = findRepoRoot()
        return root.resolve("tests/fixtures/topic-tree/validation-seeded-cases.yaml")
    }

    private fun findRepoRoot(): Path {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (current.parent != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Repository root not found")
    }
}
