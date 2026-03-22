package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.SystemInfoRt
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class FailureBypassStore {
    private val bypassKeys = ConcurrentHashMap.newKeySet<String>()

    fun markBypass(filePath: String, configPath: String?) {
        bypassKeys += key(filePath, configPath)
    }

    fun isBypassed(filePath: String, configPath: String?): Boolean {
        return bypassKeys.contains(key(filePath, configPath))
    }

    fun clearBypass(filePath: String, configPath: String?) {
        bypassKeys.remove(key(filePath, configPath))
    }

    fun clearAll() {
        bypassKeys.clear()
    }

    private fun key(filePath: String, configPath: String?): String {
        return "${normalize(filePath)}|${normalize(configPath.orEmpty())}"
    }

    private fun normalize(path: String): String {
        val normalized = path.trim().replace('\\', '/')
        return if (SystemInfoRt.isFileSystemCaseSensitive) normalized else normalized.lowercase(Locale.ROOT)
    }
}

