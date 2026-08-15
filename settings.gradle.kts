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
//
// The flag exists so that claim is checked rather than asserted. `-Pmobile.ai.absent=true`
// leaves the module out exactly as deleting this line would, and CI runs the whole quality
// gate that way on every push. Everywhere else in this repo an invariant that matters has a
// task enforcing it; "the AI layer is deletable" is the most load-bearing claim in ADR-009
// and had, until now, nothing behind it but a sentence.
if (providers.gradleProperty("mobile.ai.absent").orNull != "true") {
    include("ai")
}
