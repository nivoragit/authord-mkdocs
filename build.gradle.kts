import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
    jacoco
}

group = "com.authord.mkdocs"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("org.yaml:snakeyaml:2.2")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

intellij {
    version.set(providers.gradleProperty("platformVersion"))
    type.set(providers.gradleProperty("platformType"))
    plugins.set(
        providers.gradleProperty("platformPlugins").map { raw ->
            raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        },
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
    options.release.set(17)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
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
