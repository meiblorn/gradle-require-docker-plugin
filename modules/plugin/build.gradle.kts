import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.unbrokendome.gradle.plugins.testsets.dsl.TestSetContainer

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`

    kotlin("jvm")
    kotlin("kapt")

    id("com.diffplug.spotless")
    id("com.github.johnrengelman.shadow")
    id("com.gradle.plugin-publish")
    id("org.unbroken-dome.test-sets")
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
    shadow("com.bmuschko:gradle-docker-plugin:9.0.1")

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

configure<SpotlessExtension> {
    kotlin {
        target("src/main/kotlin/**/*.kt")
        target("src/main/kotlinExtensions/**/*.kt")

        ktfmt().dropboxStyle().configure {
            it.setMaxWidth(120)
            it.setBlockIndent(4)
            it.setContinuationIndent(4)
            it.setRemoveUnusedImport(true)
        }
    }
    kotlinGradle {
        ktlint()
    }
}