plugins {
    `java-library`
    `java-test-fixtures`
}

description = "Portable NIO configured-store filesystem implementation for Totipo"
base.archivesName.set("totipo-storage-nio")

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    api(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val snapshot = rootProject.layout.projectDirectory.dir("core/src/test/resources/totipo-spec/v1-pre-rc")
tasks.test {
    inputs.dir(snapshot)
    systemProperty("totipo.test.snapshot", snapshot.asFile.absolutePath)
}

// Preserve internal fixture consumers while excluding fixtures from publication.
(components["java"] as AdhocComponentWithVariants).apply {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}
