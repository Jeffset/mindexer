import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.compilerPlugin.compose)
    alias(libs.plugins.kotlin.compilerPlugin.serialization)
    alias(libs.plugins.sqldelight)
}

group = "io.github.jeffset"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
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
    implementation(libs.sqldelight.coroutines)

    implementation(compose.desktop.currentOs)

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

compose.desktop {
    application {
        mainClass = "io.github.jeffset.mindexer.Main"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "mindexer"
            packageVersion = version.toString()
        }
    }
}

