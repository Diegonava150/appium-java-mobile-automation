pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "mobile-framework"

include("core")
include("screens")
include("tests")

// Optional by construction. `core` declares the LocatorFallback interface; this module
// implements it and registers through ServiceLoader. Delete the line and the framework
// keeps working, minus the fallback. See ADR-009.
include("ai")
