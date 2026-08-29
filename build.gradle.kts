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
            file.parentFile.mkdirs()

            // Retried, because this is a ~31 MB transfer over the public internet on every cold
            // CI run and it does occasionally break mid-stream. One `SocketException: Unexpected
            // end of file from server` took out a whole lane, which is an absurd way to lose a
            // build. A partial file is deleted before retrying so a truncated APK is never left
            // behind to be "already present" next time.
            val attempts = 3
            for (attempt in 1..attempts) {
                try {
                    logger.lifecycle("Downloading $assetName (attempt $attempt of $attempts) …")
                    URI(url).toURL().openStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                    logger.lifecycle("Saved to ${file.absolutePath} (${file.length() / 1024 / 1024} MB)")
                    return@doLast
                } catch (e: Exception) {
                    file.delete()
                    if (attempt == attempts) {
                        throw GradleException("Could not download $assetName after $attempts attempts", e)
                    }
                    logger.lifecycle("  failed (${e.message}); retrying in ${attempt * 5}s")
                    Thread.sleep(attempt * 5000L)
                }
            }
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
// Locator debt — the gate that makes AI healing cost something (ADR-009)
// ---------------------------------------------------------------------------

/**
 * Fails when the AI fallback rescued a locator that is not in the accepted set.
 *
 * This is the whole argument of the AI layer expressed as a build task. Healing keeps the run
 * alive; this makes sure it does not also keep the app change invisible. The failure names each
 * locator and points at the screenshot the fallback based its answer on, so fixing it is a
 * matter of opening an image rather than reproducing a twenty-minute device run.
 *
 * It also reports the reverse: accepted entries that nothing rescued. Those locators have been
 * fixed or deleted upstream, and leaving their lines in place is how an exemption list stops
 * meaning anything.
 *
 * Runs after `:tests:test` rather than inside `qualityGate` — with no device there are no
 * locators to heal, so there is nothing for it to check.
 */
