plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

group = "pl.forestdev.common"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.bundles.exposed)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
    testImplementation("io.kotest:kotest-assertions-core:5.9.0")
    testImplementation("io.kotest:kotest-property:5.9.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
