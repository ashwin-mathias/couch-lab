pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SparkyIPTV"
// 3.0: shared code in core/, each app under apps/ (see docs/ARCHITECTURE.md §2).
include(":core")
include(":apps:android-tv")
