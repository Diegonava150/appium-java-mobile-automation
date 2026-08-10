plugins {
    id("mobile.test-conventions")
}

dependencies {
    api(platform(libs.junit.bom))

    api(libs.appium.java.client)
    api(libs.selenium.java)
    api(libs.junit.jupiter.api)

    implementation(libs.slf4j.api)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    runtimeOnly(libs.logback.classic)

    // core's own tests are pure unit tests — no device, no Appium server. They are what
    // gives the emulator-free quality gate something real to assert on.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Allure 2.35.4 is compiled against Jupiter 5.10.3 / Platform 1.10.3 while this build
    // runs JUnit 6. The integration is a JUnit Platform TestExecutionListener — a stable
    // SPI — so it is expected to work, but "expected to" is not "verified". Wiring it into
    // the device-free unit tests means every quality-gate run proves it, in seconds,
    // instead of the report silently coming up empty on a device job later. See ADR-005.
    testImplementation(libs.allure.jupiter)
}
