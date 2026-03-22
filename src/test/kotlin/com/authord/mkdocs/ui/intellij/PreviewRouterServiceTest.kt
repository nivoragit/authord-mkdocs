package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewRouterServiceTest {
    @Test
    fun `router routes docs scoped markdown to authord`() {
        val projectRoot = Files.createTempDirectory("preview-router-docs")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val docsFile = projectRoot.resolve("docs/guide.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-router-docs")
            val router = PreviewRouterService()

            val decision = router.decide(project, docsFile)

            assertEquals(PreviewRouteTarget.AUTHORD, decision.target)
            assertEquals(PreviewRouteReason.DOCS_SCOPED, decision.reason)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `router routes non docs markdown to intellij`() {
        val projectRoot = Files.createTempDirectory("preview-router-non-docs")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val readme = projectRoot.resolve("README.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-router-non-docs")
            val router = PreviewRouterService()

            val decision = router.decide(project, readme)

            assertEquals(PreviewRouteTarget.INTELLIJ, decision.target)
            assertEquals(PreviewRouteReason.NON_DOCS, decision.reason)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `router routes ambiguous scope markdown to intellij`() {
        val projectRoot = Files.createTempDirectory("preview-router-ambiguous")
        try {
            val sharedDocs = projectRoot.resolve("shared")
            Files.createDirectories(sharedDocs)
            val filePath = sharedDocs.resolve("guide.md")
            Files.writeString(filePath, "# Guide\n")
            val sharedPath = sharedDocs.toAbsolutePath().normalize().toString().replace('\\', '/')

            val siteOne = projectRoot.resolve("one")
            val siteTwo = projectRoot.resolve("two")
            Files.createDirectories(siteOne)
            Files.createDirectories(siteTwo)
            Files.writeString(siteOne.resolve("mkdocs.yml"), "site_name: One\ndocs_dir: '$sharedPath'\n")
            Files.writeString(siteTwo.resolve("mkdocs.yml"), "site_name: Two\ndocs_dir: '$sharedPath'\n")

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-router-ambiguous")
            val router = PreviewRouterService()

            val decision = router.decide(project, filePath.toString())

            assertEquals(PreviewRouteTarget.INTELLIJ, decision.target)
            assertEquals(PreviewRouteReason.AMBIGUOUS, decision.reason)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `router routes misconfigured scope markdown to intellij`() {
        val projectRoot = Files.createTempDirectory("preview-router-misconfigured")
        try {
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: missing-docs\n")
            val filePath = projectRoot.resolve("notes.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-router-misconfigured")
            val router = PreviewRouterService()

            val decision = router.decide(project, filePath)

            assertEquals(PreviewRouteTarget.INTELLIJ, decision.target)
            assertEquals(PreviewRouteReason.MISCONFIGURED, decision.reason)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `router honors failure bypass for docs scoped markdown`() {
        val projectRoot = Files.createTempDirectory("preview-router-bypass")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val docsFile = projectRoot.resolve("docs/guide.md").toString()
            val bypassStore = FailureBypassStore()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "preview-router-bypass")
            val router = PreviewRouterService(
                bypassStoreResolver = { bypassStore },
            )

            val initialDecision = router.decide(project, docsFile)
            bypassStore.markBypass(docsFile, initialDecision.configPath)
            val decision = router.decide(project, docsFile)

            assertEquals(PreviewRouteTarget.INTELLIJ, decision.target)
            assertEquals(PreviewRouteReason.FAILURE_BYPASS, decision.reason)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}

