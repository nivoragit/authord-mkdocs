import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

plugins {
    id("org.jetbrains.intellij")
}

dependencies {
    implementation(project(":modules:core-domain"))
    implementation(project(":modules:mkdocs-runtime-adapter"))
    implementation(project(":modules:extension-ports"))
    implementation(project(":modules:infra-defaults"))

    testImplementation(project(":modules:core-domain"))
    testImplementation(project(":modules:mkdocs-runtime-adapter"))
    testImplementation(project(":modules:extension-ports"))
    testImplementation(project(":modules:infra-defaults"))
}

kotlin {
    sourceSets.getByName("test").kotlin.srcDirs(
        "../../tests/integration",
        "../../tests/contract"
    )
}

intellij {
    version.set(providers.gradleProperty("platformVersion"))
    type.set(providers.gradleProperty("platformType"))
    plugins.set(
        providers.gradleProperty("platformPlugins").map { raw ->
            raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    )
}

tasks {
    patchPluginXml {
        sinceBuild.set(providers.gradleProperty("sinceBuild"))
        untilBuild.set(providers.gradleProperty("untilBuild"))
    }

    instrumentCode {
        enabled = false
    }

    instrumentTestCode {
        enabled = false
    }
}

/**
 * Explicitly scopes strict coverage to unit-testable production classes for the plugin shell cycle.
 * Platform-bound rendering and JVM-runtime wrapper classes are validated via behavior tests but
 * excluded from the 100% unit coverage gate because they rely on IDE runtime internals.
 */
val coverageExcludes = listOf(
    "com/authord/mkdocs/ui/intellij/PreviewContent*.class",
    "com/authord/mkdocs/ui/intellij/JcefPreviewContent*.class",
    "com/authord/mkdocs/ui/intellij/HtmlPreviewContent*.class",
    "com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactoryKt.class",
    "com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory\$*.class",
    "com/authord/mkdocs/ui/intellij/ProcessBuilderSystemProcessFactory*.class",
    "com/authord/mkdocs/ui/intellij/RuntimeShutdownHookRegistrar*.class",
    "com/authord/mkdocs/ui/intellij/SystemRuntimeAdaptersKt.class",
    "com/authord/mkdocs/ui/intellij/RuntimeIntegrationDependencies\$Companion.class",
)

val scopedCoverageClassDirectories = files(
    fileTree("${layout.buildDirectory.asFile.get()}/classes/kotlin/main") {
        exclude(coverageExcludes)
    },
    fileTree("${layout.buildDirectory.asFile.get()}/classes/java/main") {
        exclude(coverageExcludes)
    },
)

tasks.withType<JacocoReport>().configureEach {
    classDirectories.setFrom(scopedCoverageClassDirectories)
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    classDirectories.setFrom(scopedCoverageClassDirectories)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "com.authord.mkdocs.ui.*",
                "com.authord.mkdocs.ui.intellij.StartMkdocsAction*",
                "com.authord.mkdocs.ui.intellij.MkdocsToolWindowFactory",
                "com.authord.mkdocs.ui.intellij.InMemory*",
                "com.authord.mkdocs.ui.intellij.RuntimeIntegrationDependencies",
                "com.authord.mkdocs.ui.intellij.ProjectUserDataStartupOutputProvider*",
                "com.authord.mkdocs.ui.intellij.PreviewResultReporterKt",
                "com.authord.mkdocs.ui.intellij.SystemProcessFactory",
                "com.authord.mkdocs.ui.intellij.ShutdownHookRegistrar",
                "com.authord.mkdocs.ui.intellij.StartupOutputProvider",
                "com.authord.mkdocs.ui.intellij.ActionPresentationState",
            )
            excludes = listOf(
                "com.authord.mkdocs.ui.intellij.PreviewContent*",
                "com.authord.mkdocs.ui.intellij.JcefPreviewContent*",
                "com.authord.mkdocs.ui.intellij.HtmlPreviewContent*",
                "com.authord.mkdocs.ui.intellij.MkdocsToolWindowFactory*",
                "com.authord.mkdocs.ui.intellij.RuntimeShutdownHookRegistrar*",
                "com.authord.mkdocs.ui.intellij.ProcessBuilderSystemProcessFactory*",
                "com.authord.mkdocs.ui.intellij.SystemRuntimeAdaptersKt",
                "com.authord.mkdocs.ui.intellij.RuntimeIntegrationDependencies\$Companion",
                "com.authord.mkdocs.ui.PluginActivationService",
                "com.authord.mkdocs.ui.intellij.PluginRuntimeIntegrationService*",
                "com.authord.mkdocs.ui.intellij.ProcessBuilderCommandRunner",
                "com.authord.mkdocs.ui.intellij.ProcessBuilderProcessLauncher*",
                "com.authord.mkdocs.ui.intellij.ProcessBackedManagedProcessHandle",
            )
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal("1.0")
            }
        }
    }
}
