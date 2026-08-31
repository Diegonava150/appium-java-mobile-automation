/**
 * Throwaway test classes that exist to be executed <em>by</em> a test, through {@code EngineTestKit},
 * so an extension can be asserted against a real JUnit lifecycle.
 *
 * <p>They are excluded from this module's own test run (see {@code core/build.gradle.kts}). Gradle
 * finds test classes by scanning and then selects them by name, which reaches nested and
 * unconventionally-named classes that JUnit's own discovery filter would skip — so without the
 * exclusion these run twice: once deliberately, once as ordinary members of the suite. For a probe
 * that is meant to fail, the second run is a red build.
 */
package dev.diegonava.mobile.core.junit.probes;
