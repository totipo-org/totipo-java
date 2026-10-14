import java.io.DataInputStream
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.JavadocMemberLevel
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.plugins.signing.Sign

plugins {
    base
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
}

description = "Totipo Java libraries"

val versionText = providers.fileContents(layout.projectDirectory.file("VERSION")).asText.orNull
    ?: error("VERSION is required")
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?\n").matches(versionText)) {
    "VERSION must contain major.minor.patch, an optional Maven qualifier, and one final newline"
}
version = versionText.removeSuffix("\n")
val centralRelease = providers.gradleProperty("centralRelease").map { it.toBooleanStrict() }.getOrElse(false)
val centralUpload = providers.gradleProperty("centralUpload").map { it.toBooleanStrict() }.getOrElse(false)
require(!centralUpload || centralRelease) { "Central upload requires explicit Central mode" }
require(!centralRelease || gradle.startParameter.taskNames.none { it.substringAfterLast(':') == "publish" }) {
    "Use an explicit Central task with -PcentralRelease=true, never generic publish"
}

if (centralRelease) tasks.register<Exec>("verifyCentralSigningInputs") {
    group = "verification"
    dependsOn(":core:signMavenPublication", ":storage-nio:signMavenPublication")
    val frozen = centralUpload
    val executable = providers.gradleProperty("publicationTool")
        .orElse(providers.environmentVariable("TOTIPO_PUBLICATION_TOOL")).getOrElse("__missing_totipo_publication_tool__")
    commandLine(executable, "release", "signed", rootDir.absolutePath,
        layout.buildDirectory.dir("repository").get().asFile.absolutePath,
        layout.buildDirectory.file("publication-manifest.json").get().asFile.absolutePath)
    doFirst {
        check(frozen && executable != "__missing_totipo_publication_tool__") { "Signing-input acceptance failed: frozen inputs and standalone launcher required" }
    }
}

