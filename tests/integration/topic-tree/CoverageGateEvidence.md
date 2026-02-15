# Coverage Gate Evidence (Canonical Latest Run)

## Run Context

- Run ID: `20260215T041517Z`
- Timestamp (UTC): `2026-02-15T04:15:17Z`
- Branch: `001-mkdocs-topic-tree`
- Commit: `bc04d71e120531c40553f8d41b1be2667c5a97d5` (`bc04d71`)
- Canonical command log 1: `.tmp/gate-runs/20260215T041517Z_cmd1_scopedCoverageGate.log`
- Canonical command log 2: `.tmp/gate-runs/20260215T041517Z_cmd2_clean_test_jacoco.log`
- Stop policy: command 2 MUST run when command 1 passes.

## Command 1: scopedCoverageGate

Executed command:

```bash
./gradlew scopedCoverageGate --no-daemon --console=plain
```

Verbatim output:

```text
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by net.rubygrapefruit.platform.internal.NativeLibraryLoader in an unnamed module (file:/Users/madushika/.gradle/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/lib/native-platform-0.22-milestone-26.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :modules:core-domain:checkKotlinGradlePluginConfigurationErrors
> Task :modules:extension-ports:checkKotlinGradlePluginConfigurationErrors
> Task :modules:extension-ports:compileKotlin UP-TO-DATE
> Task :modules:extension-ports:compileJava NO-SOURCE
> Task :modules:extension-ports:processResources NO-SOURCE
> Task :modules:extension-ports:classes UP-TO-DATE
> Task :modules:extension-ports:jar UP-TO-DATE
> Task :modules:core-domain:compileKotlin UP-TO-DATE
> Task :modules:core-domain:compileJava NO-SOURCE
> Task :modules:core-domain:processResources NO-SOURCE
> Task :modules:core-domain:classes UP-TO-DATE
> Task :modules:core-domain:jar UP-TO-DATE
> Task :modules:core-domain:compileTestKotlin UP-TO-DATE
> Task :modules:core-domain:compileTestJava NO-SOURCE
> Task :modules:core-domain:processTestResources NO-SOURCE
> Task :modules:core-domain:testClasses UP-TO-DATE
> Task :modules:core-domain:test UP-TO-DATE
> Task :modules:core-domain:jacocoTestReport UP-TO-DATE
> Task :modules:core-domain:jacocoTestCoverageVerification UP-TO-DATE
> Task :modules:infra-defaults:checkKotlinGradlePluginConfigurationErrors
> Task :modules:infra-defaults:compileKotlin UP-TO-DATE
> Task :modules:infra-defaults:compileJava NO-SOURCE
> Task :modules:infra-defaults:processResources NO-SOURCE
> Task :modules:infra-defaults:classes UP-TO-DATE
> Task :modules:infra-defaults:jar UP-TO-DATE
> Task :modules:extension-ports:compileTestKotlin UP-TO-DATE
> Task :modules:extension-ports:compileTestJava NO-SOURCE
> Task :modules:extension-ports:processTestResources NO-SOURCE
> Task :modules:extension-ports:testClasses UP-TO-DATE
> Task :modules:extension-ports:test UP-TO-DATE
> Task :modules:extension-ports:jacocoTestReport UP-TO-DATE
> Task :modules:extension-ports:jacocoTestCoverageVerification UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:checkKotlinGradlePluginConfigurationErrors
> Task :modules:mkdocs-runtime-adapter:compileKotlin UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:compileJava NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:processResources NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:classes UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:jar UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:compileTestKotlin UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:compileTestJava NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:processTestResources NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:testClasses UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:test UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:jacocoTestReport UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:jacocoTestCoverageVerification UP-TO-DATE
> Task :modules:ui-plugin:checkKotlinGradlePluginConfigurationErrors
> Task :modules:ui-plugin:initializeIntelliJPlugin SKIPPED
> Task :modules:ui-plugin:patchPluginXml UP-TO-DATE

> Task :modules:ui-plugin:verifyPluginConfiguration
[gradle-intellij-plugin :modules:ui-plugin ui-plugin:modules:ui-plugin:verifyPluginConfiguration] The following plugin configuration issues were found:
- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib
See: https://jb.gg/intellij-platform-versions

> Task :modules:ui-plugin:compileKotlin UP-TO-DATE
> Task :modules:ui-plugin:compileJava NO-SOURCE
> Task :modules:ui-plugin:processResources UP-TO-DATE
> Task :modules:ui-plugin:classes UP-TO-DATE
> Task :modules:ui-plugin:instrumentCode SKIPPED
> Task :modules:ui-plugin:instrumentedJar UP-TO-DATE
> Task :modules:ui-plugin:jar UP-TO-DATE
> Task :modules:ui-plugin:compileTestKotlin UP-TO-DATE
> Task :modules:ui-plugin:compileTestJava NO-SOURCE
> Task :modules:ui-plugin:processTestResources NO-SOURCE
> Task :modules:ui-plugin:testClasses UP-TO-DATE
> Task :modules:ui-plugin:instrumentTestCode SKIPPED
> Task :modules:ui-plugin:classpathIndexCleanup
> Task :modules:ui-plugin:prepareTestingSandbox UP-TO-DATE
> Task :modules:ui-plugin:test UP-TO-DATE
> Task :modules:ui-plugin:jacocoTestReport UP-TO-DATE
> Task :modules:ui-plugin:jacocoTestCoverageVerification UP-TO-DATE
> Task :scopedCoverageGate UP-TO-DATE

BUILD SUCCESSFUL in 32s
37 actionable tasks: 7 executed, 30 up-to-date
```

