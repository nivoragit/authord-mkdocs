import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import java.math.BigDecimal

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(project(":modules:infra-defaults"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
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
