package dev.diegonava.mobile.core.perf;

import java.time.Duration;
import java.util.Optional;

/**
 * What {@code am start -W} reports about launching an activity.
 *
 * <p>The distinction between the two timings is the whole reason to capture both. {@code ThisTime}
 * covers the last activity in the launch chain; {@code TotalTime} covers the whole chain from
 * process start to that activity reporting itself drawn. {@code TotalTime} is the number that
 * corresponds to what a user experiences as "how long until the app appeared", so that is the one
 * worth putting a budget on.
 *
 * @param status the launcher's own status line, "ok" on a healthy launch
 * @param activity the component that was launched
 * @param thisTime time for the final activity in the chain
 * @param totalTime time for the entire launch chain — the user-visible number
 * @param waitTime time the launcher itself waited, including any pre-existing work
 */
public record StartupMetrics(String status, String activity, Duration thisTime, Duration totalTime, Duration waitTime) {

    public boolean launchedCleanly() {
        return "ok".equalsIgnoreCase(status);
    }

    /**
     * Parses the output of {@code adb shell am start -W}.
     *
     * <p>Written as a parser with its own tests rather than a regex buried in a test, because the
     * format varies between API levels — {@code WaitTime} is absent on some, and a cold start that
     * fails prints a {@code Status} of something other than {@code ok} with no timings at all.
     * Silently reading a zero out of that would turn a broken launch into a spectacular
     * performance result.
     */
    public static StartupMetrics parse(String amStartOutput) {
        if (amStartOutput == null || amStartOutput.isBlank()) {
            throw new IllegalArgumentException("am start produced no output at all");
        }

        String status = field(amStartOutput, "Status").orElse("unknown");
        String activity = field(amStartOutput, "Activity").orElse("unknown");

        Optional<Duration> total = millis(amStartOutput, "TotalTime");
        if (total.isEmpty()) {
            throw new IllegalStateException("""
                    am start reported no TotalTime, so the launch did not complete. Status was '%s'.
                    Full output:
                    %s""".formatted(status, amStartOutput.strip()));
        }

        return new StartupMetrics(
                status,
                activity,
                millis(amStartOutput, "ThisTime").orElse(total.get()),
                total.get(),
                millis(amStartOutput, "WaitTime").orElse(total.get()));
    }

    private static Optional<String> field(String output, String name) {
        return output.lines()
                .map(String::strip)
                .filter(line -> line.startsWith(name + ":"))
                .map(line -> line.substring(name.length() + 1).strip())
                .findFirst();
    }

    private static Optional<Duration> millis(String output, String name) {
        return field(output, name).flatMap(value -> {
            try {
                return Optional.of(Duration.ofMillis(Long.parseLong(value.trim())));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public String toString() {
        return "startup{status=%s, total=%dms, this=%dms, wait=%dms}"
                .formatted(status, totalTime.toMillis(), thisTime.toMillis(), waitTime.toMillis());
    }
}
