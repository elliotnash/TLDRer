plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
}

group = "org.elliotnash"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0-RC")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("org.jetbrains.exposed:exposed-core:0.42.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.42.0")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")

    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    implementation("io.github.oshai:kotlin-logging-jvm:5.0.2")

    implementation("com.cjcrafter:openai:1.3.1")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    implementation("me.xdrop:fuzzywuzzy:1.4.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("TLDRerKt")
}
