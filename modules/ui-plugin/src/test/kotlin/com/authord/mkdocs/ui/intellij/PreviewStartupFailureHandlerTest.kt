package com.authord.mkdocs.ui.intellij

import com.authord.mkdocs.ui.ActivationFailureReason
import com.authord.mkdocs.ui.ActivationResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewStartupFailureHandlerTest {
    @Test
    fun `startup failure applies bypass and triggers fallback for docs scoped markdown`() {
        val projectRoot = Files.createTempDirectory("startup-failure-handler-bypass")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val selectedPath = projectRoot.resolve("docs/index.md").toString()
            val bypassStore = FailureBypassStore()
            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "startup-failure-handler-bypass",
                services = mapOf(FailureBypassStore::class.java to bypassStore),
            )
            var fallbackInvocations = 0
            var capturedRetry: (() -> Unit)? = null
            val notifier = PreviewFailureNotifier(
                emit = { _, payload -> capturedRetry = payload.onRetry },
            )
            val handler = PreviewStartupFailureHandler(
                routerResolver = { PreviewRouterService() },
                bypassStoreResolver = { bypassStore },
                notifier = notifier,
                fallbackInvoker = { _, _ -> fallbackInvocations += 1 },
            )

            handler.handleFailure(
                project = project,
                result = ActivationResult(
                    success = false,
                    reason = ActivationFailureReason.START_FAILED,
                    message = "Authord process exited before readiness probe succeeded.",
                ),
                selectedPath = selectedPath,
                onRetry = {},
            )

            val decision = PreviewRouterService().decide(project, selectedPath)
            assertTrue(bypassStore.isBypassed(selectedPath, decision.configPath))
            assertEquals(1, fallbackInvocations)
            assertTrue(capturedRetry != null)
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `retry action clears bypass before retrying`() {
        val projectRoot = Files.createTempDirectory("startup-failure-handler-retry")
        try {
            Files.createDirectories(projectRoot.resolve("docs"))
            Files.writeString(projectRoot.resolve("mkdocs.yml"), "site_name: Demo\ndocs_dir: docs\n")
            val selectedPath = projectRoot.resolve("docs/index.md").toString()
            val bypassStore = FailureBypassStore()
            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "startup-failure-handler-retry",
                services = mapOf(FailureBypassStore::class.java to bypassStore),
            )
            var retryInvoked = false
            var capturedRetry: (() -> Unit)? = null
            val notifier = PreviewFailureNotifier(
                emit = { _, payload -> capturedRetry = payload.onRetry },
            )
            val handler = PreviewStartupFailureHandler(
                routerResolver = { PreviewRouterService() },
                bypassStoreResolver = { bypassStore },
                notifier = notifier,
                fallbackInvoker = { _, _ -> Unit },
            )

            handler.handleFailure(
                project = project,
                result = ActivationResult(
                    success = false,
                    reason = ActivationFailureReason.START_FAILED,
                    message = "Authord process exited before readiness probe succeeded.",
                ),
                selectedPath = selectedPath,
                onRetry = { retryInvoked = true },
            )

            val decision = PreviewRouterService().decide(project, selectedPath)
            assertTrue(bypassStore.isBypassed(selectedPath, decision.configPath))
            capturedRetry?.invoke()
            assertTrue(retryInvoked)
            assertTrue(!bypassStore.isBypassed(selectedPath, decision.configPath))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }

    @Test
    fun `creates empty requirements txt in scoped config directory before dependency notification`() {
        val projectRoot = Files.createTempDirectory("startup-failure-handler-scaffold-requirements")
        try {
            val docsSiteRoot = projectRoot.resolve("docs-site")
            Files.createDirectories(docsSiteRoot.resolve("docs"))
            Files.writeString(
                docsSiteRoot.resolve("mkdocs.yml"),
                "site_name: Demo\ndocs_dir: docs\n",
            )
            val selectedPath = docsSiteRoot.resolve("docs/index.md").toString()
            Files.writeString(docsSiteRoot.resolve("docs/index.md"), "# Demo\n")

            val bypassStore = FailureBypassStore()
            val project = IntellijTestFixtures.project(
                basePath = projectRoot.toString(),
                locationHash = "startup-failure-handler-scaffold",
                services = mapOf(FailureBypassStore::class.java to bypassStore),
            )
            var capturedPayload: PreviewFailureNotificationPayload? = null
            val notifier = PreviewFailureNotifier(
                emit = { _, payload -> capturedPayload = payload },
            )
            val handler = PreviewStartupFailureHandler(
                routerResolver = { PreviewRouterService() },
                bypassStoreResolver = { bypassStore },
                notifier = notifier,
                fallbackInvoker = { _, _ -> Unit },
            )

            handler.handleFailure(
                project = project,
                result = ActivationResult(
                    success = false,
                    reason = ActivationFailureReason.START_FAILED,
                    message = "ModuleNotFoundError: No module named 'material'",
                ),
                selectedPath = selectedPath,
                onRetry = {},
            )

            val requirementsPath = docsSiteRoot.resolve("requirements.txt")
            assertTrue(Files.exists(requirementsPath))
            assertEquals(0L, Files.size(requirementsPath))
            assertTrue(capturedPayload != null)
            val dependencyHint = capturedPayload!!.failure.dependencyDeclarationHint.orEmpty()
            assertTrue(dependencyHint.contains("Created empty requirements.txt"))
            assertTrue(dependencyHint.contains("restart preview", ignoreCase = true))
        } finally {
            projectRoot.toFile().deleteRecursively()
        }
    }
}
