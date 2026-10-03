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

rootProject.name = "Orbis"
include(":app")

// NOTE: The :orbis-core module (proprietary Nostr engine) is not included in this
// public build. The app/ module compiles standalone using structural stubs for
// NostrSyncManager and NostrProtocolEngine (see docs/stubs/).
// The production binary (orbis-core-release.aar) is distributed separately.