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
     * - non-docs or non-markdown paths -> `null`
     */
    fun mapToRoute(selectedPath: String, docsRoot: String = "docs"): String? {
        val normalized = selectedPath.replace("\\", "/").trimStart('/')
        val normalizedRoot = docsRoot.replace("\\", "/").trim('/')

        if (!normalized.startsWith("$normalizedRoot/")) {
            return null
        }
        if (!normalized.endsWith(".md")) {
            return null
        }

        val relative = normalized.removePrefix("$normalizedRoot/")
        if (relative == "index.md") {
            return "/"
        }

        val noExt = relative.removeSuffix(".md")
        val route = if (noExt.endsWith("/index")) {
            noExt.removeSuffix("/index")
        } else {
            noExt
        }

        val normalizedRoute = route.trim('/')
        return if (normalizedRoute.isBlank()) "/" else "/$normalizedRoute/"
    }
}
