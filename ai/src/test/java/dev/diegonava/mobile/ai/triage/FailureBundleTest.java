package dev.diegonava.mobile.ai.triage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FailureBundleTest {

    private static Path bundleDir(Path root, String name, String... files) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        for (String file : files) {
            Files.writeString(dir.resolve(file), "content");
        }
        return dir;
    }

    @Test
    @DisplayName("a complete bundle is discovered with all three artifacts")
    void discoversCompleteBundle(@TempDir Path root) throws Exception {
        bundleDir(root, "CheckoutTest.buys.android", "screenshot.png", "page-source.xml", "logcat.txt");

        List<FailureBundle> found = FailureBundle.discover(root);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).name()).isEqualTo("CheckoutTest.buys.android");
        assertThat(found.get(0).screenshot()).isPresent();
        assertThat(found.get(0).pageSource()).isPresent();
        assertThat(found.get(0).logcat()).isPresent();
    }

    @Test
    @DisplayName("a partial bundle is still a bundle — iOS never produces a logcat")
    void partialBundleIsUsable(@TempDir Path root) throws Exception {
        bundleDir(root, "LoginTest.signsIn.ios", "screenshot.png", "page-source.xml");

        List<FailureBundle> found = FailureBundle.discover(root);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).logcat()).isEmpty();
        assertThat(found.get(0).hasEvidence()).isTrue();
    }

    @Test
    @DisplayName("an empty directory is skipped rather than reported as a failure with no evidence")
    void emptyDirectoriesAreSkipped(@TempDir Path root) throws Exception {
        // These are the residue of a capture that could not reach the device at all. Sending one
        // to a model produces a confident answer about nothing whatsoever.
        bundleDir(root, "Ghost.test.android");
        bundleDir(root, "Real.test.android", "screenshot.png");

        assertThat(FailureBundle.discover(root)).extracting(FailureBundle::name).containsExactly("Real.test.android");
    }

    @Test
    @DisplayName("discovery on a missing directory is empty, not an exception")
    void missingDirectoryIsEmpty(@TempDir Path root) throws Exception {
        assertThat(FailureBundle.discover(root.resolve("never-created"))).isEmpty();
    }

    @Test
    @DisplayName("bundles come back in a stable order")
    void orderIsStable(@TempDir Path root) throws Exception {
        bundleDir(root, "Zebra.test.android", "screenshot.png");
        bundleDir(root, "Alpha.test.android", "screenshot.png");

        assertThat(FailureBundle.discover(root))
                .extracting(FailureBundle::name)
                .containsExactly("Alpha.test.android", "Zebra.test.android");
    }

    @Test
    @DisplayName("the logcat tail keeps the end of the log, not the start")
    void tailKeepsTheEnd(@TempDir Path root) throws Exception {
        // The buffer opens with minutes of system chatter from before the app launched. Whatever
        // went wrong is at the end, and sending the head is worse than sending nothing because it
        // looks like evidence.
        Path dir = Files.createDirectories(root.resolve("Long.test.android"));
        String log = IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> "line " + i)
                .reduce((a, b) -> a + "\n" + b)
                .orElseThrow();
        Files.writeString(dir.resolve("logcat.txt"), log);

        String tail = FailureBundle.discover(root).get(0).logcatTail(10);

        assertThat(tail).contains("line 1000").contains("line 991").doesNotContain("line 1\n");
        assertThat(tail).contains("990 earlier line(s) omitted");
    }

    @Test
    @DisplayName("a short log is returned whole, with no truncation marker")
    void shortLogIsWhole(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("Short.test.android"));
        Files.writeString(dir.resolve("logcat.txt"), "only\ntwo lines");

        String tail = FailureBundle.discover(root).get(0).logcatTail(400);

        assertThat(tail)
                .isEqualTo("only" + System.lineSeparator() + "two lines")
                .doesNotContain("omitted");
    }

    @Test
    @DisplayName("no logcat gives an empty tail rather than an exception")
    void absentLogcatIsEmpty(@TempDir Path root) throws Exception {
        bundleDir(root, "NoLog.test.ios", "screenshot.png");

        assertThat(FailureBundle.discover(root).get(0).logcatTail(400)).isEmpty();
    }
}
