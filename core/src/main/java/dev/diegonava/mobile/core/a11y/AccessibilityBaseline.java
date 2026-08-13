package dev.diegonava.mobile.core.a11y;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Known, accepted accessibility findings — so the audit gates on <i>new</i> problems.
 *
 * <p>The app under test is somebody else's release and it has pre-existing accessibility debt that
 * cannot be fixed from here. An audit that fails on all of it would be red forever and would be
 * switched off within a week, which is the usual fate of a check that is never green.
 *
 * <p>So the same shape as the flake ledger in ADR-006: the existing debt is written down and
 * accepted, and anything <i>new</i> fails the build. That keeps the check meaningful on the thing
 * a suite can actually influence — a regression — without pretending the baseline is clean.
 *
 * <p>A finding is identified by rule plus element rather than by its detail text, so a button
 * shifting a few pixels does not read as a brand new problem.
 */
public final class AccessibilityBaseline {

    private static final String RESOURCE = "a11y-baseline.txt";

    private final Set<String> accepted;
    private final boolean present;

    private AccessibilityBaseline(Set<String> accepted, boolean present) {
        this.accepted = accepted;
        this.present = present;
    }

    public static AccessibilityBaseline load() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new AccessibilityBaseline(Set.of(), false);
            }
            Set<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new AccessibilityBaseline(lines, true);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    /** Whether a baseline file exists at all. Absent means "first run, record what is there". */
    public boolean exists() {
        return present;
    }

    public int size() {
        return accepted.size();
    }

    public static String signatureOf(AccessibilityFinding finding) {
        return finding.signature();
    }

    /** Findings not already accepted — the ones worth failing a build over. */
    public List<AccessibilityFinding> regressions(List<AccessibilityFinding> findings) {
        return findings.stream()
                .filter(finding -> !accepted.contains(signatureOf(finding)))
                .toList();
    }

    /** The file content that would accept everything currently found, ready to paste. */
    public static String asBaselineFile(String screen, List<AccessibilityFinding> findings) {
        return findings.stream()
                .map(AccessibilityBaseline::signatureOf)
                .distinct()
                .sorted()
                .collect(Collectors.joining(
                        System.lineSeparator(),
                        "# Accepted accessibility findings for " + screen + "."
                                + System.lineSeparator()
                                + "# Pre-existing debt in a third-party app. New findings fail the build."
                                + System.lineSeparator(),
                        System.lineSeparator()));
    }
}
