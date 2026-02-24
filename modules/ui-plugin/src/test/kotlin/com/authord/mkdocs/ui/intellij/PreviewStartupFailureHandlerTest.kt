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
}

