import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File
import java.math.BigDecimal

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.13.1"
    jacoco
}

group = "com.authord.mkdocs"
version = "0.1.0"

val platformType = providers.gradleProperty("platformType").get()
val platformVersion = providers.gradleProperty("platformVersion").get()
val platformPlugins = providers.gradleProperty("platformPlugins")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: emptyList()
val platformBundledPlugins = providers.gradleProperty("platformBundledPlugins")
    .orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?: emptyList()
val platformArtifactId = when (platformType) {
    "IU" -> "ideaIU"
    "IC" -> "ideaIC"
    else -> null
}
val configuredLocalPlatformPath = providers.gradleProperty("platformLocalPath")
    .orNull
    ?.takeIf { it.isNotBlank() }
val cachedLocalPlatformPath = platformArtifactId
    ?.let { artifactId ->
        val versionRoot = File(
            gradle.gradleUserHomeDir,
            "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/$artifactId/$platformVersion",
        )
        versionRoot.listFiles()
            ?.map { File(it, "$artifactId-$platformVersion") }
            ?.firstOrNull { candidate -> candidate.isDirectory && File(candidate, "product-info.json").isFile }
            ?.absolutePath
    }
val localPlatformPath = configuredLocalPlatformPath ?: cachedLocalPlatformPath

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    intellijPlatform {
        if (localPlatformPath != null) {
            local(localPlatformPath)
        } else {
            create(platformType, platformVersion)
        }

        plugins(platformPlugins)

        bundledPlugins(platformBundledPlugins)

        testFramework(TestFrameworkType.Platform)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("sinceBuild").get()
            untilBuild = providers.gradleProperty("untilBuild")
                .orNull
                ?.takeIf { it.isNotBlank() }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Keep performance-threshold tests deterministic by avoiding cross-fork contention.
    maxParallelForks = 1

    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        includes = listOf("com.authord.mkdocs.*")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

val hasFilteredTestSelection = gradle.startParameter.taskRequests
    .asSequence()
    .flatMap { it.args.asSequence() }
    .any { arg -> arg == "--tests" }

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

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    enabled = !hasFilteredTestSelection
    classDirectories.setFrom(scopedCoverageClassDirectories)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("test")
    enabled = !hasFilteredTestSelection
    classDirectories.setFrom(scopedCoverageClassDirectories)
    violationRules {
        rule {
            element = "PACKAGE"
            excludes = listOf("com.authord.mkdocs.ui.intellij*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.9")
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
