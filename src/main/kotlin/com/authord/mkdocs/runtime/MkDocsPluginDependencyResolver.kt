package com.authord.mkdocs.runtime

/**
 * Resolves MkDocs plugin identifiers to installable Python package names.
 */
object MkDocsPluginDependencyResolver {
    fun packageForPlugin(pluginId: String): String? {
        val normalized = normalizePluginId(pluginId)
        if (normalized.isEmpty()) {
            return null
        }
        if (normalized.startsWith("mkdocs-")) {
            return normalized
        }
        return "mkdocs-$normalized"
    }

    fun packagesFromConfigValue(rawPlugins: Any?): List<String> {
        return extractPluginIds(rawPlugins)
            .mapNotNull(::packageForPlugin)
            .distinct()
    }

    private fun extractPluginIds(rawPlugins: Any?): List<String> {
        val pluginIds = when (rawPlugins) {
            is List<*> -> rawPlugins.mapNotNull(::extractPluginId)
            is Map<*, *> -> rawPlugins.keys.mapNotNull { key -> key?.toString() }
            else -> listOfNotNull(extractPluginId(rawPlugins))
        }
        return pluginIds
            .map(::normalizePluginId)
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun extractPluginId(rawPlugin: Any?): String? {
        return when (rawPlugin) {
            is String -> rawPlugin
            is Map<*, *> -> rawPlugin.entries.firstOrNull()?.key?.toString()
            else -> null
        }
    }

    private fun normalizePluginId(pluginId: String): String {
        return pluginId
            .trim()
            .removePrefix("'")
            .removeSuffix("'")
            .removePrefix("\"")
            .removeSuffix("\"")
            .lowercase()
            .replace('_', '-')
    }
}
