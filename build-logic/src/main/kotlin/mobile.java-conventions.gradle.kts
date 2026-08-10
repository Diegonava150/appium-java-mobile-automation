plugins {
    // java-library, not java: core and screens expose Appium and JUnit types on their
    // own public API, so downstream modules need them on the compile classpath.
    `java-library`
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -serial: we never serialize anything here.
    // -processing: no annotation processors are wired, and the warning is noise.
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing"))
}

spotless {
    java {
        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
