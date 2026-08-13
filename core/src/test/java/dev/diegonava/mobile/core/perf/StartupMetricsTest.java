package dev.diegonava.mobile.core.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parser tests for {@code am start -W} output.
 *
 * <p>Worth having as unit tests rather than trusting a live device: the format differs between API
 * levels, and the interesting cases — a launch that failed, a missing field — are awkward to
 * reproduce on demand and easy to get silently wrong. Reading a zero out of a failed launch would
 * report a broken app as an outstanding performance result.
 */
class StartupMetricsTest {

    private static final String HEALTHY = """
            Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] }
            Status: ok
            LaunchState: COLD
            Activity: com.saucelabs.mydemoapp.rn/.MainActivity
            ThisTime: 523
            TotalTime: 523
            WaitTime: 570
            Complete""";

    @Test
    @DisplayName("reads status, activity and all three timings from a healthy cold start")
    void parsesHealthyLaunch() {
        StartupMetrics metrics = StartupMetrics.parse(HEALTHY);

        assertThat(metrics.launchedCleanly()).isTrue();
        assertThat(metrics.activity()).isEqualTo("com.saucelabs.mydemoapp.rn/.MainActivity");
        assertThat(metrics.thisTime().toMillis()).isEqualTo(523);
        assertThat(metrics.totalTime().toMillis()).isEqualTo(523);
        assertThat(metrics.waitTime().toMillis()).isEqualTo(570);
    }

    @Test
    @DisplayName("falls back to TotalTime when an API level omits WaitTime")
    void toleratesMissingWaitTime() {
        String output = """
                Status: ok
                Activity: com.example/.Main
                ThisTime: 300
                TotalTime: 412""";

        StartupMetrics metrics = StartupMetrics.parse(output);

        assertThat(metrics.totalTime().toMillis()).isEqualTo(412);
        assertThat(metrics.waitTime().toMillis()).isEqualTo(412);
    }

    @Test
    @DisplayName("a launch with no TotalTime is an error, never a zero")
    void refusesToInventATimingForAFailedLaunch() {
        String output = """
                Starting: Intent { act=android.intent.action.MAIN }
                Error: Activity not started, unable to resolve Intent
                Status: error""";

        assertThatThrownBy(() -> StartupMetrics.parse(output))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no TotalTime")
                .hasMessageContaining("error");
    }

    @Test
    @DisplayName("empty output is rejected outright")
    void rejectsEmptyOutput() {
        assertThatThrownBy(() -> StartupMetrics.parse("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the median of an odd sample set is the middle value")
    void medianOfOddSampleSet() {
        List<StartupMetrics> samples = List.of(sampleOf(700), sampleOf(500), sampleOf(600));
        assertThat(PerformanceProbe.medianTotalMillis(samples)).isEqualTo(600);
    }

    @Test
    @DisplayName("the median of an even sample set averages the middle pair")
    void medianOfEvenSampleSet() {
        List<StartupMetrics> samples = List.of(sampleOf(400), sampleOf(500), sampleOf(600), sampleOf(700));
        assertThat(PerformanceProbe.medianTotalMillis(samples)).isEqualTo(550);
    }

    @Test
    @DisplayName("the median ignores a single outlier, which is the point of using it")
    void medianResistsAnOutlier() {
        List<StartupMetrics> samples = List.of(sampleOf(500), sampleOf(520), sampleOf(9000));
        assertThat(PerformanceProbe.medianTotalMillis(samples)).isEqualTo(520);
    }

    private static StartupMetrics sampleOf(long totalMillis) {
        return StartupMetrics.parse("""
                Status: ok
                Activity: com.example/.Main
                ThisTime: %d
                TotalTime: %d
                WaitTime: %d""".formatted(totalMillis, totalMillis, totalMillis));
    }
}
