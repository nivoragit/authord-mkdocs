package com.authord.mkdocs.core.topic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathNormalizationPolicyTest {
    private val caseSensitive = PathPolicy(caseSensitiveComparison = true)
    private val caseInsensitive = PathPolicy(caseSensitiveComparison = false)

    @Test
    fun `normalize handles empty and whitespace-only input`() {
        assertEquals("", caseSensitive.normalize(""))
        assertEquals("", caseSensitive.normalize("   "))
    }

    @Test
    fun `normalize handles windows drive and dot segments`() {
        val normalized = caseSensitive.normalize(" C:\\docs\\.\\guides\\..\\readme.md ")
        assertEquals("C:/docs/readme.md", normalized)
        assertEquals("C:/", caseSensitive.normalize("C:/./"))
    }

    @Test
    fun `normalize handles leading slash and relative parent traversal`() {
        assertEquals("/", caseSensitive.normalize("/./"))
        assertEquals("/docs/index.md", caseSensitive.normalize("/docs/./guide/../index.md"))
        assertEquals("../../index.md", caseSensitive.normalize("../../guide/../index.md"))
    }

    @Test
    fun `normalize treats non-letter drive prefix as regular path`() {
        assertEquals("1:/docs", caseSensitive.normalize("1:/docs"))
    }

    @Test
    fun `comparison key follows case sensitivity policy`() {
        assertEquals("Guide/Intro.md", caseSensitive.comparisonKey("Guide/Intro.md"))
        assertEquals("guide/intro.md", caseInsensitive.comparisonKey("Guide/Intro.md"))
    }

    @Test
    fun `equivalent uses normalized comparison key`() {
        assertFalse(caseSensitive.equivalent("Guide/Intro.md", "guide/intro.md"))
        assertTrue(caseInsensitive.equivalent("Guide/Intro.md", "guide/intro.md"))
    }

    @Test
    fun `for os name maps windows and mac to case-insensitive mode`() {
        assertFalse(PathPolicy.forOsName("Windows 11").caseSensitiveComparison)
        assertFalse(PathPolicy.forOsName("macOS").caseSensitiveComparison)
        assertFalse(PathPolicy.forOsName("Darwin").caseSensitiveComparison)
        assertTrue(PathPolicy.forOsName("Linux").caseSensitiveComparison)
    }

    @Test
    fun `for current os delegates to os-name mapping`() {
        val osName = System.getProperty("os.name").orEmpty()
        assertEquals(PathPolicy.forOsName(osName), PathPolicy.forCurrentOs())
    }
}
