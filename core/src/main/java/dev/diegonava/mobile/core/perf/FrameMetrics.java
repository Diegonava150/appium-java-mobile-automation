package dev.diegonava.mobile.core.perf;

import java.time.Duration;
import java.util.Optional;

/**
 * Rendering statistics from {@code dumpsys gfxinfo}.
 *
 * <p>Percentiles rather than an average, because an average frame time hides exactly the thing
 * users notice. A screen that renders 95% of frames in 6ms and the rest in 90ms feels broken and
 * averages fine. The 95th and 99th percentiles are where stutter lives.
 *
 * @param totalFrames frames rendered since the last reset
 * @param jankyFrames frames that missed their deadline
 * @param p50 median frame time
 * @param p90 90th percentile frame time
 * @param p95 95th percentile frame time
 * @param p99 99th percentile frame time
 */
public record FrameMetrics(int totalFrames, int jankyFrames, Duration p50, Duration p90, Duration p95, Duration p99) {

    /** Percentage of frames that missed their deadline. */
    public double jankPercentage() {
        return totalFrames == 0 ? 0d : (jankyFrames * 100d) / totalFrames;
    }

    /**
     * Whether enough frames were rendered for the numbers to mean anything.
     *
     * <p>A handful of frames can produce a flattering or alarming percentile by accident. Asserting
     * on a sample this small is how a performance gate becomes a coin toss.
     */
    public boolean hasUsableSample() {
        return totalFrames >= 20;
    }

    /**
     * Parses {@code adb shell dumpsys gfxinfo <package>}.
     *
     * <p>Only the aggregate block is read. The command also emits a large per-frame table which is
     * useful interactively and pure noise to a gate.
     */
    public static FrameMetrics parse(String gfxinfoOutput) {
        if (gfxinfoOutput == null || gfxinfoOutput.isBlank()) {
            throw new IllegalArgumentException("dumpsys gfxinfo produced no output at all");
        }

        int total = integerAfter(gfxinfoOutput, "Total frames rendered:").orElse(0);
        int janky = integerAfter(gfxinfoOutput, "Janky frames:").orElse(0);

        return new FrameMetrics(
                total,
                janky,
                percentile(gfxinfoOutput, "50th").orElse(Duration.ZERO),
                percentile(gfxinfoOutput, "90th").orElse(Duration.ZERO),
                percentile(gfxinfoOutput, "95th").orElse(Duration.ZERO),
                percentile(gfxinfoOutput, "99th").orElse(Duration.ZERO));
    }

    private static Optional<Integer> integerAfter(String output, String label) {
        return output.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(label))
                .map(line -> line.substring(label.length()).strip())
                // "Janky frames: 25 (5.00%)" — take the count, drop the percentage.
                .map(rest -> rest.split("\\s+")[0])
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .findFirst();
    }

    /** Reads lines of the form {@code 95th percentile: 20ms}. */
    private static Optional<Duration> percentile(String output, String which) {
        return output.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(which + " percentile:"))
                .map(line -> line.substring(line.indexOf(':') + 1).strip())
                .map(value -> value.replace("ms", "").strip())
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(Duration.ofMillis(Long.parseLong(value)));
                    } catch (NumberFormatException e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .findFirst();
    }

    @Override
    public String toString() {
        return "frames{total=%d, janky=%d (%.1f%%), p50=%dms, p90=%dms, p95=%dms, p99=%dms}"
                .formatted(
                        totalFrames,
                        jankyFrames,
                        jankPercentage(),
                        p50.toMillis(),
                        p90.toMillis(),
                        p95.toMillis(),
                        p99.toMillis());
    }
}
