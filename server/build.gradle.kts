import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.vibepizzaiolo"
version = "0.1.0"

application { mainClass.set("com.vibepizzaiolo.server.MainKt") }

// the community's data classes are shared with the app
sourceSets["main"].kotlin.srcDir("../shared/src")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    val ktor = "3.0.3"
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-rate-limit:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    // without DATABASE_URL the server keeps its data in an H2 file, so it runs with nothing installed
    implementation("com.h2database:h2:2.3.232")
    implementation("com.password4j:password4j:1.8.2")

    // looks at a posted photo and names the pizza
    implementation("com.anthropic:anthropic-java:3.3.0")

    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation(kotlin("test-junit"))
}
