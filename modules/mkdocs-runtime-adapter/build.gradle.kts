import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import java.math.BigDecimal

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.yaml:snakeyaml:2.2")
    implementation(project(":modules:extension-ports"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal("0.9")
            }
        }
    }
}
