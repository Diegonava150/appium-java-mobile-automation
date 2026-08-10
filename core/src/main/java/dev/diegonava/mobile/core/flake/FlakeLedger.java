package dev.diegonava.mobile.core.flake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.diegonava.mobile.core.config.FrameworkConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accumulates what quarantine cost this run, and writes it out as a machine-readable ledger.
 *
 * <p>A retry that leaves no trace is indistinguishable from a test that never had a problem. This
 * is the paper trail: which tests needed how many attempts, what they failed with, and when their
 * quarantine expires. {@code ./gradlew flakeReport} reads it, and week three publishes it as a
 * trend so the debt is visible over time rather than only inside one console log.
 */
public final class FlakeLedger {

    private static final Logger log = LoggerFactory.getLogger(FlakeLedger.class);

    private static final Map<String, FlakeRecord> RECORDS = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(FlakeLedger::write, "flake-ledger-writer"));
    }

    private FlakeLedger() {}

    public static void record(FlakeRecord record) {
        RECORDS.put(record.testId(), record);
    }

    public static Collection<FlakeRecord> records() {
        return List.copyOf(RECORDS.values());
    }

    static void clear() {
        RECORDS.clear();
    }

    public static Path reportPath() {
        return Path.of(FrameworkConfig.get().string("mobile.flake.report", "build/flake-report.json"))
                .toAbsolutePath();
    }

    /**
     * Writes the ledger.
     *
     * <p>Runs from a shutdown hook so the file exists even when the run ends badly — a crashed
     * suite is exactly when you want to know what was already limping.
     */
    public static void write() {
        if (RECORDS.isEmpty()) {
            return;
        }
        Path target = reportPath();
        try {
            Files.createDirectories(target.getParent());
            List<FlakeRecord> sorted = RECORDS.values().stream()
                    .sorted((a, b) -> a.testId().compareTo(b.testId()))
                    .toList();
            MAPPER.writeValue(target.toFile(), sorted);
            log.info("Flake ledger written to {} ({} quarantined test(s))", target, sorted.size());
        } catch (IOException e) {
            log.warn("Could not write the flake ledger to {}: {}", target, e.getMessage());
        }
    }
}
