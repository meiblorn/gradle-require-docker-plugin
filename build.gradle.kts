import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.TestLoggerPlugin
import com.adarshr.gradle.testlogger.theme.ThemeType.STANDARD
import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.gradle.spotless.SpotlessPlugin
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.0" apply false
    kotlin("kapt") version "1.7.10" apply false
    id("com.adarshr.test-logger") version "3.2.0" apply false
    id("com.diffplug.spotless") version "6.12.0"
    id("com.github.johnrengelman.shadow") version "7.1.2" apply false
    id("com.gradle.plugin-publish") version "1.1.0" apply false
    id("org.unbroken-dome.test-sets") version "4.0.0" apply false
    id("pl.droidsonroids.jacoco.testkit") version "1.0.9" apply false
}

allprojects {
    apply<JacocoPlugin>()
    apply<SpotlessPlugin>()

    repositories {
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2") }
    }

    configurations {
        all {
            resolutionStrategy {
                eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion("1.7.10")
                    }
                }
            }
        }
    }

    tasks.withType<JacocoReport> {
        reports {
            with(xml) {
                required.set(true)
            }
            with(html) {
                required.set(true)
            }
        }
        setDependsOn(tasks.withType<Test>())
    }

    afterEvaluate {
        if (plugins.hasPlugin(JavaPlugin::class)) {
            apply<TestLoggerPlugin>()

            configure<JavaPluginExtension> {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(11))
                }
            }

            dependencies {
                val testImplementation: Configuration by configurations.getting

                testImplementation("io.kotest:kotest-assertions-core:5.5.4")
                testImplementation("io.kotest:kotest-property:5.5.4")
                testImplementation("io.kotest:kotest-runner-junit5:5.5.4")
            }

            tasks.withType<Test> {
                useJUnitPlatform()
                finalizedBy(tasks.withType<JacocoReport>())
            }

            configure<TestLoggerExtension> {
                theme = STANDARD
                showExceptions = true
                showStackTraces = true
                showFullStackTraces = true
                showCauses = true
                slowThreshold = 2000
                showSummary = true
                showSimpleNames = false
                showPassed = true
                showSkipped = true
                showFailed = true
                showOnlySlow = false
                showStandardStreams = true
                showPassedStandardStreams = false
                showSkippedStandardStreams = false
                showFailedStandardStreams = true
                logLevel = LIFECYCLE
            }
        }

        if (plugins.hasPlugin(KotlinPluginWrapper::class)) {
            tasks.withType<KotlinCompile> {
                kotlinOptions {
                    jvmTarget = "11"
                    freeCompilerArgs = listOf("-Xjsr305=strict")
                }
            }

            configure<SourceSetContainer> {
                all {
                    java {
                        // https://arturdryomov.dev/posts/kotlin-code-organization/
                        srcDirs("src/${this@all.name}/kotlinExtensions")
                    }
                }
            }

            configure<SpotlessExtension> {
                kotlin {
                    target(
                        fileTree("${projectDir.path}/src") {
                            include("**/*.kt")
                        }
                    )

                    ktfmt().dropboxStyle().configure {
                        it.setMaxWidth(100)
                        it.setBlockIndent(4)
                        it.setContinuationIndent(4)
                        it.setRemoveUnusedImport(true)
                    }
                }
            }
        }
    }

    configure<SpotlessExtension> {
        kotlinGradle {
            ktlint()
        }
    }
}

afterEvaluate {
    val jacocoTestReport by tasks.registering(JacocoReport::class) {
        group = "verification"
        description = "Generate Jacoco coverage reports for all subprojects."

        val subprojectTasks = subprojects
            .filter { it.plugins.hasPlugin(JacocoPlugin::class) }
            .flatMap { it.tasks.withType<JacocoReport>() }

        additionalSourceDirs.setFrom(subprojectTasks.flatMap { it.additionalSourceDirs })
        sourceDirectories.setFrom(subprojectTasks.flatMap { it.sourceDirectories })
        classDirectories.setFrom(subprojectTasks.flatMap { it.classDirectories })
        executionData.setFrom(subprojectTasks.flatMap { it.executionData })

        setDependsOn(subprojects.map { it.tasks.withType<Test>() })
    }
}
