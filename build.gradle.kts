plugins {
    `java-library`
    jacoco
    alias(libs.plugins.run.paper)
    alias(libs.plugins.spotless)
}

group = "io.github.xreatlabz"
version = "0.1.0-SNAPSHOT"
description = "Minecraft practice core plugin for Modern Paper 1.21.11"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    compileOnly(libs.paper.api)

    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.sqlite.jdbc)

    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

spotless {
    java {
        target("src/**/*.java")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target(
            "*.md",
            ".gitignore",
            ".github/**/*.yml",
            ".github/**/*.yaml",
            "docs/**/*.md",
            "gradle/**/*.toml",
            "scripts/**/*.sh",
            "src/main/resources/**/*.yml",
            "src/main/resources/**/*.yaml",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jar {
    archiveBaseName.set("RevPrac")
}

tasks.runServer {
    minecraftVersion("1.21.11")
}