Command 1 result: **PASS** (exit code `0`).

## Command 2: Full Quality Gate

Executed command:

```bash
GRADLE_USER_HOME=$PWD/.gradle-user ./gradlew clean test jacocoTestCoverageVerification --no-daemon --console=plain
```

Verbatim output:

```text
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by net.rubygrapefruit.platform.internal.NativeLibraryLoader in an unnamed module (file:/Users/madushika/projects/authord-mkdocs-plugin/.gradle-user/wrapper/dists/gradle-8.10.2-bin/a04bxjujx95o3nb99gddekhwo/gradle-8.10.2/lib/native-platform-0.22-milestone-26.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :modules:core-domain:clean
> Task :modules:extension-ports:clean
> Task :modules:infra-defaults:clean
> Task :modules:mkdocs-runtime-adapter:clean
> Task :modules:ui-plugin:clean
> Task :modules:core-domain:checkKotlinGradlePluginConfigurationErrors
> Task :modules:extension-ports:checkKotlinGradlePluginConfigurationErrors
> Task :modules:extension-ports:processResources NO-SOURCE
> Task :modules:core-domain:processResources NO-SOURCE
> Task :modules:core-domain:processTestResources NO-SOURCE
> Task :modules:infra-defaults:checkKotlinGradlePluginConfigurationErrors
> Task :modules:infra-defaults:processResources NO-SOURCE
> Task :modules:extension-ports:processTestResources NO-SOURCE
> Task :modules:infra-defaults:processTestResources NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:checkKotlinGradlePluginConfigurationErrors
> Task :modules:mkdocs-runtime-adapter:processResources NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:processTestResources NO-SOURCE
> Task :modules:ui-plugin:checkKotlinGradlePluginConfigurationErrors
> Task :modules:ui-plugin:initializeIntelliJPlugin
> Task :modules:ui-plugin:patchPluginXml

> Task :modules:ui-plugin:verifyPluginConfiguration
[gradle-intellij-plugin :modules:ui-plugin ui-plugin:modules:ui-plugin:verifyPluginConfiguration] The following plugin configuration issues were found:
- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib
See: https://jb.gg/intellij-platform-versions

> Task :modules:ui-plugin:processResources
> Task :modules:ui-plugin:processTestResources NO-SOURCE
> Task :modules:extension-ports:compileKotlin
> Task :modules:extension-ports:compileJava NO-SOURCE
> Task :modules:extension-ports:classes UP-TO-DATE
> Task :modules:extension-ports:jar
> Task :modules:infra-defaults:compileKotlin
> Task :modules:infra-defaults:compileJava NO-SOURCE
> Task :modules:infra-defaults:classes UP-TO-DATE
> Task :modules:infra-defaults:jar
> Task :modules:infra-defaults:compileTestKotlin
> Task :modules:infra-defaults:compileTestJava NO-SOURCE
> Task :modules:infra-defaults:testClasses UP-TO-DATE
> Task :modules:extension-ports:compileTestKotlin
> Task :modules:infra-defaults:test
> Task :modules:extension-ports:compileTestJava NO-SOURCE
> Task :modules:extension-ports:testClasses UP-TO-DATE
> Task :modules:core-domain:compileKotlin
> Task :modules:extension-ports:test
> Task :modules:core-domain:compileJava NO-SOURCE
> Task :modules:core-domain:classes UP-TO-DATE
> Task :modules:core-domain:jar

> Task :modules:mkdocs-runtime-adapter:compileKotlin
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/UvExecutableProvider.kt:151:50 'getter for nextTarEntry: TarArchiveEntry!' is deprecated. Deprecated in Java
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/mkdocs-runtime-adapter/src/main/kotlin/com/authord/mkdocs/runtime/UvExecutableProvider.kt:154:39 There is more than one label with such a name in this scope

> Task :modules:extension-ports:jacocoTestReport
> Task :modules:infra-defaults:jacocoTestReport
> Task :modules:mkdocs-runtime-adapter:compileJava NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:classes UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:jar
> Task :modules:extension-ports:jacocoTestCoverageVerification
> Task :modules:infra-defaults:jacocoTestCoverageVerification

> Task :modules:core-domain:compileTestKotlin
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/core-domain/src/test/kotlin/com/authord/mkdocs/core/topic/TopicTreeAggregateTest.kt:295:47 Unchecked cast: Any! to MutableMap<String, TopicNode>

> Task :modules:core-domain:compileTestJava NO-SOURCE
> Task :modules:core-domain:testClasses UP-TO-DATE
> Task :modules:mkdocs-runtime-adapter:compileTestKotlin
> Task :modules:core-domain:test
> Task :modules:core-domain:jacocoTestReport
> Task :modules:mkdocs-runtime-adapter:compileTestJava NO-SOURCE
> Task :modules:mkdocs-runtime-adapter:testClasses UP-TO-DATE
> Task :modules:ui-plugin:compileKotlin
> Task :modules:mkdocs-runtime-adapter:test
> Task :modules:mkdocs-runtime-adapter:jacocoTestReport
> Task :modules:core-domain:jacocoTestCoverageVerification
> Task :modules:mkdocs-runtime-adapter:jacocoTestCoverageVerification

> Task :modules:ui-plugin:compileKotlin
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt:337:9 Parameter 'visibleStartOffset' is never used
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt:338:9 Parameter 'visibleEndOffset' is never used
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt:590:49 'create(JBCefBrowser): JBCefJSQuery!' is deprecated. Deprecated in Java
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt:591:45 'create(JBCefBrowser): JBCefJSQuery!' is deprecated. Deprecated in Java
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/main/kotlin/com/authord/mkdocs/ui/intellij/MkdocsToolWindowFactory.kt:592:49 'create(JBCefBrowser): JBCefJSQuery!' is deprecated. Deprecated in Java

> Task :modules:ui-plugin:compileJava NO-SOURCE
> Task :modules:ui-plugin:classes
> Task :modules:ui-plugin:instrumentCode SKIPPED
> Task :modules:ui-plugin:instrumentedJar
> Task :modules:ui-plugin:jar
> Task :modules:ui-plugin:prepareTestingSandbox

> Task :modules:ui-plugin:compileTestKotlin
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/IntellijTestFixtures.kt:54:22 This declaration overrides deprecated member but not marked as deprecated itself. Please add @Deprecated annotation or suppress. See https://youtrack.jetbrains.com/issue/KT-47902 for details
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/IntellijTestFixtures.kt:74:22 This declaration overrides deprecated member but not marked as deprecated itself. Please add @Deprecated annotation or suppress. See https://youtrack.jetbrains.com/issue/KT-47902 for details
w: file:///Users/madushika/projects/authord-mkdocs-plugin/modules/ui-plugin/src/test/kotlin/com/authord/mkdocs/ui/intellij/IntellijTestFixtures.kt:122:44 Unchecked cast: Any? to Key<Any?>

> Task :modules:ui-plugin:compileTestJava NO-SOURCE
> Task :modules:ui-plugin:testClasses UP-TO-DATE
> Task :modules:ui-plugin:instrumentTestCode SKIPPED
> Task :modules:ui-plugin:classpathIndexCleanup

> Task :modules:ui-plugin:test
CompileCommand: exclude com/intellij/openapi/vfs/impl/FilePartNodeRoot.trieDescend bool exclude = true

> Task :modules:ui-plugin:jacocoTestReport
> Task :modules:ui-plugin:jacocoTestCoverageVerification

BUILD SUCCESSFUL in 3m 50s
47 actionable tasks: 47 executed
```

