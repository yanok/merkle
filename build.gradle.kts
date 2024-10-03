plugins {
    kotlin("jvm") version "2.0.20"
    distribution
}

group = "io.github.yanok"
version = "0.1"

repositories {
    mavenCentral()
}
dependencies {
    testImplementation(kotlin("test"))
    val kotlincryptoCore = "0.5.3"
    implementation(platform("org.kotlincrypto.hash:bom:$kotlincryptoCore"))
    implementation("org.kotlincrypto.hash:sha3")
    val kotestVersion = "5.9.1"
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}