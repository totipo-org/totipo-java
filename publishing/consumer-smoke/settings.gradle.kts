rootProject.name = "totipo-maven-consumer-smoke"
// Remote verification deliberately has one repository and no staging fallback.
val remoteCentral = providers.gradleProperty("remoteCentral").isPresent
val pomOnly = providers.gradleProperty("pomOnly").isPresent
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (remoteCentral) {
            maven {
                name = "VerifiedMavenCentral"
                url = uri("https://repo.maven.apache.org/maven2/")
                content {
                    includeGroup("org.totipo")
                    includeModule("org.bouncycastle", "bcprov-jdk18on")
                }
                if (pomOnly) {
                    metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
                }
            }
        } else {
            exclusiveContent {
                forRepository { maven {
                    name = "TotipoStaging"
                    url = uri("../../build/repository")
                    if (pomOnly) {
                        metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
                    }
                } }
                filter { includeGroup("org.totipo") }
            }
            mavenCentral { content { includeModule("org.bouncycastle", "bcprov-jdk18on") } }
        }
    }
}
