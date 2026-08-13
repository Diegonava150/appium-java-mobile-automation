package dev.diegonava.mobile.core.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records every locator a fallback had to rescue, and fails the build on new ones.
 *
 * <p>This is the whole argument of the AI layer, and it is deliberately the opposite of what
 * self-healing is usually sold as. A framework that silently repairs a broken locator has not
 * fixed anything — it has hidden a change in the app and removed the only signal that would have
 * prompted anyone to look. Do that for a year and the suite is a set of assertions about a UI
 * nobody can describe any more.
 *
 * <p>So healing here buys exactly one thing: the run continues instead of collapsing at the first
 * changed selector. It buys it on credit. Every heal is recorded with its evidence, the accepted
 * set is committed, and <b>a heal that is not in the accepted set fails the build</b>. The
 * appealing property is that the failure names the precise locator to fix and attaches the
 * screenshot showing what the element became.
 *
 * <p>Same shape as the flake ledger (ADR-006) and the accessibility baseline, for the same reason:
 * the debt is visible, attributed and dated, rather than absorbed.
 */
public final class LocatorDebtLedger {

    private static final Logger log = LoggerFactory.getLogger(LocatorDebtLedger.class);
    private static final String RESOURCE = "locator-debt.txt";

    private static final Map<String, LocatorHealingEvent> RECORDED = new ConcurrentHashMap<>();

    private final Set<String> accepted;

    private LocatorDebtLedger(Set<String> accepted) {
        this.accepted = accepted;
    }

    /** Loads the accepted set from the classpath. An absent file means nothing is accepted. */
    public static LocatorDebtLedger load() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return new LocatorDebtLedger(Set.of());
            }
            Set<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new LocatorDebtLedger(lines);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + RESOURCE, e);
        }
    }

    /** Called by the fallback whenever it rescues a locator. */
    public static void record(LocatorHealingEvent event) {
        RECORDED.put(event.signature(), event);
        log.warn(
                "Locator debt: {} was rescued by {} during {}. The locator still needs fixing.",
                event.signature(),
                event.resolvedBy(),
                event.testId());
    }

    public static List<LocatorHealingEvent> recorded() {
        return List.copyOf(RECORDED.values());
    }

    static void clear() {
        RECORDED.clear();
    }

    public int acceptedCount() {
        return accepted.size();
    }

    /** Heals that are not in the accepted set — the ones worth failing over. */
    public List<LocatorHealingEvent> newDebt() {
        return RECORDED.values().stream()
                .filter(event -> !accepted.contains(event.signature()))
                .toList();
    }

    /**
     * Accepted entries that nothing rescued this run.
     *
     * <p>The graduation signal. A locator that stops needing help has either been fixed or been
     * deleted, and either way its line should come out of the file — otherwise the accepted set
     * grows monotonically and stops meaning anything, which is exactly how quarantine lists rot.
     */
    public List<String> staleAcceptances() {
        return accepted.stream()
                .filter(signature -> !RECORDED.containsKey(signature))
                .toList();
    }

    /** A failure message that names what to fix rather than reporting a count. */
    public String describeNewDebt() {
        List<LocatorHealingEvent> debt = newDebt();
        return debt.stream()
                .map(event -> "  %s%n      rescued by: %s%n      evidence:   %s"
                        .formatted(event.signature(), event.resolvedBy(), event.evidence()))
                .collect(
                        Collectors.joining(System.lineSeparator(), """
                        %d locator(s) had to be rescued by the fallback and are not in the accepted set.

                        The run was kept alive on credit. Fix each locator, or add its signature to
                        locator-debt.txt with a reason and an owner:

                        """.formatted(debt.size()), System.lineSeparator()));
    }

    /** Writes the run's heals for CI to keep, whether or not the build failed on them. */
    public static void writeReport(Path target) {
        if (RECORDED.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            String content = RECORDED.values().stream()
                    .map(event -> "%s|%s|%s|%s|%s"
                            .formatted(
                                    event.testId(),
                                    event.screen(),
                                    event.failedLocator(),
                                    event.resolvedBy(),
                                    event.evidence()))
                    .sorted()
                    .collect(Collectors.joining(System.lineSeparator()));
            Files.writeString(target, content, StandardCharsets.UTF_8);
            log.info("Locator debt report written to {} ({} heal(s))", target, RECORDED.size());
        } catch (IOException e) {
            log.warn("Could not write the locator debt report to {}: {}", target, e.getMessage());
        }
    }
}
