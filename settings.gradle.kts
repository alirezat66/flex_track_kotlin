pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "GoogleMavenCentralMirror"
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "GoogleMavenCentralMirror"
        }
        mavenCentral()
    }
}

rootProject.name = "flex-track-kotlin"
include(":flextrack")
include(":sample")
