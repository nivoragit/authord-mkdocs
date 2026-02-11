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
