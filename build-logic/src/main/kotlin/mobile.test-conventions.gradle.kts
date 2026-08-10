plugins {
    id("mobile.java-conventions")
}

/**
 * Forward every -Dmobile.* flag from the Gradle invocation into the test JVM, so
 * `./gradlew :tests:test -Dmobile.platform=ios` behaves the way anyone would expect.
 */
val mobileProperties = providers.systemPropertiesPrefixedBy("mobile.")

/**
 * Test-thread count tracks the device pool size. One worker per device, never more —
 * a thread without a device slot to lease would just block. See ADR-002.
 */
val deviceCount = providers.systemProperty("mobile.device.count").orElse("1")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    systemProperties(mobileProperties.get())

    // JUnit reads its own configuration parameters from system properties.
    systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    systemProperty("junit.jupiter.execution.parallel.mode.default", "same_thread")
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
    systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
    systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", deviceCount.get())

    systemProperty("allure.results.directory", layout.buildDirectory.dir("allure-results").get().asFile.absolutePath)

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    // Device-backed tests are never up-to-date: the device is external state.
    outputs.upToDateWhen { false }
}
