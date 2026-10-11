plugins {
    `java-library`
}

description = "Portable Totipo primitives and protocol foundation"
base.archivesName.set("totipo-core")

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    // BC is used only for lightweight Argon2id; existing crypto stays on JDK providers.
    implementation(libs.bcprov)
    testImplementation(libs.jackson.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    // Exercise package-private orchestration against the real backend only in tests.
    testImplementation(project(":storage-nio"))
    testImplementation(testFixtures(project(":storage-nio")))
}
