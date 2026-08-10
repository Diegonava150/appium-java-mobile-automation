import java.net.URI

plugins {
    base
}

description = "Cross-platform mobile test automation framework — Appium 3 + Java 21"

// ---------------------------------------------------------------------------
// App under test
// ---------------------------------------------------------------------------

/**
 * Sauce Labs' My Demo App (React Native). Downloaded, never committed.
 *
 * <p>A 30 MB binary in git is a permanent tax on every clone of the repository, and it goes
 * stale the moment upstream cuts a release. Two releases are wired up because week two's
 * upgrade suite installs 1.2.0, seeds state, then upgrades to 1.3.0 in place.
 */
val autCurrentVersion = "1.3.0"
val autPreviousVersion = "1.2.0"

val autReleases = mapOf(
    "androidCurrent" to Triple(
        "v$autCurrentVersion",
        "Android-MyDemoAppRN.1.3.0.build-244.apk",
        "MyDemoApp-$autCurrentVersion.apk",
    ),
    "androidPrevious" to Triple(
        "v$autPreviousVersion",
        "Android-MyDemoAppRN.1.2.0.build-231.apk",
        "MyDemoApp-$autPreviousVersion.apk",
    ),
    "iosSimulator" to Triple(
        "v$autCurrentVersion",
        "iOS-Simulator-MyRNDemoApp.1.3.0-162.zip",
        "MyDemoApp-$autCurrentVersion-sim.zip",
    ),
)

val appsDir = layout.buildDirectory.dir("apps")

fun registerDownload(taskName: String, key: String, taskDescription: String) =
    tasks.register(taskName) {
        group = "app under test"
        description = taskDescription

        val (tag, assetName, localName) = autReleases.getValue(key)
        val target = appsDir.map { it.file(localName) }
        outputs.file(target)

        doLast {
            val file = target.get().asFile
            if (file.exists() && file.length() > 0) {
                logger.lifecycle("$localName already present (${file.length() / 1024 / 1024} MB)")
                return@doLast
            }
            val url = "https://github.com/saucelabs/my-demo-app-rn/releases/download/$tag/$assetName"
            logger.lifecycle("Downloading $assetName …")
            file.parentFile.mkdirs()
            URI(url).toURL().openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            logger.lifecycle("Saved to ${file.absolutePath} (${file.length() / 1024 / 1024} MB)")
        }
    }

val downloadAndroidApp =
    registerDownload("downloadAndroidApp", "androidCurrent", "Downloads the current Android build under test.")

val downloadAndroidPreviousApp = registerDownload(
    "downloadAndroidPreviousApp",
    "androidPrevious",
    "Downloads the previous Android build, for the upgrade suite.",
)

val downloadIosApp =
    registerDownload("downloadIosApp", "iosSimulator", "Downloads the current iOS simulator build under test.")

val unpackIosApp = tasks.register<Copy>("unpackIosApp") {
    group = "app under test"
    description = "Unzips the iOS simulator build so Appium can be pointed at the .app bundle."
    dependsOn(downloadIosApp)
    from(zipTree(appsDir.map { it.file("MyDemoApp-$autCurrentVersion-sim.zip") }))
    into(appsDir.map { it.dir("ios") })
}

// ---------------------------------------------------------------------------
// ADR-003 guard rail: accessibility IDs only, never XPath
// ---------------------------------------------------------------------------

/**
 * React Native's `testID` surfaces as `content-desc` on Android and as the accessibility
 * identifier on iOS, so a single `AppiumBy.accessibilityId` resolves on both. XPath is
 * therefore never necessary for this app, and it is the single biggest source of slow,
 * brittle mobile tests — every lookup walks the whole view hierarchy.
 *
 * Escape hatch: a line ending in `// xpath-ok: <reason>` is exempt, so the genuinely
 * unavoidable cases (OS-level dialogs that live outside the app's view tree — biometric
 * prompts, permission sheets) stay possible but have to be argued for in the diff.
 */
val forbiddenLocatorPatterns = listOf(
    Regex("""AppiumBy\.xpath"""),
    Regex("""\bBy\.xpath"""),
    Regex(""""-android uiautomator""""),
)

val checkNoXPath = tasks.register("checkNoXPath") {
    group = "verification"
    description = "Fails if any source file uses an XPath locator (ADR-003)."

    val sourceTrees = subprojects.map { project ->
        project.layout.projectDirectory.dir("src").asFileTree.matching { include("**/*.java") }
    }
    inputs.files(sourceTrees)

    val rootPath = rootDir

    doLast {
        val violations = mutableListOf<String>()
        sourceTrees.forEach { tree ->
            tree.forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (line.contains("// xpath-ok:")) return@forEachIndexed
                    if (forbiddenLocatorPatterns.any { it.containsMatchIn(line) }) {
                        violations += "${file.relativeTo(rootPath)}:${index + 1}  ${line.trim()}"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("XPath locators are banned by ADR-003. ${violations.size} violation(s):")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("""Use Locators.id("<testID>") — it resolves on Android and iOS alike.""")
                    appendLine("If the element genuinely lives outside the app's view tree, append")
                    appendLine("  // xpath-ok: <reason>")
                    appendLine("to the line and justify it in the pull request.")
                },
            )
        }
        logger.lifecycle("No-XPath check passed across ${sourceTrees.sumOf { it.files.size }} source file(s).")
    }
}

// ---------------------------------------------------------------------------
// Quality gate — everything that runs without a device
// ---------------------------------------------------------------------------

/**
 * The gate that runs on every push, in about a minute, with no emulator and no API key.
 *
 * It names its tasks explicitly rather than delegating to `check`, because `check` in the
 * `:tests` module correctly includes device-backed tests. Being explicit is what keeps
 * "the gate is green" from quietly meaning "the gate skipped everything".
 */
val qualityGate = tasks.register("qualityGate") {
    group = "verification"
    description = "Format, compile, unit tests and the locator policy. No device required."

    dependsOn(checkNoXPath)
    dependsOn(subprojects.map { "${it.path}:spotlessCheck" })
    dependsOn(subprojects.map { "${it.path}:compileJava" })
    dependsOn(subprojects.map { "${it.path}:compileTestJava" })
    dependsOn(":core:test")
}
