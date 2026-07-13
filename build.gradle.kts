plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    alias(libs.plugins.dependencyCheck)
    alias(libs.plugins.axionRelease)
    application
}

group = "pl.forestdev.common"

scmVersion {
    tag {
        initialVersion { _, _ -> "1.0.0"}
    }
}

version = scmVersion.version

repositories {
    mavenCentral()
    mavenLocal()
}

application {
    mainClass.set("pl.forestdev.nvd.ApplicationKt")
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.exposed)
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikaricp)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

ktor {
    docker {
        localImageName.set("nvd-local-server")
    }
}

tasks.test {
    useJUnitPlatform()
}
