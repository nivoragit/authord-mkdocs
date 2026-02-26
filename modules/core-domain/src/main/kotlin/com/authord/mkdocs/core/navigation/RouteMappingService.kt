package com.authord.mkdocs.core.navigation

/**
 * Maps docs-relative markdown files to preview routes.
 */
class RouteMappingService {
    /**
     * Converts a selected docs path to normalized preview route.
     *
     * Rules:
     * - `docs/index.md` -> `/`
     * - `docs/foo/index.md` -> `/foo/`
     * - `docs/foo.md` -> `/foo/`
     * - with `useDirectoryUrls=false`: `docs/foo.md` -> `/foo.html`
     * - non-docs or non-markdown paths -> `null`
     */
    fun mapToRoute(
        selectedPath: String,
        docsRoot: String = "docs",
        useDirectoryUrls: Boolean = true,
    ): String? {
        val normalized = selectedPath.replace("\\", "/").trimStart('/')
        val normalizedRoot = docsRoot.replace("\\", "/").trim('/')

        if (!normalized.startsWith("$normalizedRoot/")) {
            return null
        }

        val relative = normalized.removePrefix("$normalizedRoot/")
        val withoutMarkdownExtension = when {
            relative.endsWith(".md", ignoreCase = true) -> relative.dropLast(3)
            relative.endsWith(".markdown", ignoreCase = true) -> relative.dropLast(9)
            else -> null
        } ?: return null

        if (useDirectoryUrls) {
            return mapDirectoryRoute(withoutMarkdownExtension)
        }
        return mapHtmlRoute(withoutMarkdownExtension)
    }

    private fun mapDirectoryRoute(relativeWithoutExtension: String): String {
        if (relativeWithoutExtension.equals("index", ignoreCase = true)) {
            return "/"
        }

        val route = if (relativeWithoutExtension.endsWith("/index", ignoreCase = true)) {
            relativeWithoutExtension.dropLast(6)
        } else {
            relativeWithoutExtension
        }

        val normalizedRoute = route.trim('/')
        return if (normalizedRoute.isBlank()) "/" else "/$normalizedRoute/"
    }

    private fun mapHtmlRoute(relativeWithoutExtension: String): String {
        if (relativeWithoutExtension.equals("index", ignoreCase = true)) {
            return "/"
        }

        val normalizedRoute = relativeWithoutExtension.trim('/')
        if (normalizedRoute.isBlank()) {
            return "/"
        }
        return "/$normalizedRoute.html"
    }
}
