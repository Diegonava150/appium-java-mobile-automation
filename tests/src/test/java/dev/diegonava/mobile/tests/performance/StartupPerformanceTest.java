package dev.diegonava.mobile.tests.performance;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegonava.mobile.core.config.FrameworkConfig;
import dev.diegonava.mobile.core.config.MobilePlatform;
import dev.diegonava.mobile.core.device.AdbClient;
import dev.diegonava.mobile.core.driver.DriverManager;
import dev.diegonava.mobile.core.junit.EnabledOnPlatform;
import dev.diegonava.mobile.core.junit.MobileTest;
import dev.diegonava.mobile.core.perf.FrameMetrics;
import dev.diegonava.mobile.core.perf.PerformanceProbe;
import dev.diegonava.mobile.core.perf.StartupMetrics;
import dev.diegonava.mobile.screens.App;
import dev.diegonava.mobile.screens.CatalogScreen;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Startup and rendering budgets, measured with the platform's own instrumentation.
 *
 * <p>Two honest caveats are built into how these assert, rather than left out of the README.
 *
 * <p>First, the budgets are loose. A software-rendered emulator on a shared two-core runner is
 * several times slower than any handset, so a threshold tuned on a developer's machine would fail
 * constantly and train everyone to ignore the gate. These catch a regression of the order that
 * matters — a launch that suddenly doubles — and the recorded numbers are the more useful signal
 * as they accumulate.
 *
 * <p>Second, every measurement is attached to the report whether it passes or fails. A performance
 * test that only speaks up when it breaks throws away the data that would have shown the trend.
 */
@MobileTest
@EnabledOnPlatform(
        value = MobilePlatform.ANDROID,
        reason = "am start and gfxinfo are Android instrumentation; iOS equivalents need Instruments"
                + " and a local Xcode toolchain")
@Epic("Performance")
@DisplayName("Startup and rendering")
class StartupPerformanceTest {

    @Test
    @Feature("Cold start")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("cold start stays within budget across repeated launches")
    void coldStartIsWithinBudget() {
        FrameworkConfig config = FrameworkConfig.get();
        PerformanceProbe probe = PerformanceProbe.forCurrentDevice(AdbClient.forSlot(DriverManager.slot()));

        List<StartupMetrics> samples = probe.measureColdStarts(config.startupSamples());
        long median = PerformanceProbe.medianTotalMillis(samples);

        Allure.addAttachment(
                "cold start samples",
                "text/plain",
                samples.stream().map(StartupMetrics::toString).reduce("", (a, b) -> a + b + "\n")
                        + "\nmedian TotalTime: " + median + "ms"
                        + "\nbudget: " + config.startupBudget().toMillis() + "ms");

        assertThat(samples).allSatisfy(sample -> assertThat(sample.launchedCleanly())
                .as("every launch should report Status: ok, got '%s'", sample.status())
                .isTrue());

        // Median, not mean and not worst: one unlucky scheduling hiccup on a shared runner should
        // not fail an otherwise healthy build, and one lucky launch should not hide a regression.
        assertThat(median)
                .as(
                        "median cold start over %d samples should stay within %dms",
                        samples.size(), config.startupBudget().toMillis())
                .isLessThanOrEqualTo(config.startupBudget().toMillis());
    }

    @Test
    @Feature("Rendering")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("scrolling the catalog does not blow the jank budget")
    void catalogScrollingIsSmooth() {
        FrameworkConfig config = FrameworkConfig.get();
        PerformanceProbe probe = PerformanceProbe.forCurrentDevice(AdbClient.forSlot(DriverManager.slot()));

        CatalogScreen catalog = App.launch();
        probe.resetFrameStats();

        // Generate frames to measure. Counters were just reset, so everything below is the
        // catalog scrolling and nothing else.
        catalog.scrollThroughProducts(4);

        FrameMetrics frames = probe.frameStats();

        Allure.addAttachment(
                "frame statistics", "text/plain", frames + "\njank budget: " + config.jankBudgetPercent() + "%");

        assertThat(frames.hasUsableSample())
                .as("only %d frames were rendered — too few to make any claim about", frames.totalFrames())
                .isTrue();

        assertThat(frames.jankPercentage())
                .as(
                        "janky frames should stay within %.1f%% (rendered %d)",
                        config.jankBudgetPercent(), frames.totalFrames())
                .isLessThanOrEqualTo(config.jankBudgetPercent());
    }
}