Command 2 result: **PASS** (exit code `0`).

## JaCoCo XML Counters (Canonical Run)

| Module | XML Path | INSTRUCTION | BRANCH | LINE | COMPLEXITY | METHOD | CLASS |
|--------|----------|-------------|--------|------|------------|--------|-------|
| `core-domain` | `modules/core-domain/build/reports/jacoco/test/jacocoTestReport.xml` | `missed=9 covered=2781` | `missed=18 covered=229` | `missed=0 covered=410` | `missed=19 covered=199` | `missed=1 covered=93` | `missed=0 covered=24` |
| `extension-ports` | `modules/extension-ports/build/reports/jacoco/test/jacocoTestReport.xml` | `missed=18 covered=1355` | `missed=0 covered=2` | `missed=0 covered=179` | `missed=6 covered=141` | `missed=6 covered=140` | `missed=0 covered=31` |
| `mkdocs-runtime-adapter` | `modules/mkdocs-runtime-adapter/build/reports/jacoco/test/jacocoTestReport.xml` | `missed=189 covered=4444` | `missed=69 covered=288` | `missed=0 covered=617` | `missed=68 covered=234` | `missed=0 covered=122` | `missed=0 covered=21` |
| `ui-plugin` | `modules/ui-plugin/build/reports/jacoco/test/jacocoTestReport.xml` | `missed=2804 covered=8196` | `missed=310 covered=326` | `missed=496 covered=1379` | `missed=319 covered=465` | `missed=95 covered=365` | `missed=28 covered=99` |

## Canonical Disposition (C1 / SC-005)

- C1 (coverage MUST gate): **PASS**
  - Evidence: command 1 (`./gradlew scopedCoverageGate --no-daemon --console=plain`) completed with `BUILD SUCCESSFUL` in `.tmp/gate-runs/20260215T041517Z_cmd1_scopedCoverageGate.log`.
- SC-005 (coverage gate release path): **PASS**
  - Evidence: both required canonical commands completed with `BUILD SUCCESSFUL` and JaCoCo XML counters are captured above.
- Blocker attribution: **none for canonical run `20260215T041517Z`**.

## Cross-File Consistency Pointers

- `specs/001-mkdocs-topic-tree/quickstart.md` section `2.1 Latest canonical coverage-gate evidence (2026-02-15)`.
- `specs/001-mkdocs-topic-tree/test-plan-traceability.md` section `4.1 Scoped Coverage Evidence Pointers (SC-005)`.
- `specs/001-mkdocs-topic-tree/test-plan-traceability.md` matrix rows for `R-18` and `SC-005`.
