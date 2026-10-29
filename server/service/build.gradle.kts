plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("moe.archivetune.server.MainKt")
    applicationDefaultJvmArgs = listOf("-XX:MaxRAMPercentage=60", "-Dfile.encoding=UTF-8")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation("org.json:json:20240303")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
    testImplementation(libs.junit)
}

configurations.all {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
}

tasks.withType<Sync>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