val checkLocatorDebt = tasks.register("checkLocatorDebt") {
    group = "verification"
    description = "Fails on AI-healed locators that are not in tests/src/test/resources/locator-debt.txt."

    val reportFile = layout.buildDirectory.file("locator-debt-report.txt")
    val acceptedFile = file("tests/src/test/resources/locator-debt.txt")

    inputs.files(reportFile).optional()
    inputs.file(acceptedFile)

    doLast {
        val report = reportFile.get().asFile
        if (!report.exists()) {
            logger.lifecycle("No locators were healed this run. Nothing to reconcile.")
            return@doLast
        }

        // A byte-order mark on the first line would make an accepted signature silently fail to
        // match its own entry — the worst possible failure for this task, since it reads as
        // "you did not accept that" rather than as an encoding problem. Both files are
        // hand-edited on Windows often enough for this to be worth two lines.
        fun String.withoutBom() = removePrefix("﻿")

        // Report lines are `signature|testId|resolvedBy|evidence`, signature first precisely so
        // this comparison reads one field instead of reassembling it.
        val healed = report.readLines()
            .filter { it.isNotBlank() }
            .associate { line -> line.withoutBom().substringBefore('|') to line.withoutBom().split('|') }

        val accepted = acceptedFile.readLines()
            .map { it.withoutBom().trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val stale = accepted - healed.keys
        if (stale.isNotEmpty()) {
            logger.warn("")
            logger.warn("${stale.size} accepted locator(s) needed no rescue this run. Delete their lines:")
            stale.forEach { logger.warn("  $it") }
        }

        val unaccepted = healed.filterKeys { it !in accepted }
        if (unaccepted.isEmpty()) {
            logger.lifecycle("Locator debt reconciled: ${healed.size} heal(s), all accepted.")
            return@doLast
        }

        throw GradleException(
            buildString {
                appendLine("${unaccepted.size} locator(s) were rescued by the AI fallback and are not accepted.")
                appendLine()
                appendLine("The run was kept alive on credit (ADR-009). Each of these is a locator in the")
                appendLine("source that no longer matches the app. Fix it, or accept it explicitly with a")
                appendLine("reason and an owner in tests/src/test/resources/locator-debt.txt.")
                appendLine()
                unaccepted.forEach { (signature, fields) ->
                    appendLine("  $signature")
                    appendLine("      in test:    ${fields.getOrElse(1) { "?" }}")
                    appendLine("      rescued by: ${fields.getOrElse(2) { "?" }}")
                    appendLine("      screenshot: ${fields.getOrElse(3) { "?" }}")
                }
            },
        )
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
/**
 * Fails on an expired `@Flaky`, without a device.
 *
 * The expiry was already enforced — by `QuarantinePolicy`, called from `FlakyExtension` while the
 * annotated test executes. That means it only fires when the device suite runs *and* reaches that
 * test, and the device lanes are slow, infrastructure-flaky and deliberately not required checks.
 * An exemption could therefore sit past its date indefinitely without blocking a single merge,
 * which is precisely the "permanent exemption" the mechanism exists to prevent. The rule was real
 * and its enforcement was in the one lane least likely to gate anything.
 *
 * So the same rule is checked here, from source, on every push. Reading the annotation rather than
 * running the test is what makes it device-free — and the date in the source is the thing being
 * asserted on anyway.
 */
val checkQuarantine = tasks.register("checkQuarantine") {
    group = "verification"
    description = "Fails on an @Flaky whose expires date has passed (ADR-006). No device required."

    val sourceTrees = subprojects.map { project ->
        project.layout.projectDirectory.dir("src").asFileTree.matching { include("**/*.java") }
    }
    inputs.files(sourceTrees)

    // Deliberately not cacheable on inputs alone: the same unchanged source becomes a failure the
    // day the date passes, so a task that skipped as UP-TO-DATE would keep reporting yesterday's
    // answer. That is the exact failure mode this task exists to remove.
    outputs.upToDateWhen { false }

    val rootPath = rootDir

    doLast {
        val flakyBlock = Regex("""@Flaky\s*\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
        val expiresIn = Regex("""expires\s*=\s*"([^"]*)"""")
        val today = java.time.LocalDate.now()

        val expired = mutableListOf<String>()
        val malformed = mutableListOf<String>()
        var found = 0

        sourceTrees.forEach { tree ->
            tree.forEach { file ->
                // The annotation declaration itself is not a usage.
                if (file.name == "Flaky.java") return@forEach
                val text = file.readText()
                flakyBlock.findAll(text).forEach { block ->
                    found++
                    val where = file.relativeTo(rootPath).path
                    val raw = expiresIn.find(block.value)?.groupValues?.get(1)
                    if (raw == null) {
                        malformed += "$where  @Flaky with no expires"
                        return@forEach
                    }
                    val date = runCatching { java.time.LocalDate.parse(raw) }.getOrNull()
                    if (date == null) {
                        malformed += "$where  expires=\"$raw\" is not an ISO-8601 date"
                    } else if (date.isBefore(today)) {
                        expired += "$where  expired on $raw"
                    }
                }
            }
        }

        if (expired.isNotEmpty() || malformed.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Quarantine policy (ADR-006). ${expired.size + malformed.size} problem(s):")
                    (expired + malformed).forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("An exemption with no deadline is a permanent one. Either:")
                    appendLine("  - fix the underlying instability and remove @Flaky, or")
                    appendLine("  - extend expires with a reason that says what changed.")
                },
            )
        }
        logger.lifecycle("Quarantine check passed: $found @Flaky annotation(s), none expired.")
    }
}

val qualityGate = tasks.register("qualityGate") {
    group = "verification"
    description = "Format, compile, unit tests and the locator policy. No device required."

    dependsOn(checkNoXPath)
    dependsOn(checkQuarantine)
    dependsOn(subprojects.map { "${it.path}:spotlessCheck" })
    dependsOn(subprojects.map { "${it.path}:compileJava" })
    dependsOn(subprojects.map { "${it.path}:compileTestJava" })
    dependsOn(":core:test")

    // The AI layer's unit tests need no key and no network — they cover the credential
    // resolution, the prompt, the hierarchy digest and the locator policy gate, which is
    // everything about that module except the HTTP call in the middle. Leaving them out of
    // the gate would mean the one part of the AI layer that *is* verifiable never runs in CI.
    //
    // Conditional so the gate still runs under -Pmobile.ai.absent=true, which is the run that
    // proves the module is genuinely removable. See settings.gradle.kts.
    if (findProject(":ai") != null) {
        dependsOn(":ai:test")
    }
}
