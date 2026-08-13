package dev.diegonava.mobile.core.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parser tests for {@code dumpsys gfxinfo} output. */
class FrameMetricsTest {

    private static final String GFXINFO = """
            Applications Graphics Acceleration Info:
            Uptime: 123456 Realtime: 123456

            ** Graphics info for pid 4242 [com.saucelabs.mydemoapp.rn] **

            Total frames rendered: 500
            Janky frames: 25 (5.00%)
            50th percentile: 8ms
            90th percentile: 14ms
            95th percentile: 20ms
            99th percentile: 35ms
            Number Missed Vsync: 3
            Number High input latency: 0""";

    @Test
    @DisplayName("reads frame counts and every percentile")
    void parsesFullOutput() {
        FrameMetrics metrics = FrameMetrics.parse(GFXINFO);

        assertThat(metrics.totalFrames()).isEqualTo(500);
        assertThat(metrics.jankyFrames()).isEqualTo(25);
        assertThat(metrics.p50().toMillis()).isEqualTo(8);
        assertThat(metrics.p90().toMillis()).isEqualTo(14);
        assertThat(metrics.p95().toMillis()).isEqualTo(20);
        assertThat(metrics.p99().toMillis()).isEqualTo(35);
    }

    @Test
    @DisplayName("computes jank as a percentage of frames rendered")
    void computesJankPercentage() {
        assertThat(FrameMetrics.parse(GFXINFO).jankPercentage()).isEqualTo(5.0d);
    }

    @Test
    @DisplayName("takes the count from the janky line and ignores its bracketed percentage")
    void ignoresTheBracketedPercentage() {
        assertThat(FrameMetrics.parse(GFXINFO).jankyFrames()).isEqualTo(25);
    }

    @Test
    @DisplayName("a tiny sample is flagged as unusable rather than asserted on")
    void tinySampleIsNotUsable() {
        String output = """
                Total frames rendered: 4
                Janky frames: 2 (50.00%)
                50th percentile: 9ms
                90th percentile: 15ms
                95th percentile: 18ms
                99th percentile: 40ms""";

        FrameMetrics metrics = FrameMetrics.parse(output);

        assertThat(metrics.hasUsableSample())
                .as("four frames cannot support a 99th percentile claim")
                .isFalse();
        assertThat(FrameMetrics.parse(GFXINFO).hasUsableSample()).isTrue();
    }

    @Test
    @DisplayName("zero frames does not divide by zero")
    void zeroFramesIsSafe() {
        FrameMetrics metrics = FrameMetrics.parse("Total frames rendered: 0\nJanky frames: 0 (0.00%)");
        assertThat(metrics.jankPercentage()).isEqualTo(0d);
        assertThat(metrics.hasUsableSample()).isFalse();
    }

    @Test
    @DisplayName("empty output is rejected outright")
    void rejectsEmptyOutput() {
        assertThatThrownBy(() -> FrameMetrics.parse("")).isInstanceOf(IllegalArgumentException.class);
    }
}
