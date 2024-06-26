plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    alias(libs.plugins.sqldelight)
    application
}

group = "io.github.jeffset"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.cli)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.contentNegotiation)
    implementation(libs.ktor.serialization.xml)
    implementation(libs.ktor.serialization.json)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinxSerializationCsv)

    implementation(libs.sqldelight.sqlite)

    runtimeOnly(libs.slf4j.nop)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

sqldelight {
    databases {
        create("IndexDB") {
            packageName.set("io.github.jeffset.mindexer.data")
        }
    }
}

application {
    mainClass = "io.github.jeffset.mindexer.Main"
}
