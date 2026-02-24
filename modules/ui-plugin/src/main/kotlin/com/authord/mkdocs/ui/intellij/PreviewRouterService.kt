package com.authord.mkdocs.ui.intellij

import com.intellij.openapi.project.Project

internal enum class PreviewRouteTarget {
    AUTHORD,
    INTELLIJ,
}

internal enum class PreviewRouteReason {
    NON_MARKDOWN,
    FAILURE_BYPASS,
    DOCS_SCOPED,
    NON_DOCS,
    AMBIGUOUS,
    MISCONFIGURED,
}

internal data class PreviewDecision(
    val target: PreviewRouteTarget,
    val scopeStatus: DocsScopeStatus,
    val reason: PreviewRouteReason,
    val configPath: String? = null,
    val detail: String? = null,
)

internal class PreviewRouterService(
    private val docsScopeResolver: DocsScopeResolver = DocsScopeResolver(),
    private val bypassStoreResolver: (Project) -> FailureBypassStore? = { project ->
        runCatching { project.getService(FailureBypassStore::class.java) }.getOrNull()
    },
) {
    fun decide(project: Project, filePath: String): PreviewDecision {
        if (!isMarkdownPath(filePath)) {
            return PreviewDecision(
                target = PreviewRouteTarget.INTELLIJ,
                scopeStatus = DocsScopeStatus.NON_DOCS,
                reason = PreviewRouteReason.NON_MARKDOWN,
                detail = "Preview routing applies only to markdown files",
            )
        }

        val scope = docsScopeResolver.resolve(project, filePath)
        val bypassStore = bypassStoreResolver(project)
        if (scope.status == DocsScopeStatus.DOCS_SCOPED &&
            bypassStore?.isBypassed(filePath, scope.configPath) == true
        ) {
            return PreviewDecision(
                target = PreviewRouteTarget.INTELLIJ,
                scopeStatus = scope.status,
                reason = PreviewRouteReason.FAILURE_BYPASS,
                configPath = scope.configPath,
                detail = "Startup failure bypass is active for this docs markdown",
            )
        }

        return when (scope.status) {
            DocsScopeStatus.DOCS_SCOPED -> PreviewDecision(
                target = PreviewRouteTarget.AUTHORD,
                scopeStatus = scope.status,
                reason = PreviewRouteReason.DOCS_SCOPED,
                configPath = scope.configPath,
                detail = scope.detail,
            )

            DocsScopeStatus.NON_DOCS -> PreviewDecision(
                target = PreviewRouteTarget.INTELLIJ,
                scopeStatus = scope.status,
                reason = PreviewRouteReason.NON_DOCS,
                configPath = scope.configPath,
                detail = scope.detail,
            )

            DocsScopeStatus.AMBIGUOUS -> PreviewDecision(
                target = PreviewRouteTarget.INTELLIJ,
                scopeStatus = scope.status,
                reason = PreviewRouteReason.AMBIGUOUS,
                configPath = scope.configPath,
                detail = scope.detail,
            )

            DocsScopeStatus.MISCONFIGURED -> PreviewDecision(
                target = PreviewRouteTarget.INTELLIJ,
                scopeStatus = scope.status,
                reason = PreviewRouteReason.MISCONFIGURED,
                configPath = scope.configPath,
                detail = scope.detail,
            )
        }
    }
}