subprojects {
    val freezePublicationInputs = centralUpload
    apply(plugin = "java-library")
    group = "org.totipo"
    version = rootProject.version
    apply(plugin = "com.vanniktech.maven.publish")
    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates("org.totipo", "totipo-${project.name}", project.version.toString())
        // Archives already belong to the Java component; avoid duplicate plugin tasks/fixtures sources.
        configure(com.vanniktech.maven.publish.JavaLibrary(
            javadocJar = com.vanniktech.maven.publish.JavadocJar.None(),
            sourcesJar = com.vanniktech.maven.publish.SourcesJar.None(),
        ))
        if (centralRelease) {
            publishToMavenCentral(automaticRelease = false)
            signAllPublications()
        }
        pom {
            name.set(if (project.name == "core") "Totipo Java Core" else "Totipo Java NIO Storage")
            description.set(if (project.name == "core") "Portable Totipo protocol/application API and core implementation" else "Filesystem/NIO entry point and storage provider for Totipo, exposing core transitively")
            url.set("https://github.com/totipo-org/totipo-java")
            licenses { license {
                name.set("Apache License 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            } }
            developers { developer {
                id.set("ingon")
                name.set("Nikolay Petrov")
                url.set("https://github.com/ingon/")
            } }
            scm {
                url.set("https://github.com/totipo-org/totipo-java")
                connection.set("scm:git:https://github.com/totipo-org/totipo-java.git")
                developerConnection.set("scm:git:ssh://git@github.com/totipo-org/totipo-java.git")
            }
        }
    }
    if (!centralRelease) {
        extensions.configure<PublishingExtension> {
            repositories { maven {
                name = "Staging"
                url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
            } }
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
        withJavadocJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        // The implementation still intentionally keeps most APIs package-private.
        (options as StandardJavadocDocletOptions).memberLevel = JavadocMemberLevel.PACKAGE
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // The upload invocation must reuse the already accepted signing inputs. If any
    // producer is scheduled again, skip it before it can replace those inputs.
    // One shared check reauthenticates all ten originals before either module's
    // Maven publisher copies signatures into its local Central staging tree.
    tasks.configureEach {
        if (this is JavaCompile || this is Javadoc || this is AbstractArchiveTask
            || this is GenerateMavenPom || this is GenerateModuleMetadata || this is Sign) {
            onlyIf("Accepted publication inputs must not rebuild during upload") { !freezePublicationInputs }
        }
    }
    tasks.withType<PublishToMavenRepository>().configureEach {
        if (name.endsWith("ToMavenCentralRepository")) {
            dependsOn(rootProject.tasks.named("verifyCentralSigningInputs"))
            doFirst { check(freezePublicationInputs) { "Central publication requires frozen accepted signing inputs" } }
        }
    }

    // Inspect every production class, so an accidental target increase fails `check`.
    val moduleName = project.name
    val mainClasses = extensions.getByType<JavaPluginExtension>().sourceSets.named("main").map { it.output.classesDirs }
    val verifyJava17Bytecode = tasks.register("verifyJava17Bytecode") {
        group = "verification"
        description = "Verify all ${moduleName} production classes use Java 17 bytecode"
        dependsOn("compileJava")
        inputs.files(mainClasses)
        doLast {
            val classes = inputs.files.asFileTree.matching { include("**/*.class") }.files
            check(classes.isNotEmpty()) { "No ${moduleName} classes to verify" }
            classes.forEach { file ->
                DataInputStream(file.inputStream()).use { input ->
                    check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid class: $file" }
                    check(input.readUnsignedShort() == 0) { "Preview bytecode: $file" }
                    check(input.readUnsignedShort() == 61) { "Not Java 17 bytecode: $file" }
                }
            }
        }
    }
    tasks.named("check") { dependsOn(verifyJava17Bytecode) }
    tasks.named("test") { dependsOn(verifyJava17Bytecode) }

    dependencyLocking {
        lockAllConfigurations()
        lockMode.set(LockMode.STRICT)
    }
}

tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }
tasks.named("assemble") { dependsOn(subprojects.map { "${it.path}:assemble" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }
tasks.register("test") { dependsOn(subprojects.map { "${it.path}:test" }) }

// Prebuilt standalone verifier; compilation never depends on Gradle or production classes.
tasks.register<Exec>("verifyPublication") {
    group = "verification"
    dependsOn(subprojects.map { "${it.path}:publishAllPublicationsToStagingRepository" })
    val tool = providers.gradleProperty("publicationTool").orElse(providers.environmentVariable("TOTIPO_PUBLICATION_TOOL"))
    val executable = tool.getOrElse("__missing_totipo_publication_tool__")
    commandLine(executable, "publication", "verify", rootDir.absolutePath,
        layout.buildDirectory.dir("repository").get().asFile.absolutePath,
        layout.buildDirectory.file("publication-manifest.json").get().asFile.absolutePath,
        layout.projectDirectory.file("publishing/consumer-smoke/gradle/verification-metadata.xml").asFile.absolutePath)
    doFirst {
        check(executable != "__missing_totipo_publication_tool__") { "Set TOTIPO_PUBLICATION_TOOL to the prebuilt standalone Java launcher (nix build .#tooling)" }
    }
}
// Deliberately separate Gradle build, with no composite/source substitution.
tasks.register<Exec>("consumerSmoke") {
    group = "verification"
    dependsOn("verifyPublication")
    // Explicit runtime path in CI/release; the Nix devShell supplies local PATH.
    val executable = providers.gradleProperty("consumerGradleExecutable")
        .orElse(providers.environmentVariable("TOTIPO_GRADLE")).getOrElse("gradle")
    commandLine(executable, "-p", "publishing/consumer-smoke", "--offline",
        "--no-daemon", "--no-build-cache", "--rerun-tasks",
        "--dependency-verification=strict", "clean", "check")
}
