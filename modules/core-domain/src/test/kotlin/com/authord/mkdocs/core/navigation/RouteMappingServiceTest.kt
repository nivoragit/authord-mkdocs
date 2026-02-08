package com.authord.mkdocs.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteMappingServiceTest {
    private val service = RouteMappingService()

    @Test
    fun `maps docs index to root`() {
        assertEquals("/", service.mapToRoute("docs/index.md"))
    }

    @Test
    fun `maps segment index to trailing slash route`() {
        assertEquals("/foo/", service.mapToRoute("docs/foo/index.md"))
    }

    @Test
    fun `maps segment markdown file to trailing slash route`() {
        assertEquals("/foo/", service.mapToRoute("docs/foo.md"))
    }

    @Test
    fun `maps nested index path`() {
        assertEquals("/guide/setup/", service.mapToRoute("docs/guide/setup/index.md"))
    }

    @Test
    fun `maps double separator index path to root`() {
        assertEquals("/", service.mapToRoute("docs//index.md"))
    }

    @Test
    fun `returns null for non-docs paths`() {
        assertNull(service.mapToRoute("README.md"))
    }

    @Test
    fun `returns null for non markdown files under docs`() {
        assertNull(service.mapToRoute("docs/readme.txt"))
    }
}
