import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.unbrokendome.gradle.plugins.testsets.dsl.TestSetContainer
import pl.droidsonroids.gradle.jacoco.testkit.GenerateJaCoCoTestKitProperties
import pl.droidsonroids.gradle.jacoco.testkit.JacocoTestKitExtension

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`

    kotlin("jvm")
    kotlin("kapt")

    id("com.github.johnrengelman.shadow")
    id("com.gradle.plugin-publish")
    id("org.unbroken-dome.test-sets")
    id("pl.droidsonroids.jacoco.testkit")
}

group = "io.github.meiblorn"

description =
    """Gradle plugin for requiring Docker containers 
    to be up and running before executing attached tasks
    """.trimIndent()

configurations {
    implementation { extendsFrom(shadow.get()) }
}

afterEvaluate {
    val shadow by configurations.getting
    with(shadow) {
        dependencies.remove(project.dependencies.gradleApi())
    }
}

dependencies {
    shadow("com.bmuschko:gradle-docker-plugin:9.1.0")

    afterEvaluate {
        val functionalTestImplementation: Configuration by configurations.getting
        functionalTestImplementation(gradleTestKit())
    }
}

configure<TestSetContainer> {
    val unitTest by getting
    val functionalTest by creating {
        extendsFrom(unitTest)
    }
}

configure<JacocoTestKitExtension> {
    val functionalTestRuntimeOnly by configurations.getting
    val functionalTest = tasks.named("functionalTest")
    applyTo(functionalTestRuntimeOnly.name, functionalTest)
}

val generateJacocoFunctionalTestKitProperties by tasks.getting(GenerateJaCoCoTestKitProperties::class) {

    val execFilePath =
        project.layout.buildDirectory
            .dir("jacoco")
            .map { it.file("functionalTestKit.exec") }
            .map { it.asFile }
            .map { it.path }

    destinationFile.set(execFilePath)
}

tasks.withType<JacocoReport> {
    executionData.setFrom(
        project.layout.buildDirectory
            .dir("jacoco")
            .map {
                it.asFileTree.matching {
                    include("*.exec")
                }
            }
    )
}

// NOTE: This is required to avoid printing of the following warning:
//
//     Execution optimizations have been disabled for task ':modules:plugin:functionalTest'
//     to ensure correctness due to the following reasons:

//     Gradle detected a problem with the following location: '/path/to/modules/plugin/build/testkit/test'.
//     Reason: Task ':modules:plugin:functionalTest' uses this output
//     of task ':modules:plugin:generateJacocoTestKitProperties' without declaring an
//     explicit or implicit dependency. This can lead to incorrect results being produced, depending on what
//     order the tasks are executed.
//
//     Please refer to https://docs.gradle.org/7.6/userguide/validation_problems.html#implicit_dependency
//     for more details about this problem.
//
val functionalTest by tasks.getting(Test::class) {
    val processorCount = Runtime.getRuntime().availableProcessors() / 4
    maxParallelForks = if (processorCount > 0) processorCount else 1
    dependsOn(tasks.withType<GenerateJaCoCoTestKitProperties>())
}

tasks.withType<Jar> {
    archiveBaseName.set("gradle-require-docker-plugin")
}

val shadowJar by tasks.getting(ShadowJar::class) {
    archiveClassifier.set("")
    configurations = listOf(project.configurations.shadow.get())
    exclude(
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/*.SF",
        "META-INF/CHANGELOG*",
        "META-INF/DEPENDENCIES*",
        "META-INF/INDEX.LIST",
        "META-INF/NOTICE*",
        "META-INF/README*",
        "migrations/*",
        "module-info.class",

        // Nothing personal, just don't want to keep it in the jar
        // while it's not used because of shadow minification
        "META-INF/gradle-plugins/com.bmuschko.*"
    )
    mergeServiceFiles()
    minimize()
}

val jar by tasks.getting(Jar::class) {
    enabled = false
    dependsOn(shadowJar)
}

val relocateShadowJar by tasks.creating(ConfigureShadowRelocation::class) {
    target = shadowJar
    prefix = "io.github.meiblorn.requiredocker.shaded"
}

shadowJar.dependsOn(relocateShadowJar)

gradlePlugin {
    val requireDocker by plugins.creating {
        id = "io.github.meiblorn.require-docker"
        implementationClass = "io.github.meiblorn.requiredocker.RequireDockerPlugin"
        version = project.version
        displayName = "Require Docker Plugin"
        description =
            """Gradle plugin to require Docker to be up 
                and running before executing attached tasks
            """.trimIndent()
    }
}

pluginBundle {
    website = "https://github.com/meiblorn/gradle-require-docker-plugin"
    vcsUrl = "https://github.com/meiblorn/gradle-require-docker-plugin"
    tags = listOf("docker", "require", "lifecycle")
}
