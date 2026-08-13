package dev.diegonava.mobile.ai.triage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The three views of one failed moment that {@code FailureArtifactExtension} leaves on disk.
 *
 * <p>A screenshot, the native view hierarchy, and the device log, captured the instant the test
 * method threw — while the device is still sitting on the failing screen. That combination is
 * what a mobile failure can produce and a stack trace cannot, and it is the reason this framework
 * diagnosed six rounds of iOS failures from a Linux machine with no Mac attached. The
 * "Save Password?" system dialog sitting invisibly over the catalog was in the screenshot and in
 * no log anywhere.
 *
 * <p>A bundle is deliberately tolerant of missing parts: a device that will not screenshot may
 * still hand over its page source, and a partial bundle beats no bundle.
 */
public record FailureBundle(String name, Optional<Path> screenshot, Optional<Path> pageSource, Optional<Path> logcat) {

    private static final String SCREENSHOT = "screenshot.png";
    private static final String PAGE_SOURCE = "page-source.xml";
    private static final String LOGCAT = "logcat.txt";

    /**
     * Every failure directory under {@code artifactsDir} that holds at least one artifact.
     *
     * <p>Directories with nothing in them are skipped rather than reported as empty failures —
     * they are usually the residue of a capture that could not reach the device at all, and
     * sending one to a model produces a confident answer about no evidence whatsoever.
     */
    public static List<FailureBundle> discover(Path artifactsDir) throws IOException {
        if (!Files.isDirectory(artifactsDir)) {
            return List.of();
        }
        List<FailureBundle> bundles = new ArrayList<>();
        try (Stream<Path> entries = Files.list(artifactsDir)) {
            entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(dir -> {
                        FailureBundle bundle = new FailureBundle(
                                dir.getFileName().toString(),
                                existing(dir.resolve(SCREENSHOT)),
                                existing(dir.resolve(PAGE_SOURCE)),
                                existing(dir.resolve(LOGCAT)));
                        if (bundle.hasEvidence()) {
                            bundles.add(bundle);
                        }
                    });
        }
        return List.copyOf(bundles);
    }

    public boolean hasEvidence() {
        return screenshot.isPresent() || pageSource.isPresent() || logcat.isPresent();
    }

    /**
     * The end of the device log.
     *
     * <p>The tail, not the head. A logcat buffer opens with minutes of unrelated system chatter
     * from before the app even launched; whatever went wrong is in the last few hundred lines.
     * Sending the head is worse than sending nothing, because it looks like evidence.
     */
    public String logcatTail(int maxLines) throws IOException {
        if (logcat.isEmpty()) {
            return "";
        }
        List<String> lines = Files.readAllLines(logcat.get(), StandardCharsets.UTF_8);
        List<String> tail = lines.size() <= maxLines ? lines : lines.subList(lines.size() - maxLines, lines.size());
        String body = String.join(System.lineSeparator(), tail);
        if (lines.size() <= maxLines) {
            return body;
        }
        return "... %d earlier line(s) omitted ...%n%s".formatted(lines.size() - maxLines, body);
    }

    /** The test this bundle belongs to, as {@code Class.method.platform}. */
    public String testId() {
        return name;
    }

    private static Optional<Path> existing(Path candidate) {
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }
}
