plugins {
    id("mobile.test-conventions")
}

dependencies {
    testImplementation(platform(libs.junit.bom))

    testImplementation(project(":screens"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.allure.jupiter)

    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.logback.classic)

    // Runtime only, and inert unless -Dmobile.ai.locatorFallback=true is set with a key present.
    // On the runtime classpath rather than the compile one on purpose: no test can import an
    // AI type, so nothing here can come to depend on the layer being there. Delete the line and
    // the suite still compiles and still runs. See ADR-009.
    //
    // Guarded so `-Pmobile.ai.absent=true` genuinely removes the module rather than failing to
    // resolve it — which would prove nothing except that this line exists.
    if (findProject(":ai") != null) {
        testRuntimeOnly(project(":ai"))
    }
}

/**
 * Every test here needs a running device and a running Appium server, and it stays wired
 * into `check` — a project's check genuinely does include its tests, and pretending
 * otherwise is how suites rot.
 *
 * The emulator-free CI job therefore does not call `check`. It calls `qualityGate`, which
 * names exactly what it runs. Nothing is silently skipped in either direction.
 */
tasks.named<Test>("test") {
    description = "Device-backed parity tests. Requires an emulator/simulator and an Appium server."

    val platform = providers.systemProperty("mobile.platform").orElse("android").get()
    val appsDir = rootProject.layout.buildDirectory.dir("apps")

    // Point at the downloaded build unless the caller named their own. Someone testing a
    // local debug build should not have to fight a default.
    if (platform.equals("android", ignoreCase = true)) {
        dependsOn(":downloadAndroidApp", ":downloadAndroidPreviousApp")

        val currentApk = appsDir.get().file("MyDemoApp-1.3.0.apk").asFile.absolutePath
        val previousApk = appsDir.get().file("MyDemoApp-1.2.0.apk").asFile.absolutePath

        if (!providers.systemProperty("mobile.app.android").isPresent) {
            systemProperty("mobile.app.android", currentApk)
        }
        // Addressed separately from the `app` capability: the upgrade suite unsets that one so
        // the driver attaches instead of reinstalling, but still needs the new binary's path.
        systemProperty("mobile.app.android.current", currentApk)
        systemProperty("mobile.app.android.previous", previousApk)

        // Required by the upgrade suite, which starts a session with no APK for Appium to
        // read a manifest from. Verified against the installed package on 2026-08-10.
        systemProperty("mobile.app.package", "com.saucelabs.mydemoapp.rn")
    } else {
        dependsOn(":unpackIosApp")
        if (!providers.systemProperty("mobile.app.ios").isPresent) {
            systemProperty(
                "mobile.app.ios",
                appsDir.get().dir("ios").file("MyRNDemoApp.app").asFile.absolutePath,
            )
        }
        // AppLifecycle resolves the app by bundle id on iOS, the way it uses appPackage on
        // Android. Its absence only showed up on the first real simulator run, as
        // "mobile.app.bundleId is not set" — the Android path had been carrying the suite.
        systemProperty("mobile.app.bundleId", "com.saucelabs.mydemoapp.rn")
    }

    systemProperty(
        "mobile.artifacts.dir",
        layout.buildDirectory.dir("test-artifacts").get().asFile.absolutePath,
    )

    // Where the AI layer's shutdown hook writes the run's heals, and where checkLocatorDebt
    // looks for them. Under the root build directory because the task that reconciles them
    // against the committed accepted set lives in the root build.
    systemProperty(
        "mobile.ai.debtReport",
        rootProject.layout.buildDirectory.file("locator-debt-report.txt").get().asFile.absolutePath,
    )

    // finalizedBy, not dependsOn: the reconciliation has to happen even when the suite fails,
    // because a run that failed on a locator is exactly the run whose heals are worth reading.
    finalizedBy(":checkLocatorDebt")
}
