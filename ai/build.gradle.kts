plugins {
    id("mobile.test-conventions")
}

dependencies {
    // Implementation, not api: nothing downstream should be able to see com.anthropic.*
    // types. Everything this module offers is reached through core's LocatorFallback SPI.
    implementation(project(":core"))
    implementation(libs.anthropic.java)
    implementation(libs.slf4j.api)

    // The SDK pulls Jackson in transitively, but the structured-output record is annotated
    // directly — so declare it rather than relying on someone else's dependency graph.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)
}

/**
 * These are ordinary unit tests — no device, no API key, no network. Everything that decides
 * what the model is asked and what is done with its answer is testable that way; only the HTTP
 * call in the middle is not, and that part is deliberately thin.
 *
 * Tests tagged `ai` do make live calls and are excluded unless a key is present. See ADR-009.
 */
tasks.named<Test>("test") {
    description = "Unit tests for the AI layer. No API key required."
    useJUnitPlatform {
        if (System.getenv("ANTHROPIC_API_KEY").isNullOrBlank()) {
            excludeTags("ai")
        }
    }
}
