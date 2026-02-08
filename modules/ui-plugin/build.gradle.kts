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
