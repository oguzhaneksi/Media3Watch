pluginManagement {
    repositories {
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Media3Watch-Android"
include(":sample")
include(":sdk:android-runtime")
include(":sdk:adapter-media3")
include(":sdk:transport-okhttp")
include(":sdk:inspector-overlay")
include(":sdk:starter-media3")
include(":sdk:core")
include(":sdk:schema")
