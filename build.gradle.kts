import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import java.math.BigDecimal

plugins {
    kotlin("jvm") version "1.9.24" apply false
    id("org.jetbrains.intellij") version "1.17.4" apply false
    jacoco
}

allprojects {
    group = "com.authord.mkdocs"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    if (project.path == ":modules") {
        return@subprojects
    }

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.2")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()

        if (project.path == ":modules:ui-plugin") {
            extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                includes = listOf("com.authord.mkdocs.*")
            }
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    tasks.named<Test>("test") {
        finalizedBy("jacocoTestReport", "jacocoTestCoverageVerification")
    }

    val testTaskProvider = tasks.named<Test>("test")

    fun hasFilteredTestSelection(): Boolean {
        val filter = testTaskProvider.get().filter
        val includesConfiguredInBuild = filter.includePatterns.isNotEmpty()
        val includesFromCommandLine = runCatching {
            @Suppress("UNCHECKED_CAST")
            val reflected = filter.javaClass
                .getMethod("getCommandLineIncludePatterns")
                .invoke(filter) as? Set<String>
            reflected?.isNotEmpty() == true
        }.getOrDefault(false)
        return includesConfiguredInBuild || includesFromCommandLine
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        // Skip report generation for filtered test runs (e.g., --tests "*Foo*"),
        // because report/verification on a partial suite is not representative.
        onlyIf {
            !hasFilteredTestSelection()
        }
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("test")
        // Keep strict 100% gate for full suite runs while allowing focused test commands.
        onlyIf {
            !hasFilteredTestSelection()
        }

        if (project.path != ":modules:ui-plugin") {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = BigDecimal("1.0")
                    }
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn("jacocoTestCoverageVerification")
    }
}
