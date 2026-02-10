import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    application
}

group = "com.media3watch"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val ktorVersion = "3.4.0"
val logbackVersion = "1.5.27"
val prometheusVersion = "1.16.2"
val hikariVersion = "7.0.2"
val flywayVersion = "12.0.0"
val postgresVersion = "42.7.7"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.media3watch.ApplicationKt")
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Main-Class"] = "com.media3watch.ApplicationKt"
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    android.set(false)
    version.set("1.5.0")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/detekt.yml"))
}
