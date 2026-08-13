package dev.diegonava.mobile.core.perf;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.device.AdbClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures app startup and rendering on a real Android device.
 *
 * <p>These numbers come from the platform's own instrumentation rather than from wall-clock timing
 * around a driver call. A stopwatch around {@code driver.activateApp} measures the automation
 * round-trip as much as the app; {@code am start -W} is what the framework itself reports, and
 * {@code gfxinfo} is the renderer's own accounting.
 *
 * <p>Android only, and deliberately so. iOS startup and frame timing come from Instruments, which
 * needs a local Xcode toolchain and does not fit in this shape. Better to have one platform
 * measured honestly than two measured badly.
 */
public final class PerformanceProbe {

    private static final Logger log = LoggerFactory.getLogger(PerformanceProbe.class);

    private final AdbClient adb;
    private final String appPackage;
    private final String appActivity;

    private PerformanceProbe(AdbClient adb, String appPackage, String appActivity) {
        this.adb = adb;
        this.appPackage = appPackage;
        this.appActivity = appActivity;
    }

    public static PerformanceProbe forCurrentDevice(AdbClient adb) {
        FrameworkConfig config = FrameworkConfig.get();
        String appPackage = config.appPackage()
                .orElseThrow(() -> new IllegalStateException("mobile.app.package is required to measure performance"));
        String activity = config.appActivity().orElse(".MainActivity");
        return new PerformanceProbe(adb, appPackage, activity);
    }

    /**
     * Force-stops the app and measures a genuine cold start.
     *
     * <p>The force-stop is what makes it cold. Without it the process is usually still resident and
     * the measurement is a warm start — typically several times faster, and not the number anyone
     * cares about, because the slow launch a user complains about is the first one.
     */
    public StartupMetrics measureColdStart() {
        adb.run("shell", "am", "force-stop", appPackage);
        String component = appPackage + "/" + appActivity;
        StartupMetrics metrics = StartupMetrics.parse(adb.run("shell", "am", "start", "-W", "-n", component));
        log.info("Cold start of {}: {}", component, metrics);
        return metrics;
    }

    /**
     * Repeats a cold start and returns every sample.
     *
     * <p>One launch on shared CI hardware is close to meaningless. Callers assert on the median so
     * a single unlucky scheduling hiccup cannot fail an otherwise healthy build.
     */
    public List<StartupMetrics> measureColdStarts(int samples) {
        if (samples < 1) {
            throw new IllegalArgumentException("Need at least one sample, asked for " + samples);
        }
        List<StartupMetrics> results = new ArrayList<>(samples);
        for (int i = 0; i < samples; i++) {
            results.add(measureColdStart());
        }
        return List.copyOf(results);
    }

    /** Clears the renderer's counters so the next reading covers only what happens next. */
    public void resetFrameStats() {
        adb.run("shell", "dumpsys", "gfxinfo", appPackage, "reset");
    }

    public FrameMetrics frameStats() {
        FrameMetrics metrics = FrameMetrics.parse(adb.run("shell", "dumpsys", "gfxinfo", appPackage));
        log.info("Frame stats for {}: {}", appPackage, metrics);
        return metrics;
    }

    /** Median of a set of cold starts, in milliseconds. */
    public static long medianTotalMillis(List<StartupMetrics> samples) {
        List<Long> sorted = samples.stream()
                .map(sample -> sample.totalTime().toMillis())
                .sorted()
                .toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }
}
