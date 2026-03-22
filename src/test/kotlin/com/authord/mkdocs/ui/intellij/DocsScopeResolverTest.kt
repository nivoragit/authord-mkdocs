package com.authord.mkdocs.ui.intellij

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DocsScopeResolverTest {
    @Test
    fun `docs scope resolver marks docs markdown as docs scoped`() {
        val projectRoot = Files.createTempDirectory("docs-scope-resolver-docs")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val markdownPath = projectRoot.resolve("docs/guide.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "docs-scope-docs")
            val resolver = DocsScopeResolver()

            val result = resolver.resolve(project, markdownPath)

            assertEquals(DocsScopeStatus.DOCS_SCOPED, result.status)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `docs scope resolver marks non docs markdown as non docs`() {
        val projectRoot = Files.createTempDirectory("docs-scope-resolver-non-docs")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val markdownPath = projectRoot.resolve("README.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "docs-scope-non-docs")
            val resolver = DocsScopeResolver()

            val result = resolver.resolve(project, markdownPath)

            assertEquals(DocsScopeStatus.NON_DOCS, result.status)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `docs scope resolver marks tied docs roots as ambiguous`() {
        val projectRoot = Files.createTempDirectory("docs-scope-resolver-ambiguous")
        try {
            val sharedDocs = projectRoot.resolve("shared-docs")
            Files.createDirectories(sharedDocs)
            val filePath = sharedDocs.resolve("guide.md")
            Files.writeString(filePath, "# Guide\n")

            val siteOne = projectRoot.resolve("site-one")
            val siteTwo = projectRoot.resolve("site-two")
            Files.createDirectories(siteOne)
            Files.createDirectories(siteTwo)
            val sharedPath = sharedDocs.toAbsolutePath().normalize().toString().replace('\\', '/')
            Files.writeString(siteOne.resolve("mkdocs.yml"), "site_name: One\ndocs_dir: '$sharedPath'\n")
            Files.writeString(siteTwo.resolve("mkdocs.yml"), "site_name: Two\ndocs_dir: '$sharedPath'\n")

            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "docs-scope-ambiguous")
            val resolver = DocsScopeResolver()

            val result = resolver.resolve(project, filePath.toString())

            assertEquals(DocsScopeStatus.AMBIGUOUS, result.status)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `docs scope resolver marks invalid docs dir as misconfigured`() {
        val projectRoot = Files.createTempDirectory("docs-scope-resolver-misconfigured")
        try {
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: missing-docs\n")
            val markdownPath = projectRoot.resolve("notes.md").toString()
            val project = IntellijTestFixtures.project(basePath = projectRoot.toString(), locationHash = "docs-scope-misconfigured")
            val resolver = DocsScopeResolver()

            val result = resolver.resolve(project, markdownPath)

            assertEquals(DocsScopeStatus.MISCONFIGURED, result.status)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}

