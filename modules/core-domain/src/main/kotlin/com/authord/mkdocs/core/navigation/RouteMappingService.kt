package com.authord.mkdocs.core.navigation

class RouteMappingService {
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
