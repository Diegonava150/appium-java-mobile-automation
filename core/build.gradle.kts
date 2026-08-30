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

    // Executes a throwaway test class in-process so an extension can be asserted on the real
    // JUnit context hierarchy. DriverExtension has to find the *class* context to hang a
    // per-class session on, and the shape of that hierarchy differs between a plain @Test and a
    // retried @Flaky one — which is not something a hand-rolled fake would reproduce, since
    // getting it wrong is exactly the mistake being guarded against.
    testImplementation(libs.junit.platform.testkit)

    // Allure 2.35.4 is compiled against Jupiter 5.10.3 / Platform 1.10.3 while this build
    // runs JUnit 6. The integration is a JUnit Platform TestExecutionListener — a stable
    // SPI — so it is expected to work, but "expected to" is not "verified". Wiring it into
    // the device-free unit tests means every quality-gate run proves it, in seconds,
    // instead of the report silently coming up empty on a device job later. See ADR-005.
    testImplementation(libs.allure.jupiter)
}

// The probes are test classes on purpose: they exist to be run by EngineTestKit from inside a real
// test, which is the only way to assert an extension against JUnit's actual lifecycle. They must
// not also run as members of this suite. Gradle scans for test classes and selects them by name,
// which reaches these; one of them fails every time by design, and a suite that runs it is red for
// a reason that is not a defect.
tasks.named<Test>("test") {
    exclude("**/junit/probes/**")
}
